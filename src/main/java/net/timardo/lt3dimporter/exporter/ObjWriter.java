package net.timardo.lt3dimporter.exporter;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.timardo.lt3dimporter.importer.JobControl;

public final class ObjWriter {
    public static Path write(MeshSnapshot mesh, Path root, String name, JobControl control) throws IOException {
        control.check();
        Files.createDirectories(root);
        String safe = name.replaceAll("[^\\p{L}\\p{N}_-]", "_");
        if (safe.isBlank()) safe="model";
        if (safe.length()>48) safe=safe.substring(0,48);
        // Each job owns a new directory; existing exports and source assets are never overwritten.
        Path directory=Files.createTempDirectory(root,safe+"-");
        Path textures=Files.createDirectories(directory.resolve("textures"));
        try (var mtl=Files.newBufferedWriter(directory.resolve("model.mtl"),StandardCharsets.UTF_8)) {
            for (int i=0; i<mesh.materials().size(); i++) {
                control.check();
                var material=mesh.materials().get(i);
                var image=new BufferedImage(material.width(),material.height(),BufferedImage.TYPE_INT_ARGB);
                image.setRGB(0,0,material.width(),material.height(),material.argb(),0,material.width());
                ImageIO.write(image,"PNG",textures.resolve("material_"+i+".png").toFile());
                mtl.write("newmtl material_"+i+"\n# "+material.name().replace('\n',' ') + "\nKa 1 1 1\nKd 1 1 1\nKs 0 0 0\nd 1\nillum 1\nmap_Kd textures/material_"+i+".png\n\n");
            }
        }
        var positions=new LinkedHashMap<String,Integer>();
        var uvs=new LinkedHashMap<String,Integer>();
        try (var obj=Files.newBufferedWriter(directory.resolve("model.obj"),StandardCharsets.UTF_8)) {
            obj.write("# LT 3D Importer & Exporter 1.21.1; one unit = one Minecraft block\nmtllib model.mtl\no model\n");
            int material=-1, normalIndex=0, faceIndex=0;
            for (var face : mesh.faces()) {
                control.check();
                var vertices=face.vertices();
                var a=vertices.get(0); var b=vertices.get(1); var c=vertices.get(2);
                double ax=b.x()-a.x(), ay=b.y()-a.y(), az=b.z()-a.z();
                double bx=c.x()-a.x(), by=c.y()-a.y(), bz=c.z()-a.z();
                double nx=ay*bz-az*by, ny=az*bx-ax*bz, nz=ax*by-ay*bx;
                double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
                if (length<1e-12) continue;
                int[] vi=new int[vertices.size()], ti=new int[vertices.size()];
                for (int i=0; i<vertices.size(); i++) {
                    var v=vertices.get(i);
                    String position=v.x()+" "+v.y()+" "+v.z();
                    Integer vertex=positions.get(position);
                    if (vertex==null) { positions.put(position,vertex=positions.size()+1); obj.write("v "+position+"\n"); }
                    String uv=v.u()+" "+v.v();
                    Integer tex=uvs.get(uv);
                    if (tex==null) { uvs.put(uv,tex=uvs.size()+1); obj.write("vt "+uv+"\n"); }
                    vi[i]=vertex; ti[i]=tex;
                }
                obj.write(String.format(Locale.ROOT,"vn %.9f %.9f %.9f%n",nx/length,ny/length,nz/length));
                normalIndex++;
                if (material!=face.material()) { material=face.material(); obj.write("usemtl material_"+material+"\n"); }
                obj.write("f");
                for (int i=0; i<vi.length; i++) obj.write(" "+vi[i]+"/"+ti[i]+"/"+normalIndex);
                obj.write("\n");
                control.percent=95+(int)(5L*++faceIndex/Math.max(1,mesh.faces().size()));
            }
        }
        return directory.resolve("model.obj");
    }
}
