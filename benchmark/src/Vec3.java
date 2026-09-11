/**
 * Minimal Vec3/Vec2 faithful to the Minecraft operations used by the voice chat
 * targeted methods (subtract/normalize/distanceTo/distanceToSqr all behave like
 * the MC implementations these calls compile against).
 */
public class Vec3 {

    public final double x, y, z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3 subtract(Vec3 o) {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    public Vec3 normalize() {
        double d = Math.sqrt(x * x + y * y + z * z);
        return d < 1.0E-4D ? new Vec3(0D, 0D, 0D) : new Vec3(x / d, y / d, z / d);
    }

    public double distanceTo(Vec3 o) {
        return Math.sqrt(distanceToSqr(o));
    }

    public double distanceToSqr(Vec3 o) {
        double dx = o.x - x;
        double dy = o.y - y;
        double dz = o.z - z;
        return dx * dx + dy * dy + dz * dz;
    }
}

class Vec2 {

    public final float x, y;

    public Vec2(float x, float y) {
        this.x = x;
        this.y = y;
    }
}