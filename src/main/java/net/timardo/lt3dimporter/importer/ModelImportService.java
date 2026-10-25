package net.timardo.lt3dimporter.importer;

import de.javagl.obj.Mtl;
import de.javagl.obj.MtlReader;
import de.javagl.obj.ObjReader;
import de.javagl.obj.ObjUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.timardo.lt3dimporter.obj3d.LightObj;
import net.timardo.lt3dimporter.obj3d.LightObjGroup;

/** Modernized orchestration of the upstream OBJ/MTL/slice conversion. */
public final class ModelImportService {
    public record Parameters(double longestSideCells, float precision, int maxTiles, Path textureOverride) {
        public Parameters {
            if (!Double.isFinite(longestSideCells) || longestSideCells<=0 || longestSideCells>4096)
                throw new IllegalArgumentException("Longest side must be between 0 and 4096 little cells.");
            if (!Float.isFinite(precision) || precision<0.05f || precision>1)
                throw new IllegalArgumentException("Sampling interval must be between 0.05 and 1.");
            if (maxTiles<1 || maxTiles>500_000) throw new IllegalArgumentException("Tile limit must be 1 to 500000.");
        }
    }
    public static ConvertedModel convert(Path file, Parameters parameters, JobControl control) throws ImportException {
        control.check();
        control.phase="sampling";
        try {
            file = file.toAbsolutePath().normalize();
            if (!Files.isRegularFile(file) || Files.size(file)>32L*1024*1024)
                throw new ImportException("OBJ missing or larger than 32 MiB: " + file);
            LightObj source;
            try (var stream = Files.newInputStream(file)) { source = (LightObj)ObjReader.read(stream, new LightObj()); }
            if (source.getNumFaces()==0 || source.getNumVertices()==0) throw new ImportException("OBJ contains no faces.");
            if (source.getNumFaces()>200_000 || source.getNumVertices()>1_000_000) throw new ImportException("OBJ exceeds the face/vertex limit.");
            for (int i=0; i<source.getNumVertices(); i++) {
                var p=source.getVertex(i);
                if (!Float.isFinite(p.getX()) || !Float.isFinite(p.getY()) || !Float.isFinite(p.getZ()))
                    throw new ImportException("OBJ contains invalid vertex coordinates.");
            }
            LightObj obj = ObjUtils.triangulate(source, new LightObj(true));
            if (obj.getNumFaces()>400_000) throw new ImportException("Triangulated OBJ exceeds 400000 faces.");
            Map<String, Mtl> materials = new HashMap<>();
            Map<String, Path> materialDirs = new HashMap<>();
            if (parameters.textureOverride()==null) for (String name : obj.getMtlFileNames()) {
                Path mtlFile = file.getParent().resolve(name).normalize();
                // Geometry-only exports often retain mtllib/usemtl references.
                // A missing library leaves those surfaces with the white fallback.
                if (!Files.isRegularFile(mtlFile)) continue;
                if (Files.size(mtlFile)>4L*1024*1024) throw new ImportException("MTL larger than 4 MiB: " + mtlFile);
                try (var stream=Files.newInputStream(mtlFile)) {
                    for (Mtl material : MtlReader.read(stream)) {
                        if (materials.putIfAbsent(material.getName(), material)!=null) throw new ImportException("Duplicate material: " + material.getName());
                        materialDirs.put(material.getName(), mtlFile.getParent());
                    }
                }
            }
            Map<String, MaterialTexture> textures = new HashMap<>();
            MaterialTexture override = parameters.textureOverride()==null ? null : MaterialTexture.image(parameters.textureOverride());
            for (int i=0; i<obj.getNumMaterialGroups(); i++) {
                String name=obj.getMaterialGroup(i).getName();
                textures.put(name, override!=null ? override : MaterialTexture.mtl(materials.get(name), materialDirs.getOrDefault(name,file.getParent())));
            }
            double[] sides=obj.getSides();
            double longest=Math.max(sides[0],Math.max(sides[1],sides[2]));
            if (!Double.isFinite(longest) || longest<1e-12) throw new ImportException("OBJ has zero extent.");
            double scale=parameters.longestSideCells()/longest;
            int[] max=new int[3];
            for (int axis=0; axis<3; axis++) max[axis]=Math.max(0,(int)Math.ceil(sides[axis]*scale-1e-9)-1);
            Point3 offset=new Point3(-obj.boxCoords[3],-obj.boxCoords[4],-obj.boxCoords[5]);
            ConvertedModel output=new ConvertedModel(textures,parameters.maxTiles(),max,control);
            int done=0;
            for (int i=0; i<obj.getNumMaterialGroups(); i++) {
                LightObjGroup group=(LightObjGroup)obj.getMaterialGroup(i);
                for (Triangle triangle : group.triangles) {
                    control.check();
                    triangle.calcBlocks(parameters.precision(),scale,offset,output,group.getName());
                    control.percent=(int)(90L*++done/obj.getNumFaces());
                }
            }
            if (output.voxels.isEmpty()) throw new ImportException("No visible tiles were generated.");
            return output;
        } catch (ImportException e) { throw e; }
        catch (java.util.concurrent.CancellationException e) { throw e; }
        catch (Exception e) { throw new ImportException("Cannot convert OBJ: " + e.getMessage(),e); }
    }
}
