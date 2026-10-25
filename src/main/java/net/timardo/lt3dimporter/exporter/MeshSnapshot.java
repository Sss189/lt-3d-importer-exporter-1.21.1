package net.timardo.lt3dimporter.exporter;

import java.util.List;

/** Detached CPU data; no BakedModel, sprite or NativeImage reaches a worker. */
public record MeshSnapshot(List<Face> faces, List<Material> materials) {
    public record Vertex(float x, float y, float z, float u, float v) {}
    public record Face(List<Vertex> vertices, int material) {}
    public record Material(String name, int width, int height, int[] argb) {}
}
