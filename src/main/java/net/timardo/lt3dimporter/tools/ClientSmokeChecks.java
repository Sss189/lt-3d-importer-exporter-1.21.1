package net.timardo.lt3dimporter.tools;

import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.timardo.lt3dimporter.LT3DImporter;
import net.timardo.lt3dimporter.client.ModelToolScreen;
import net.timardo.lt3dimporter.exporter.ExportCapture;
import net.timardo.lt3dimporter.exporter.MeshSnapshot;
import net.timardo.lt3dimporter.exporter.ObjWriter;
import net.timardo.lt3dimporter.importer.*;
import team.creative.littletiles.LittleTilesRegistry;
import team.creative.littletiles.common.block.little.element.LittleElement;
import team.creative.littletiles.common.block.little.tile.group.LittleGroup;
import team.creative.littletiles.common.grid.LittleGrid;
import team.creative.littletiles.common.item.ItemLittleBlueprint;
import team.creative.littletiles.common.math.box.LittleBox;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Explicit developer flag only: test real loaded LT/CreativeCore models without touching a save. */
public final class ClientSmokeChecks {
    private static int ticks;
    private static boolean finished;
    private static boolean verified;
    private static int uiTicks;
    private static volatile boolean screenshotSaved;
    private static java.util.Map<ConvertedModel.Cell,Integer> expectedImport;
    public static void install() { NeoForge.EVENT_BUS.addListener(ClientSmokeChecks::tick); }
    private static void tick(ClientTickEvent.Post event) {
        var minecraft=Minecraft.getInstance();
        if (verified && !finished && minecraft.getOverlay()==null) {
            if (net.timardo.lt3dimporter.client.ClientJobs.busy) return;
            if (net.timardo.lt3dimporter.client.ClientJobs.lastOutput==null || net.timardo.lt3dimporter.client.ClientJobs.lastBlueprint==null) {
                finished=true; LT3DImporter.LOG.error("LT3D_SMOKE_FAIL client import pipeline: {}",net.timardo.lt3dimporter.client.ClientJobs.status.getString()); minecraft.stop(); return;
            }
            if (uiTicks==0) {
                try {
                    var tag=net.minecraft.nbt.TagParser.parseTag(net.timardo.lt3dimporter.client.ClientJobs.lastBlueprint);
                    var loaded=LittleGroup.load(tag);
                    check(!loaded.isEmptyIncludeChildren(),"Background pipeline produced empty native blueprint");
                    check(loaded.totalBoxes()==tag.getInt("boxes"),"Background blueprint box metadata mismatch");
                    var expanded=new java.util.HashMap<ConvertedModel.Cell,Integer>();
                    for (var tile:loaded.allTiles()) for (var box:tile)
                        for (int x=box.minX;x<box.maxX;x++) for (int y=box.minY;y<box.maxY;y++) for (int z=box.minZ;z<box.maxZ;z++)
                            check(expanded.put(new ConvertedModel.Cell(x,y,z),tile.color)==null,"Background blueprint overlaps");
                    check(expanded.equals(expectedImport),"Background blueprint changed cell positions/colors");
                    LT3DImporter.LOG.info("LT3D_BACKGROUND_BLUEPRINT_PASS voxels={} boxes={}",expanded.size(),loaded.totalBoxes());
                } catch (Throwable e) {
                    finished=true; LT3DImporter.LOG.error("LT3D_SMOKE_FAIL background blueprint",e); minecraft.stop(); return;
                }
            }
            if (++uiTicks==5) net.minecraft.client.Screenshot.grab(minecraft.gameDirectory,"lt3d-tools-smoke.png",minecraft.getMainRenderTarget(),message -> screenshotSaved=true);
            if (screenshotSaved) {
                finished=true;
                LT3DImporter.LOG.info("LT3D_SMOKE_PASS import -> LT SNBT/blueprint; actual asynchronous import pipeline; baked export -> OBJ/MTL/PNG; UV, tint, offsets, child/translucent boxes; tool screen screenshot");
                minecraft.stop();
            }
            return;
        }
        if (finished || minecraft.getOverlay()!=null || !(minecraft.screen instanceof TitleScreen) || ++ticks<10) return;
        try {
            run();
            minecraft.setScreen(new ModelToolScreen());
            verified=true;
        } catch (Throwable e) { finished=true; LT3DImporter.LOG.error("LT3D_SMOKE_FAIL",e); minecraft.stop(); }
    }
    private static void run() throws Exception {
        var minecraft=Minecraft.getInstance();
        // LT config normally arrives when joining a world; this test deliberately stays in the menu.
        LittleGrid.configure(64);
        var root=minecraft.gameDirectory.toPath().resolve("lt3d-smoke"); Files.createDirectories(root);
        var model=root.resolve("triangle.obj");
        Files.writeString(model,"v -10 -20 -30\nv -9 -20 -30\nv -10 -19 -30\nf 1 2 3\n");
        var converted=ModelImportService.convert(model,new ModelImportService.Parameters(8,0.25f,10000,null),new JobControl());
        var imported=new LittleGroup(); var grid=LittleGrid.get(16);
        var clean=LittleTilesRegistry.CLEAN.value().defaultBlockState();
        var encoded=ImportBlueprintEncoder.encode(java.util.List.of(
            new VoxelBoxMerger.Box(-1,2,3,1,3,4,-1),new VoxelBoxMerger.Box(1,2,3,2,4,5,0x80808080)),
            32,new LittleElement(clean,-1).getBlockName(),false,new JobControl());
        var encodedRoundtrip=LittleGroup.load(encoded);
        check(encodedRoundtrip.totalBoxes()==2 && encodedRoundtrip.getGrid().count==32,"Native encoder metadata mismatch");
        check(java.util.Arrays.equals(encoded.getIntArray("size"),new int[]{3,2,2}),"Native encoder bounds mismatch");
        check(encodedRoundtrip.hasTranslucentBlocks(),"Native encoder lost alpha");
        for (var entry : converted.voxels.entrySet()) {
            var p=entry.getKey();
            imported.addFast(grid,new LittleElement(clean,entry.getValue()),new LittleBox(p.x(),p.y(),p.z(),p.x()+1,p.y()+1,p.z()+1));
        }
        imported.combine(true);
        var roundtrip=LittleGroup.load(LittleGroup.save(imported));
        check(!roundtrip.isEmptyIncludeChildren(),"Imported LT SNBT was empty");
        var stack=new ItemStack(LittleTilesRegistry.BLUEPRINT.value());
        var blueprint=(ItemLittleBlueprint)stack.getItem(); blueprint.saveTiles(stack,roundtrip);
        check(blueprint.hasTiles(stack),"Blueprint data component missing");
        check(blueprint.getTiles(stack).totalBoxes()==roundtrip.totalBoxes(),"Blueprint data changed on read");
        Files.writeString(root.resolve("imported.struct"),LittleGroup.save(roundtrip).toString());

        var child=new LittleGroup();
        child.addFast(grid,new LittleElement(Blocks.GLASS.defaultBlockState(),-1),new LittleBox(0,16,0,8,24,8));
        var group=new LittleGroup((net.minecraft.nbt.CompoundTag)null,java.util.List.of(child));
        group.addFast(grid,new LittleElement(Blocks.STONE.defaultBlockState(),0xff804020),new LittleBox(0,0,0,16,16,16));
        group.addFast(grid,new LittleElement(Blocks.OAK_PLANKS.defaultBlockState(),-1),new LittleBox(16,0,0,24,16,16));
        var control=new JobControl(); var capture=new ExportCapture(group,control);
        while (!capture.step()) {}
        MeshSnapshot mesh=capture.snapshot();
        check(mesh.faces().size()>=18,"Missing solid, child or translucent faces: "+mesh.faces().size());
        check(mesh.materials().size()>=3,"Missing texture materials");
        float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
        for (var face : mesh.faces()) for (var vertex : face.vertices()) {
            minX=Math.min(minX,vertex.x()); maxX=Math.max(maxX,vertex.x()); maxY=Math.max(maxY,vertex.y());
            check(vertex.u()>=-0.01 && vertex.u()<=1.01 && vertex.v()>=-0.01 && vertex.v()<=1.01,"UV escaped sprite");
        }
        check(Math.abs(minX)<0.001 && Math.abs(maxX-1.5)<0.001 && Math.abs(maxY-1.5)<0.001,
            "Export offset/scale mismatch: "+minX+".."+maxX+" y="+maxY);
        check(ExportCapture.localUV(0.75f,0.5f,0.75f)==1,"UV upper edge wrapped");
        var stone=minecraft.getBlockRenderer().getBlockModel(Blocks.STONE.defaultBlockState());
        BakedQuad stoneQuad=stone.getQuads(Blocks.STONE.defaultBlockState(),net.minecraft.core.Direction.SOUTH,RandomSource.create(0)).getFirst();
        int abgr=stoneQuad.getSprite().getPixelRGBA(0,0,0);
        int argb=(abgr&0xff00ff00)|((abgr&255)<<16)|((abgr>>>16)&255);
        int expected=MaterialTexture.multiply(argb,128/255f,64/255f,32/255f,1);
        var stoneMaterial=mesh.materials().stream().filter(m -> m.name().contains("stone") && m.name().endsWith("ff804020")).findFirst().orElseThrow();
        check(stoneMaterial.argb()[0]==expected,"Texture channel/tint mismatch");
        var output=ObjWriter.write(mesh,root.resolve("exports"),"smoke",control);
        try (var input=Files.newInputStream(output)) {
            var obj=de.javagl.obj.ObjReader.read(input);
            check(obj.getNumFaces()==mesh.faces().size(),"Written OBJ face count mismatch");
        }
        // Check widget construction on the real client; no world or inventory is modified.
        new ModelToolScreen().init(minecraft,427,240);
        var chair=minecraft.gameDirectory.toPath().toAbsolutePath().normalize().getParent().resolve("examples/chair-white.obj");
        var importFile=Files.isRegularFile(chair)?chair:model;
        double importSize=Files.isRegularFile(chair)?4:0.5;
        expectedImport=ModelImportService.convert(importFile,new ModelImportService.Parameters(importSize*16,0.25f,250000,null),new JobControl()).voxels;
        net.timardo.lt3dimporter.client.ClientJobs.importModel(importFile,null,importSize,16,0.25f,
            net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(LittleTilesRegistry.CLEAN.value()).toString());
        LT3DImporter.LOG.info("LT3D_SMOKE_OUTPUT {}",output);
    }
    private static void check(boolean condition,String message) { if (!condition) throw new IllegalStateException(message); }
}
