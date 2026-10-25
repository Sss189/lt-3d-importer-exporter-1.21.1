package net.timardo.lt3dimporter;

import de.javagl.obj.ObjReader;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import javax.imageio.ImageIO;
import net.timardo.lt3dimporter.exporter.MeshSnapshot;
import net.timardo.lt3dimporter.exporter.ObjWriter;
import net.timardo.lt3dimporter.importer.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConversionTest {
    @TempDir Path directory;
    private Path triangle(boolean textured) throws Exception {
        Path file=directory.resolve("triangle.obj");
        Files.writeString(file,(textured?"mtllib materials/model.mtl\n":"")+
            "v -10 -20 -30\nv -9 -20 -30\nv -10 -19 -30\n"+
            (textured?"vt 0.25 0.75\nvt 0.25 0.75\nvt 0.25 0.75\nusemtl red\nf 1/1 2/2 3/3\n":"f 1 2 3\n"));
        return file;
    }
    @Test void importNormalizesNegativeOriginAndKeepsThinTriangle() throws Exception {
        var model=ModelImportService.convert(triangle(false),new ModelImportService.Parameters(8,0.25f,500,null),new JobControl());
        assertFalse(model.voxels.isEmpty());
        assertTrue(model.voxels.keySet().stream().allMatch(p -> p.x()>=0 && p.x()<8 && p.y()>=0 && p.y()<8 && p.z()==0));
        var tiny=ModelImportService.convert(triangle(false),new ModelImportService.Parameters(0.1,0.25f,500,null),new JobControl());
        assertEquals(1,tiny.voxels.size());
    }
    @Test void materialTexturePathIsRelativeToMtlAndDiffuseColorIsApplied() throws Exception {
        Path assets=Files.createDirectories(directory.resolve("materials/images"));
        var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);
        for (int x=0;x<2;x++) for (int y=0;y<2;y++) image.setRGB(x,y,0xFFFF0000);
        ImageIO.write(image,"PNG",assets.resolve("red.png").toFile());
        Files.writeString(directory.resolve("materials/model.mtl"),"newmtl red\nKd 0.5 1 1\nd 0.5\nmap_Kd images/red.png\n");
        var model=ModelImportService.convert(triangle(true),new ModelImportService.Parameters(4,0.25f,500,null),new JobControl());
        assertTrue(model.voxels.values().stream().allMatch(color -> color==0x80800000));
    }
    @Test void geometryWithoutMaterialLibraryImportsAsOpaqueWhite() throws Exception {
        var file=triangle(false);
        Files.writeString(file,Files.readString(file).replace("f 1 2 3", "usemtl unspecified\nf 1 2 3"));
        var model=ModelImportService.convert(file,new ModelImportService.Parameters(8,0.25f,500,null),new JobControl());
        assertFalse(model.voxels.isEmpty());
        assertTrue(model.voxels.values().stream().allMatch(color -> color==0xFFFFFFFF));
    }
    @Test void missingReferencedMaterialLibraryImportsAsOpaqueWhite() throws Exception {
        var model=ModelImportService.convert(triangle(true),new ModelImportService.Parameters(8,0.25f,500,null),new JobControl());
        assertFalse(model.voxels.isEmpty());
        assertTrue(model.voxels.values().stream().allMatch(color -> color==0xFFFFFFFF));
    }
    @Test void undefinedMaterialFallsBackToWhiteAndKeepsAvailableMaterialColor() throws Exception {
        Files.createDirectories(directory.resolve("materials"));
        Files.writeString(directory.resolve("materials/model.mtl"),"newmtl red\nKd 1 0 0\n");
        var file=triangle(true);
        Files.writeString(file,Files.readString(file)+
            "v -8 -20 -30\nv -7 -20 -30\nv -8 -19 -30\nusemtl undefined\nf 4 5 6\n");
        var model=ModelImportService.convert(file,new ModelImportService.Parameters(12,0.25f,500,null),new JobControl());
        assertEquals(java.util.Set.of(0xFFFF0000,0xFFFFFFFF),java.util.Set.copyOf(model.voxels.values()));
    }
    @Test void missingDiffuseTextureFallsBackToOpaqueWhiteInsteadOfDroppingGeometry() throws Exception {
        Files.createDirectories(directory.resolve("materials"));
        Files.writeString(directory.resolve("materials/model.mtl"),"newmtl red\nKd 0 0 0\nd 0\nmap_Kd missing.png\n");
        var model=ModelImportService.convert(triangle(true),new ModelImportService.Parameters(8,0.25f,500,null),new JobControl());
        assertFalse(model.voxels.isEmpty());
        assertTrue(model.voxels.values().stream().allMatch(color -> color==0xFFFFFFFF));
    }
    @Test void explicitMissingTextureOverrideStillReportsTheIncorrectInput() throws Exception {
        assertThrows(ImportException.class,() -> ModelImportService.convert(triangle(false),
            new ModelImportService.Parameters(8,0.25f,500,directory.resolve("missing.png")),new JobControl()));
    }
    @Test void missingModelAndTileLimitProduceClearErrorsAndCancellationWorks() throws Exception {
        assertThrows(ImportException.class,() -> ModelImportService.convert(directory.resolve("absent.obj"),new ModelImportService.Parameters(4,0.25f,100,null),new JobControl()));
        var file=triangle(false);
        assertThrows(ImportException.class,() -> ModelImportService.convert(file,new ModelImportService.Parameters(32,0.25f,2,null),new JobControl()));
        var cancelled=new JobControl(); cancelled.cancelled.set(true);
        assertThrows(CancellationException.class,() -> ModelImportService.convert(file,new ModelImportService.Parameters(4,0.25f,100,null),cancelled));
    }
    @Test void negativeUVRepeatsAndMissingUVFallsBackToDiffuseColor() {
        var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1,0,0xFF123456);
        var texture=new MaterialTexture(image,1,1,1,1);
        assertEquals(0xFF123456,texture.colorTile(new double[]{-0.25,0.75}));
        assertEquals(0xFFFFFFFF,texture.colorTile(null));
    }
    @Test void exportedObjHasValidIndicesNormalsPngAndDoesNotOverwriteEarlierOutput() throws Exception {
        var vertices=List.of(new MeshSnapshot.Vertex(0,0,0,0,0),new MeshSnapshot.Vertex(1,0,0,1,0),new MeshSnapshot.Vertex(1,1,0,1,1),new MeshSnapshot.Vertex(0,1,0,0,1));
        var mesh=new MeshSnapshot(List.of(new MeshSnapshot.Face(vertices,0)),List.of(new MeshSnapshot.Material("test",1,1,new int[]{0x80123456})));
        Path first=ObjWriter.write(mesh,directory,"model",new JobControl());
        Path second=ObjWriter.write(mesh,directory,"model",new JobControl());
        assertNotEquals(first,second);
        try (var input=Files.newInputStream(first)) {
            var obj=ObjReader.read(input);
            assertEquals(4,obj.getNumVertices()); assertEquals(1,obj.getNumFaces());
            assertTrue(obj.getFace(0).containsNormalIndices());
            assertEquals(1,obj.getTexCoord(obj.getFace(0).getTexCoordIndex(2)).getX());
        }
        assertEquals(0x80123456,ImageIO.read(first.getParent().resolve("textures/material_0.png").toFile()).getRGB(0,0));
    }
}
