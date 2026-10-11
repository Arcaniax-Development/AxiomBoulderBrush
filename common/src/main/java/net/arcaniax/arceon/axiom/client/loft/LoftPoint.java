package net.arcaniax.arceon.axiom.client.loft;

import net.minecraft.world.phys.Vec3;

public class LoftPoint {
    private int pointId;
    private double x;
    private double y;
    private double z;

    public LoftPoint(int pointId, double x, double y, double z) {
        this.pointId = pointId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int pointId() {
        return pointId;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public Vec3 asVec3() {
        return new Vec3(x, y, z);
    }

    public void moveTo(Vec3 position) {
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
    }

    public LoftPoint copy() {
        return new LoftPoint(pointId, x, y, z);
    }
}
