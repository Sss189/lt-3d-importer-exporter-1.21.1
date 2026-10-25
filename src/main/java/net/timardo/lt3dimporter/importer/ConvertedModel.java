package net.timardo.lt3dimporter.importer;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure conversion result. Building LT objects is deferred to the client thread. */
public final class ConvertedModel {
    public record Cell(int x, int y, int z) {}
    public final Map<Cell, Integer> voxels = new LinkedHashMap<>();
    private final Map<String, MaterialTexture> textures;
    private final int limit;
    private final int[] max;
    public final JobControl control;
    public ConvertedModel(Map<String, MaterialTexture> textures, int limit, int[] max, JobControl control) {
        this.textures = textures; this.limit = limit; this.max = max.clone(); this.control = control;
    }
    public void addTile(int[] pos, double[] uv, String material) throws ImportException {
        control.step();
        var cell = new Cell(Math.clamp(pos[0],0,max[0]), Math.clamp(pos[1],0,max[1]), Math.clamp(pos[2],0,max[2]));
        if (voxels.containsKey(cell)) return;
        int color = textures.getOrDefault(material, MaterialTexture.white()).colorTile(uv);
        if ((color >>> 24) == 0) return;
        if (voxels.size() >= limit) throw new ImportException("Tile limit reached (" + limit + "). Reduce the model size or grid.");
        voxels.put(cell, color);
    }
}
