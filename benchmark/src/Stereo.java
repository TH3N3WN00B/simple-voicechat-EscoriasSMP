/**
 * Positional-audio hot paths. OLD = git HEAD (Vec3.subtract().normalize() +
 * Vec2 + Utils.angle, whatever-Vec3.distanceTo, new float[] per call).
 * NEW = working tree (direct atan2, manual distance, ThreadLocal scratch).
 */
public class Stereo {

    // ── Utils.normalizeAngle (identical in both versions) ────────────────────
    static float normalizeAngle(float angle) {
        angle = angle % 360F;
        if (angle <= -180F) {
            angle += 360F;
        } else if (angle > 180F) {
            angle -= 360F;
        }
        return angle;
    }

    // ── Utils.angle(Vec2, Vec2) — used only by the OLD path ──────────────────
    static float angle(Vec2 v1, Vec2 v2) {
        return (float) Math.toDegrees(Math.atan2(v1.x * v2.x + v1.y * v2.y, v1.x * v2.y - v1.y * v2.x));
    }

    /** OLD: subtract() + normalize() (2 Vec3 allocs) + Vec2 (1 alloc) + angle() + new float[2] (1 alloc). */
    static float[] getStereoVolumeOld(Vec3 cameraPos, float yRot, Vec3 soundPos) {
        Vec3 d = soundPos.subtract(cameraPos).normalize();
        Vec2 diff = new Vec2((float) d.x, (float) d.z);
        float diffAngle = angle(diff, new Vec2(-1F, 0F));
        float ang = normalizeAngle(diffAngle - (yRot % 360F));
        float dif = (float) (Math.abs(cameraPos.y - soundPos.y) / 32);

        float rot = ang / 180F;
        float perc = rot;
        if (rot < -0.5F) {
            perc = -(0.5F + (rot + 0.5F));
        } else if (rot > 0.5F) {
            perc = 0.5F - (rot - 0.5F);
        }
        perc = perc * (1 - dif);

        float minVolume = 0.3F;
        float left = perc < 0F ? Math.abs(perc * 1.4F) + minVolume : minVolume;
        float right = perc >= 0F ? (perc * 1.4F) + minVolume : minVolume;
        float fill = 1F - Math.max(left, right);
        left += fill;
        right += fill;

        return new float[]{left, right};
    }

    /** NEW (reverted): identical to OLD — C2 scalar-replaces the non-escaping temporaries. */
    static float[] getStereoVolumeNew(Vec3 cameraPos, float yRot, Vec3 soundPos) {
        Vec3 d = soundPos.subtract(cameraPos).normalize();
        Vec2 diff = new Vec2((float) d.x, (float) d.z);
        float diffAngle = angle(diff, new Vec2(-1F, 0F));
        float ang = normalizeAngle(diffAngle - (yRot % 360F));
        float dif = (float) (Math.abs(cameraPos.y - soundPos.y) / 32);

        float rot = ang / 180F;
        float perc = rot;
        if (rot < -0.5F) {
            perc = -(0.5F + (rot + 0.5F));
        } else if (rot > 0.5F) {
            perc = 0.5F - (rot - 0.5F);
        }
        perc = perc * (1 - dif);

        float minVolume = 0.3F;
        float left = perc < 0F ? Math.abs(perc * 1.4F) + minVolume : minVolume;
        float right = perc >= 0F ? (perc * 1.4F) + minVolume : minVolume;
        float fill = 1F - Math.max(left, right);
        left += fill;
        right += fill;

        return new float[]{left, right};
    }

    /** OLD: Vec3.distanceTo -> temporary subtract Vec3 + sqrt allocation churn. */
    static float getDistanceVolumeOld(float maxDistance, Vec3 listenerPos, Vec3 pos) {
        float distance = (float) pos.distanceTo(listenerPos);
        distance = Math.min(distance, maxDistance);
        return 1F - distance / maxDistance;
    }

    /** NEW: manual euclidean distance, no temporary Vec3. */
    static float getDistanceVolumeNew(float maxDistance, Vec3 listenerPos, Vec3 pos) {
        float dx = (float) (listenerPos.x - pos.x);
        float dy = (float) (listenerPos.y - pos.y);
        float dz = (float) (listenerPos.z - pos.z);
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        distance = Math.min(distance, maxDistance);
        return 1F - distance / maxDistance;
    }
}