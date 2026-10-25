package net.timardo.lt3dimporter.exporter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.timardo.lt3dimporter.importer.JobControl;
import net.timardo.lt3dimporter.importer.MaterialTexture;
import team.creative.creativecore.client.render.box.QuadGeneratorContext;
import team.creative.creativecore.client.render.box.RenderBox;
import team.creative.creativecore.client.render.face.RenderBoxFace;
import team.creative.creativecore.common.util.math.base.Facing;
import team.creative.littletiles.common.block.little.tile.group.LittleGroup;

/** Incremental client-thread capture, using CreativeCore's clipped baked geometry. */
public final class ExportCapture {
    private final List<RenderBox> boxes=new ArrayList<>();
    private final List<MeshSnapshot.Face> faces=new ArrayList<>();
    private final List<MeshSnapshot.Material> materials=new ArrayList<>();
    private final Map<String,Integer> materialIds=new LinkedHashMap<>();
    private final JobControl control;
    private int boxIndex;
    private long texturePixels;
    public ExportCapture(LittleGroup group, JobControl control) {
        requireClientThread();
        this.control=control;
        collect(group);
        if (boxes.isEmpty()) throw new IllegalArgumentException("Blueprint contains no renderable boxes.");
    }
    private void collect(LittleGroup group) {
        // getRenderingBoxes() intentionally substitutes a single box for large item previews.
        // Walk actual tiles to avoid exporting that low-resolution placeholder.
        for (var tile : group) {
            tile.addRenderingBoxes(group.getGrid(),boxes);
            if (boxes.size()>250_000) throw new IllegalArgumentException("Blueprint exceeds 250000 render boxes.");
        }
        for (LittleGroup child : group.children.all()) collect(child);
    }
    public boolean step() {
        requireClientThread(); control.check();
        long deadline=System.nanoTime()+8_000_000L;
        do {
            capture(boxes.get(boxIndex++));
            control.percent=(int)(90L*boxIndex/boxes.size());
        } while (boxIndex<boxes.size() && System.nanoTime()<deadline);
        return boxIndex==boxes.size();
    }
    public MeshSnapshot snapshot() { return new MeshSnapshot(List.copyOf(faces),List.copyOf(materials)); }
    private void capture(RenderBox box) {
        var minecraft=Minecraft.getInstance();
        var model=minecraft.getBlockRenderer().getBlockModel(box.state);
        var random=RandomSource.create(0);
        var context=new QuadGeneratorContext();
        // A blueprint has no world position. Do not invent neighbour/bio­me data for CTM.
        var layers=model.getRenderTypes(box.state,random,ModelData.EMPTY);
        for (RenderType layer : layers) for (Facing direction : Facing.VALUES) {
            box.setFace(direction,RenderBoxFace.RENDER);
            var quads=box.getBakedQuad(context,null,null,box.getOffset(),box.state,model,ModelData.EMPTY,
                direction,layer,RandomSource.create(0),false,-1);
            for (BakedQuad quad : quads) addFace(box,quad);
        }
    }
    private void addFace(RenderBox box, BakedQuad quad) {
        var sprite=quad.getSprite();
        int tint=box.color;
        if (quad.isTinted()) {
            int blockTint=Minecraft.getInstance().getBlockColors().getColor(box.state,null,null,quad.getTintIndex());
            if (blockTint!=-1) tint=MaterialTexture.multiply(tint,((blockTint>>>16)&255)/255f,((blockTint>>>8)&255)/255f,(blockTint&255)/255f,1);
        }
        String key=sprite.contents().name()+":"+Integer.toHexString(tint);
        Integer material=materialIds.get(key);
        if (material==null) {
            if (materials.size()>=2048) throw new IllegalArgumentException("Export exceeds 2048 materials.");
            int width=sprite.contents().width(), height=sprite.contents().height();
            if ((texturePixels+=(long)width*height)>64_000_000L) throw new IllegalArgumentException("Export exceeds the texture memory limit.");
            int[] pixels=new int[Math.multiplyExact(width,height)];
            int frame=sprite.contents().getUniqueFrames().findFirst().orElse(0);
            for (int y=0;y<height;y++) for (int x=0;x<width;x++) {
                int abgr=sprite.getPixelRGBA(frame,x,y);
                int argb=(abgr&0xff00ff00)|((abgr&255)<<16)|((abgr>>>16)&255);
                pixels[y*width+x]=MaterialTexture.multiply(argb,((tint>>>16)&255)/255f,((tint>>>8)&255)/255f,(tint&255)/255f,(tint>>>24)/255f);
            }
            material=materials.size(); materialIds.put(key,material);
            materials.add(new MeshSnapshot.Material(key,width,height,pixels));
        }
        int[] data=quad.getVertices();
        int stride=data.length/4;
        if (stride<6) throw new IllegalArgumentException("Unsupported baked quad vertex layout.");
        var vertices=new ArrayList<MeshSnapshot.Vertex>();
        for (int i=0;i<4;i++) {
            int start=i*stride;
            float x=Float.intBitsToFloat(data[start]), y=Float.intBitsToFloat(data[start+1]), z=Float.intBitsToFloat(data[start+2]);
            float u=localUV(Float.intBitsToFloat(data[start+4]),sprite.getU0(),sprite.getU1());
            float v=1-localUV(Float.intBitsToFloat(data[start+5]),sprite.getV0(),sprite.getV1());
            if (!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)||!Float.isFinite(u)||!Float.isFinite(v)) throw new IllegalArgumentException("Invalid baked vertex.");
            boolean duplicate=false;
            for (var vertex : vertices) if (vertex.x()==x && vertex.y()==y && vertex.z()==z) duplicate=true;
            if (!duplicate) vertices.add(new MeshSnapshot.Vertex(x,y,z,u,v));
        }
        if (vertices.size()<3) return;
        if (faces.size()>=1_500_000) throw new IllegalArgumentException("Export exceeds 1500000 faces.");
        faces.add(new MeshSnapshot.Face(List.copyOf(vertices),material));
    }
    public static float localUV(float uv,float start,float end) {
        // Preserve the upper edge as 1, rather than wrapping it to 0 as the old exporter did.
        return (uv-start)/(end-start);
    }
    private static void requireClientThread() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Capture must run on the client thread.");
    }
}
