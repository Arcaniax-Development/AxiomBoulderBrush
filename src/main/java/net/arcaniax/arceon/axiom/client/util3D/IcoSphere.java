package net.arcaniax.arceon.axiom.client.util3D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class IcoSphere {

    public List<Double[]> points = new ArrayList<>();
    public List<int[]> triangles = new ArrayList<>();
    public HashMap<String, Integer> savedPoints = new HashMap<>();

    public IcoSphere(int iterations, double mod, boolean random) {
        //golden ratio times radius
        double t = (1.0 + Math.sqrt(5.0)) * 0.5;
        double r = 1 / t;
        t = 1;


        if (random) {
            Random ran = new Random();
            double rotation = ran.nextDouble() * 2 * Math.PI;
            addPoint(-r * random(), t * random(), 0, rotation);
            addPoint(r * random(), t * random(), 0, rotation);
            addPoint(-r * random(), -t * random(), 0, rotation);
            addPoint(r * random(), -t * random(), 0, rotation);

            addPoint(0, -r * random(), t * random(), rotation);
            addPoint(0, r * random(), t * random(), rotation);
            addPoint(0, -r * random(), -t * random(), rotation);
            addPoint(0, r * random(), -t * random(), rotation);

            addPoint(t * random(), 0, -r * random(), rotation);
            addPoint(t * random(), 0, r * random(), rotation);
            addPoint(-t * random(), 0, -r * random(), rotation);
            addPoint(-t * random(), 0, r * random(), rotation);
        } else {
            addPoint(-r, t, 0, 0);
            addPoint(r, t, 0, 0);
            addPoint(-r, -t, 0, 0);
            addPoint(r, -t, 0, 0);

            addPoint(0, -r, t, 0);
            addPoint(0, r, t, 0);
            addPoint(0, -r, -t, 0);
            addPoint(0, r, -t, 0);

            addPoint(t, 0, -r, 0);
            addPoint(t, 0, r, 0);
            addPoint(-t, 0, -r, 0);
            addPoint(-t, 0, r, 0);
        }


        addTriangle(0, 11, 5);
        addTriangle(0, 5, 1);
        addTriangle(0, 1, 7);
        addTriangle(0, 7, 10);
        addTriangle(0, 10, 11);

        addTriangle(1, 5, 9);
        addTriangle(5, 11, 4);
        addTriangle(11, 10, 2);
        addTriangle(10, 7, 6);
        addTriangle(7, 1, 8);

        addTriangle(3, 9, 4);
        addTriangle(3, 4, 2);
        addTriangle(3, 2, 6);
        addTriangle(3, 6, 8);
        addTriangle(3, 8, 9);

        addTriangle(4, 9, 5);
        addTriangle(2, 4, 11);
        addTriangle(6, 2, 10);
        addTriangle(8, 6, 7);
        addTriangle(9, 8, 1);

        refine(iterations, mod, random);
    }

    private void refine(int iterations, double mod, boolean random) {
        for (int iteration = 0; iteration < iterations; iteration++) {
            List<int[]> trianglesCopy = new ArrayList<>(triangles);
            triangles.clear();
            int length = trianglesCopy.size();
            for (int[] triangle : trianglesCopy) {
                int ab = center(triangle[0], triangle[1], mod, random, iterations);
                int bc = center(triangle[1], triangle[2], mod, random, iterations);
                int ca = center(triangle[2], triangle[0], mod, random, iterations);

                addTriangle(triangle[0], ab, ca);
                addTriangle(triangle[1], bc, ab);
                addTriangle(triangle[2], ca, bc);
                addTriangle(ab, bc, ca);
            }
        }
    }

    private double random() {
        Random ran = new Random();
        return (ran.nextDouble() / 2) + 0.75;
    }

    private int center(int a, int b, double mod, boolean random, int it) {
        Double[] v1 = points.get(a);
        Double[] v2 = points.get(b);
        Double[] vCenter = new Double[3];
        vCenter[0] = (v1[0] + v2[0]) * 0.5;
        vCenter[1] = (v1[1] + v2[1]) * 0.5;
        vCenter[2] = (v1[2] + v2[2]) * 0.5;


        if (random) {
            it *= it;
            double distance1 = Math.sqrt(v1[0] * v1[0] + v1[1] * v1[1] + v1[2] * v1[2]);
            double distance2 = Math.sqrt(v2[0] * v2[0] + v2[1] * v2[1] + v2[2] * v2[2]);

            double avgDistance = (distance1 + distance2) / 2;
            double distance = Math.sqrt(vCenter[0] * vCenter[0] + vCenter[1] * vCenter[1] + vCenter[2] * vCenter[2]);
            double multiplier = avgDistance / distance;
            Random ran = new Random();
            double rand = (ran.nextDouble() / (2 + it)) + (1 - (1 / (2 + it)) / 2);
            vCenter[0] *= rand * multiplier;
            rand = (ran.nextDouble() / (2 + it)) + (1 - (1 / (2 + it)) / 2);
            vCenter[1] *= rand * multiplier;
            rand = (ran.nextDouble() / (2 + it)) + (1 - (1 / (2 + it)) / 2);
            vCenter[2] *= rand * multiplier;
        } else {
            double distance = Math.sqrt(vCenter[0] * vCenter[0] + vCenter[1] * vCenter[1] + vCenter[2] * vCenter[2]);
            double multiplier = 1 / distance;
            vCenter[0] *= multiplier * mod;
            vCenter[1] *= multiplier * mod;
            vCenter[2] *= multiplier * mod;
        }

        if (points.contains(vCenter)) {
            for (int x = 0; x < points.size(); x++) {
                if (points.get(x) == vCenter) {
                    return x;
                }
            }
        }
        String check = a + "," + b;
        if (savedPoints.containsKey(check)) {
            return savedPoints.get(check);

        }
        check = b + "," + a;
        if (savedPoints.containsKey(check)) {
            return savedPoints.get(check);
        }
        savedPoints.put(check, points.size());
        return addPoint(vCenter[0], vCenter[1], vCenter[2], 0);
    }

    private int addPoint(double x, double y, double z, double rotation) {
        Double[] point = new Double[3];
        if (rotation == 0) {
            point[0] = x;
            point[1] = y;
            point[2] = z;
        } else {
            point[0] = x * Math.cos(rotation) - z * Math.sin(rotation);
            point[1] = y;
            point[2] = z * Math.cos(rotation) + x * Math.sin(rotation);
        }
        points.add(point);
        return points.size() - 1;
    }

    private void addTriangle(int x, int y, int z) {
        int[] triangle = new int[3];
        triangle[0] = x;
        triangle[1] = y;
        triangle[2] = z;
        triangles.add(triangle);
    }


}
