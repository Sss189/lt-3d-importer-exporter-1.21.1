package net.timardo.lt3dimporter.importer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;

/** LT's native collection format, encoded off-thread from captured registry data. */
public final class ImportBlueprintEncoder {
    private ImportBlueprintEncoder() {}
    public static CompoundTag encode(List<VoxelBoxMerger.Box> boxes,int grid,String blockName,
                                     boolean baseTranslucent,JobControl control) {
        control.phase="encoding";
        control.check();
        if (boxes.isEmpty()) throw new IllegalArgumentException("Cannot encode an empty model");
        var colors=new LinkedHashMap<Integer,List<VoxelBoxMerger.Box>>();
        int[] min={Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE};
        int[] max={Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE};
        for (var box:boxes) {
            control.check();
            colors.computeIfAbsent(box.argb(),key -> new ArrayList<>()).add(box);
            for (int axis=0;axis<3;axis++) {
                min[axis]=Math.min(min[axis],box.min(axis));
                max[axis]=Math.max(max[axis],box.max(axis));
            }
        }
        var content=new ListTag(); boolean translucent=baseTranslucent;
        int done=0;
        for (var entry:colors.entrySet()) {
            content.add(new IntArrayTag(new int[]{entry.getKey()}));
            if ((entry.getKey()>>>24)!=255) translucent=true;
            for (var box:entry.getValue()) {
                control.check();
                content.add(new IntArrayTag(new int[]{box.x0(),box.y0(),box.z0(),box.x1(),box.y1(),box.z1()}));
                control.percent=94+(int)(4L*++done/boxes.size());
            }
        }
        var tiles=new CompoundTag(); tiles.put(blockName,content);
        var group=new CompoundTag(); group.put("t",tiles); group.putInt("grid",grid);
        group.putIntArray("min",min);
        group.putIntArray("size",new int[]{max[0]-min[0],max[1]-min[1],max[2]-min[2]});
        group.putInt("tiles",colors.size()); group.putInt("boxes",boxes.size());
        if (translucent) group.putBoolean("trans",true);
        control.check();
        return group;
    }
}
