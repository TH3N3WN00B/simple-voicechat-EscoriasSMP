/**
 * ServerPlayerManager.isInRange. OLD = Vec3.distanceToSqr (MC does NOT allocate,
 * so this one is expected to be ~neutral). NEW = manual squared distance.
 */
public class Dist {

    static boolean isInRangeOld(Vec3 pos1, Vec3 pos2, double range) {
        return pos1.distanceToSqr(pos2) <= range * range;
    }

    static boolean isInRangeNew(Vec3 pos1, Vec3 pos2, double range) {
        double dx = pos1.x - pos2.x;
        double dy = pos1.y - pos2.y;
        double dz = pos1.z - pos2.z;
        return dx * dx + dy * dy + dz * dz <= range * range;
    }
}