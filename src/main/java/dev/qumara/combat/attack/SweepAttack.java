package dev.qumara.combat.attack;

import dev.qumara.combat.DangerShape;
import dev.qumara.combat.Scale;
import dev.qumara.combat.DangerZone;
import dev.qumara.entity.QumaraEntity;
import dev.qumara.registry.ModSounds;
import dev.qumara.vfx.Vfx;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * ATAQUE 4 - ATAQUE ARRASTRADO. El brazo se levanta, baja y arrastra por el suelo en un arco (angulo de yaw del hombro
 * psi: 0 = lateral, 90 = frente). La franja roja es el sector anular completo que recorre el brazo; el dano se aplica
 * solo en la cuña que el brazo ocupa en cada tick (siempre dentro de la franja).
 * En furia vuelve en sentido contrario con el otro brazo.
 */
public class SweepAttack extends BossAttack {
    public static final float PSI0 = -15f, PSI1 = 150f, T0 = 20f, T1 = 40f, TOTAL = 68f, R_IN = 2.8f * Scale.KF, R_OUT = 6.4f * Scale.KF;
    public static final double SIDE = 0.71 * Scale.K, BACK = 0.5 * Scale.K;

    private char side;
    private final boolean second;
    private Vec3d shoulder;
    private final Set<UUID> hit = new HashSet<>();
    private DangerZone zone;

    public SweepAttack() { this(' ', false); }
    private SweepAttack(char side, boolean second) { this.side = side; this.second = second; }

    @Override public String animation() { return "sweep_" + side; }
    @Override public float total() { return TOTAL; }

    private Vec3d dir(QumaraEntity b, float psiDeg) {
        double s = side == 'r' ? 1 : -1, a = Math.toRadians(psiDeg);
        return b.right().multiply(s * Math.cos(a)).add(b.forward().multiply(Math.sin(a)));
    }

    private static float smooth(float x) { x = MathHelper.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    private static float psiAt(float clock) { return PSI0 + (PSI1 - PSI0) * smooth((clock - T0) / (T1 - T0)); }

    @Override
    public void start(QumaraEntity b, LivingEntity target) {
        if (side == ' ') {
            Vec3d d = target == null ? Vec3d.ZERO : target.getPos().subtract(b.getPos());
            side = d.dotProduct(b.right()) >= 0 ? 'l' : 'r';      // barre desde el lado contrario: cruza hacia el objetivo
        }
        double s = side == 'r' ? SIDE : -SIDE;
        shoulder = b.getPos().add(b.right().multiply(s)).add(b.forward().multiply(-BACK));
        double y = b.groundY(shoulder.x, shoulder.z);
        shoulder = new Vec3d(shoulder.x, y, shoulder.z);
        Vec3d d0 = dir(b, PSI0), d1 = dir(b, PSI0 + 1f);
        double a0 = Math.atan2(d0.z, d0.x), a1 = Math.atan2(d1.z, d1.x);
        double sign = Math.signum(MathHelper.wrapDegrees((float) Math.toDegrees(a1 - a0)));
        float sweep = (float) (sign * Math.toRadians(PSI1 - PSI0));
        zone = show(b, new DangerShape.Arc(shoulder.x, shoulder.z, R_IN, R_OUT, (float) a0, sweep), y, T0, (T1 - T0) + 6f, DangerZone.SWEEP);
        b.sound(ModSounds.ARM_MOVE, 3.0f, 0.7f);
        b.sound(ModSounds.WARNING, 2.0f, 0.8f);
    }

    @Override
    public void tick(QumaraEntity b, float prev, float now) {
        if (crossed(prev, now, T0 - 5f)) b.sound(ModSounds.WARNING, 2.0f, 1.1f);
        if (crossed(prev, now, T0)) b.sound(ModSounds.ARM_MOVE, 3.5f, 0.5f);
        if (now < T0 || prev > T1 + 1) return;
        float p0 = psiAt(Math.max(prev, T0)), p1 = psiAt(Math.min(now, T1));
        float lo = Math.min(p0, p1) - 7f, hi = Math.max(p0, p1) + 7f;
        double s = side == 'r' ? 1 : -1;
        // particulas en la punta del brazo (r ~ 5.4)
        Vec3d tip = shoulder.add(dir(b, p1).multiply(5.4 * Scale.K));
        Vfx.drag(b.sw(), new Vec3d(tip.x, b.groundY(tip.x, tip.z), tip.z), dir(b, p1));
        Vfx.drag(b.sw(), shoulder.add(dir(b, p1).multiply(3.6 * Scale.K)), dir(b, p1));
        if (now > T0 + 2 && (int) now % 4 == 0) b.sound(ModSounds.ARM_MOVE, 2.0f, 0.6f);
        for (ServerPlayerEntity pl : b.zones().playersIn(zone)) {
            if (hit.contains(pl.getUuid())) continue;
            Vec3d rel = pl.getPos().subtract(shoulder).multiply(1, 0, 1);
            double psi = Math.toDegrees(Math.atan2(rel.dotProduct(b.forward()), rel.dotProduct(b.right().multiply(s))));
            if (psi < lo || psi > hi) continue;
            hit.add(pl.getUuid());
            b.hurtPlayer(pl, 12f);
            double a = Math.toRadians(psi);
            Vec3d tangent = b.right().multiply(-s * Math.sin(a)).add(b.forward().multiply(Math.cos(a)));
            pl.setVelocity(tangent.x * 1.5, 0.45, tangent.z * 1.5); pl.velocityModified = true;
            b.sound(ModSounds.IMPACT, 2.5f, 1.2f);
        }
        if (crossed(prev, now, T1)) b.shake(8, 0.3f);
    }

    @Override
    public BossAttack next(QumaraEntity b) {
        return (b.fury() && !second) ? new SweepAttack(side == 'r' ? 'l' : 'r', true) : null;
    }
}
