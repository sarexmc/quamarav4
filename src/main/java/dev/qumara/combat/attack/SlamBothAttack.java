package dev.qumara.combat.attack;

import dev.qumara.combat.DangerShape;
import dev.qumara.combat.DangerZone;
import dev.qumara.combat.Scale;
import dev.qumara.combat.Shockwave;
import dev.qumara.entity.QumaraEntity;
import dev.qumara.registry.ModSounds;
import dev.qumara.vfx.Vfx;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** MARTILLO DOBLE: levanta los dos brazos y los clava a la vez. Zona enorme, onda de choque mayor y pinchos de raiz en el borde. */
public class SlamBothAttack extends BossAttack {
    public static final float IMPACT = 34f, TOTAL = 60f;
    public static final float RADIUS = 4.6f * Scale.KF, SHOCK_RADIUS = 11f * Scale.KF;
    public static final double FORWARD = 5.0 * Scale.K;

    private Vec3d center;
    private DangerZone zone;

    @Override public String animation() { return "slam_both"; }
    @Override public float total() { return TOTAL; }

    @Override
    public void start(QumaraEntity b, LivingEntity target) {
        Vec3d c = b.getPos().add(b.forward().multiply(FORWARD));
        double y = b.groundY(c.x, c.z);
        center = new Vec3d(c.x, y, c.z);
        zone = show(b, new DangerShape.Circle(center.x, center.z, RADIUS), y, IMPACT, 9f, DangerZone.SLAM);
        show(b, new DangerShape.Circle(center.x, center.z, SHOCK_RADIUS), y, IMPACT, 14f, DangerZone.RING);
        b.sound(ModSounds.ARM_MOVE, 4.0f, 0.6f);
        b.sound(ModSounds.WARNING, 2.5f, 0.6f);
    }

    @Override
    public void tick(QumaraEntity b, float prev, float now) {
        if (crossed(prev, now, 10f)) b.sound(ModSounds.ARM_MOVE, 4.0f, 0.5f);
        if (crossed(prev, now, IMPACT - 8f)) b.sound(ModSounds.WARNING, 2.5f, 0.9f);
        if (crossed(prev, now, IMPACT - 4f)) b.sound(ModSounds.WARNING, 2.5f, 1.2f);
        if (!crossed(prev, now, IMPACT)) return;
        for (ServerPlayerEntity p : b.zones().playersIn(zone)) {
            b.hurtPlayer(p, 20f);
            Vec3d away = p.getPos().subtract(center).multiply(1, 0, 1);
            away = away.lengthSquared() < 1e-4 ? b.forward() : away.normalize();
            p.setVelocity(away.x * 1.0, 0.85, away.z * 1.0); p.velocityModified = true;
        }
        Vec3d side = b.right().multiply(1.1 * Scale.K);
        Vfx.slamImpact(b.sw(), center.add(side));
        Vfx.slamImpact(b.sw(), center.subtract(side));
        for (int i = 0; i < 10; i++) {                                           // pinchos de raiz alrededor del borde
            double a = Math.PI * 2 * i / 10 + b.rng().nextDouble() * 0.3;
            Vec3d at = center.add(Math.cos(a) * RADIUS * 0.92, 0, Math.sin(a) * RADIUS * 0.92);
            at = new Vec3d(at.x, b.groundY(at.x, at.z), at.z);
            b.plants().sprout(Blocks.MANGROVE_ROOTS.getDefaultState(), at, 0.9f * Scale.KF, (2.0f + b.rng().nextFloat()) * Scale.KF,
                    (b.rng().nextFloat() - .5f) * 30, (b.rng().nextFloat() - .5f) * 30, b.rng().nextFloat() * 360, 0, 4, 22, 8);
        }
        b.sound(ModSounds.IMPACT, 6.0f, 0.7f);
        b.sound(ModSounds.EARTH_CRACK, 4.5f, 0.6f);
        b.shake(20, 0.85f);
        b.addLingering(new Shockwave(center, RADIUS, SHOCK_RADIUS, b.ticks(12f), 7f));
    }
}
