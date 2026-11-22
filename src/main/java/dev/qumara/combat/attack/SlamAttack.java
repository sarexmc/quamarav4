package dev.qumara.combat.attack;

import dev.qumara.combat.DangerShape;
import dev.qumara.combat.Scale;
import dev.qumara.combat.DangerZone;
import dev.qumara.combat.Shockwave;
import dev.qumara.entity.QumaraEntity;
import dev.qumara.registry.ModSounds;
import dev.qumara.vfx.Vfx;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** ATAQUE 3 - BRAZO APLASTANTE. En furia encadena un segundo golpe con el otro brazo. */
public class SlamAttack extends BossAttack {
    public static final float IMPACT = 29f, TOTAL = 52f;
    public static final float RADIUS = 3.0f * Scale.KF, SHOCK_RADIUS = 7.5f * Scale.KF;
    public static final double FORWARD = 4.8 * Scale.K, SIDE = 0.71 * Scale.K;

    private char side;
    private final boolean second;
    private Vec3d center;
    private DangerZone zone;

    public SlamAttack() { this(' ', false); }
    private SlamAttack(char side, boolean second) { this.side = side; this.second = second; }

    @Override public String animation() { return "slam_" + side; }
    @Override public float total() { return TOTAL; }

    @Override
    public void start(QumaraEntity b, LivingEntity target) {
        if (side == ' ') {
            Vec3d d = target == null ? Vec3d.ZERO : target.getPos().subtract(b.getPos());
            side = d.dotProduct(b.right()) >= 0 ? 'r' : 'l';
        }
        double s = side == 'r' ? SIDE : -SIDE;
        center = b.getPos().add(b.forward().multiply(FORWARD)).add(b.right().multiply(s));
        double y = b.groundY(center.x, center.z);
        center = new Vec3d(center.x, y, center.z);
        zone = show(b, new DangerShape.Circle(center.x, center.z, RADIUS), y, IMPACT, 8f, DangerZone.SLAM);
        show(b, new DangerShape.Circle(center.x, center.z, SHOCK_RADIUS), y, IMPACT, 12f, DangerZone.RING);
        b.sound(ModSounds.ARM_MOVE, 3.0f, 0.8f);
        b.sound(ModSounds.WARNING, 2.0f, 0.7f);
    }

    @Override
    public void tick(QumaraEntity b, float prev, float now) {
        if (crossed(prev, now, 8f)) b.sound(ModSounds.ARM_MOVE, 3.0f, 0.6f);
        if (crossed(prev, now, IMPACT - 4f)) b.sound(ModSounds.WARNING, 2.0f, 1.1f);
        if (crossed(prev, now, IMPACT)) impact(b);
    }

    private void impact(QumaraEntity b) {
        for (ServerPlayerEntity p : b.zones().playersIn(zone)) {
            b.hurtPlayer(p, 16f);
            Vec3d away = p.getPos().subtract(center).multiply(1, 0, 1);
            away = away.lengthSquared() < 1e-4 ? b.forward() : away.normalize();
            p.setVelocity(away.x * 0.8, 0.7, away.z * 0.8); p.velocityModified = true;
        }
        Vfx.slamImpact(b.sw(), center);
        b.sound(ModSounds.IMPACT, 4.0f, 0.9f);
        b.sound(ModSounds.EARTH_CRACK, 3.0f, 0.8f);
        b.shake(14, 0.55f);
        b.addLingering(new Shockwave(center, RADIUS, SHOCK_RADIUS, b.ticks(10f), 7f));
    }

    @Override
    public BossAttack next(QumaraEntity b) {
        return (b.fury() && !second) ? new SlamAttack(side == 'r' ? 'l' : 'r', true) : null;
    }
}
