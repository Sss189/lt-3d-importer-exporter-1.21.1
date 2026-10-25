package net.timardo.lt3dimporter.importer;

import de.javagl.obj.FloatTuple;

/**
 * Triangle slicing adapted from Timardo's LT-3D Importer (MIT, Copyright 2020).
 * Includes endpoints for thin/tiny faces; cancellation and bounded work are added.
 */
public class Triangle {
    public Point3 a, b, c;
    public double[] u, v;
    public boolean t;
    public Triangle(FloatTuple a, FloatTuple b, FloatTuple c) {
        this.a = point(a); this.b = point(b); this.c = point(c);
    }
    private static Point3 point(FloatTuple value) { return new Point3(value.getX(), value.getY(), value.getZ()); }
    public void addTexCoords(FloatTuple a, FloatTuple b, FloatTuple c) {
        u = new double[]{a.getX(), b.getX(), c.getX()};
        v = new double[]{a.getY(), b.getY(), c.getY()}; t = true;
    }
    public void calcBlocks(float precision, double scale, Point3 offset, ConvertedModel output, String material) throws ImportException {
        Point3 sa = a.add(offset).scale(scale), sb = b.add(offset).scale(scale), sc = c.add(offset).scale(scale);
        Point3 ac = sa.vectorTo(sc), bc = sb.vectorTo(sc);
        int slices = Math.max(1, (int)Math.ceil(Math.max(ac.length(), bc.length())/precision) + 2);
        for (int i=0; i<=slices; i++) {
            output.control.step();
            double fraction = (double)i/slices;
            Point3 p1 = sa.add(ac.scale(fraction)), p2 = sb.add(bc.scale(fraction));
            double[] uv1 = uv(0,2,fraction), uv2 = uv(1,2,fraction);
            output.addTile(cell(p1), uv1, material);
            output.addTile(cell(p2), uv2, material);
            for (int axis=0; axis<3; axis++) {
                double start = p1.axis(axis), end = p2.axis(axis), delta = end-start;
                if (Math.abs(delta)<1e-12) continue;
                int min = (int)Math.ceil(Math.min(start,end)), max = (int)Math.ceil(Math.max(start,end));
                for (int k=min; k<max; k++) {
                    double r = (k-start)/delta;
                    Point3 point = p1.add(p1.vectorTo(p2).scale(r));
                    output.addTile(cell(point), t ? new double[]{uv1[0]+(uv2[0]-uv1[0])*r, uv1[1]+(uv2[1]-uv1[1])*r} : null, material);
                }
            }
        }
    }
    private double[] uv(int first, int second, double fraction) {
        return t ? new double[]{u[first]+(u[second]-u[first])*fraction, v[first]+(v[second]-v[first])*fraction} : null;
    }
    private static int[] cell(Point3 p) { return new int[]{(int)Math.floor(p.x()), (int)Math.floor(p.y()), (int)Math.floor(p.z())}; }
}
