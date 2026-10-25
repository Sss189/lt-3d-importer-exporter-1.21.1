package net.timardo.lt3dimporter.importer;

import de.javagl.obj.Mtl;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** UV sampling follows Timardo's importer; no game resources are accessed on workers. */
public final class MaterialTexture {
    private final BufferedImage image;
    private final float r, g, b, a;
    public MaterialTexture(BufferedImage image, float r, float g, float b, float a) {
        this.image = image;
        this.r = clamp(r); this.g = clamp(g); this.b = clamp(b); this.a = clamp(a);
    }
    public static MaterialTexture white() { return new MaterialTexture(null, 1, 1, 1, 1); }
    public static MaterialTexture image(Path path) throws IOException { return new MaterialTexture(read(path), 1, 1, 1, 1); }
    public static MaterialTexture mtl(Mtl material, Path directory) throws IOException {
        if (material == null) return white();
        var kd = material.getKd();
        var dissolve = material.getD();
        BufferedImage image = null;
        if (material.getMapKd() != null) {
            Path texture = directory.resolve(material.getMapKd()).normalize();
            // Missing texture dependencies become opaque white, including when
            // the incomplete material carries a dark tint or zero opacity.
            if (!java.nio.file.Files.isRegularFile(texture)) return white();
            image = read(texture);
        }
        return new MaterialTexture(image, kd == null ? 1 : kd.getX(), kd == null ? 1 : kd.getY(),
            kd == null ? 1 : kd.getZ(), dissolve == null ? 1 : dissolve);
    }
    private static BufferedImage read(Path path) throws IOException {
        if (!java.nio.file.Files.isRegularFile(path) || java.nio.file.Files.size(path) > 64L*1024*1024)
            throw new IOException("Texture missing or larger than 64 MiB: " + path);
        // Inspect dimensions before decoding to avoid very large allocations.
        try (var stream = ImageIO.createImageInputStream(path.toFile())) {
            if (stream == null) throw new IOException("Cannot open texture: " + path);
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException("Unsupported texture: " + path);
            var reader = readers.next();
            try {
                reader.setInput(stream);
                if ((long)reader.getWidth(0)*reader.getHeight(0) > 16_777_216L) throw new IOException("Texture exceeds 16 million pixels: " + path);
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }
    public int colorTile(double[] uv) {
        int argb = 0xFFFFFFFF;
        if (image != null && uv != null && Double.isFinite(uv[0]) && Double.isFinite(uv[1])) {
            double u = uv[0] - Math.floor(uv[0]), v = uv[1] - Math.floor(uv[1]);
            int x = Math.min(image.getWidth()-1, (int)(u*image.getWidth()));
            int y = Math.min(image.getHeight()-1, (int)((1-v)*image.getHeight()));
            argb = image.getRGB(x, y);
        }
        return multiply(argb, r, g, b, a);
    }
    public static int multiply(int argb, float r, float g, float b, float a) {
        return (Math.round((argb >>> 24)*clamp(a)) << 24) |
            (Math.round(((argb >>> 16)&255)*clamp(r)) << 16) |
            (Math.round(((argb >>> 8)&255)*clamp(g)) << 8) |
            Math.round((argb&255)*clamp(b));
    }
    private static float clamp(float value) { return Float.isFinite(value) ? Math.clamp(value, 0, 1) : 1; }
}
