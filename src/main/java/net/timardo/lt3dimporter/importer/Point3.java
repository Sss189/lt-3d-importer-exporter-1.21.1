package net.timardo.lt3dimporter.importer;

public record Point3(double x, double y, double z) {
    public Point3 add(Point3 other) { return new Point3(x + other.x, y + other.y, z + other.z); }
    public Point3 scale(double amount) { return new Point3(x * amount, y * amount, z * amount); }
    public Point3 vectorTo(Point3 other) { return new Point3(other.x - x, other.y - y, other.z - z); }
    public double length() { return Math.sqrt(x*x + y*y + z*z); }
    public double axis(int axis) { return axis == 0 ? x : axis == 1 ? y : z; }
}
