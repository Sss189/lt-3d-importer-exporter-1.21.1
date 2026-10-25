package net.timardo.lt3dimporter;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Random;
import java.util.concurrent.CancellationException;
import net.timardo.lt3dimporter.importer.ConvertedModel.Cell;
import net.timardo.lt3dimporter.importer.JobControl;
import net.timardo.lt3dimporter.importer.VoxelBoxMerger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoxelBoxMergerTest {
    @Test void preservesAllCellColorsAndHolesWithoutOverlaps() {
        var cells=new LinkedHashMap<Cell,Integer>();
        var random=new Random(94);
        int[] colors={0xFFFFFFFF,0xFFFF0000,0x8000FF00};
        for (int x=-8;x<8;x++) for (int y=-8;y<8;y++) for (int z=-8;z<8;z++)
            if (random.nextBoolean()) cells.put(new Cell(x,y,z),colors[random.nextInt(colors.length)]);
        var expanded=new HashMap<Cell,Integer>();
        for (var box:VoxelBoxMerger.merge(cells,new JobControl()))
            for (int x=box.x0();x<box.x1();x++) for (int y=box.y0();y<box.y1();y++) for (int z=box.z0();z<box.z1();z++)
                assertNull(expanded.put(new Cell(x,y,z),box.argb()),"Merged boxes overlap");
        assertEquals(cells,expanded);
    }
    @Test void merges250000SolidCellsIntoOneBox() {
        var cells=new LinkedHashMap<Cell,Integer>();
        for (int x=0;x<100;x++) for (int y=0;y<50;y++) for (int z=0;z<50;z++)
            cells.put(new Cell(x,y,z),0xFFFFFFFF);
        assertEquals(java.util.List.of(new VoxelBoxMerger.Box(0,0,0,100,50,50,0xFFFFFFFF)),
            VoxelBoxMerger.merge(cells,new JobControl()));
    }
    @Test void cancellationStopsTheMerge() {
        var control=new JobControl(); control.cancelled.set(true);
        assertThrows(CancellationException.class,() -> VoxelBoxMerger.merge(java.util.Map.of(new Cell(0,0,0),-1),control));
    }
}
