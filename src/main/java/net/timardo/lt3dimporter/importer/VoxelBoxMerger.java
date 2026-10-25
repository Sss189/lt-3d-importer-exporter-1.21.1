package net.timardo.lt3dimporter.importer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Lossless axis passes; no world access or quadratic pairwise box search. */
public final class VoxelBoxMerger {
    public record Box(int x0, int y0, int z0, int x1, int y1, int z1, int argb) {
        public int min(int axis) { return axis==0?x0:axis==1?y0:z0; }
        public int max(int axis) { return axis==0?x1:axis==1?y1:z1; }
        private boolean joins(Box next, int axis) {
            if (argb!=next.argb || max(axis)!=next.min(axis)) return false;
            for (int other=0;other<3;other++)
                if (other!=axis && (min(other)!=next.min(other) || max(other)!=next.max(other))) return false;
            return true;
        }
        private Box join(Box next,int axis) {
            return new Box(x0,y0,z0,axis==0?next.x1:x1,axis==1?next.y1:y1,axis==2?next.z1:z1,argb);
        }
    }
    private VoxelBoxMerger() {}
    public static List<Box> merge(Map<ConvertedModel.Cell,Integer> cells,JobControl control) {
        control.phase="merging";
        control.check();
        List<Box> boxes=new ArrayList<>(cells.size());
        for (var entry:cells.entrySet()) {
            control.check();
            var p=entry.getKey();
            boxes.add(new Box(p.x(),p.y(),p.z(),p.x()+1,p.y()+1,p.z()+1,entry.getValue()));
        }
        for (int pass=0;pass<3;pass++) {
            int axis=pass;
            Comparator<Box> order=Comparator.comparingInt(Box::argb);
            for (int other=0;other<3;other++) if (other!=axis) {
                int side=other;
                order=order.thenComparingInt(box -> box.min(side)).thenComparingInt(box -> box.max(side));
            }
            order=order.thenComparingInt(box -> box.min(axis));
            Comparator<Box> sorting=order;
            boxes.sort((a,b) -> { control.check(); return sorting.compare(a,b); });
            var merged=new ArrayList<Box>();
            Box current=null;
            for (Box next:boxes) {
                control.check();
                if (current!=null && current.joins(next,axis)) current=current.join(next,axis);
                else {
                    if (current!=null) merged.add(current);
                    current=next;
                }
            }
            if (current!=null) merged.add(current);
            boxes=merged;
            control.percent=91+pass;
        }
        return boxes;
    }
}
