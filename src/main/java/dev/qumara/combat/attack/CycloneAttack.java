package dev.qumara.combat.attack;

import dev.qumara.combat.DangerShape;
import dev.qumara.combat.DangerZone;
import dev.qumara.combat.Scale;
import dev.qumara.entity.QumaraEntity;
import dev.qumara.registry.ModSounds;
import dev.qumara.vfx.Vfx;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CICLON: Qumara gira el torso dos vueltas con los dos brazos arrastrando por el suelo. La corona roja marca el anillo
 * donde pasan los brazos: se esquiva quedandose pegado al cuerpo (radio interior) o fuera del alcance. La curva del giro
 * (-28 -> 720 grados entre 1.0 y 3.2 s) es la misma que la de la animacion 'cyclone'.
 */
public class CycloneAttack extends BossAttack {
    public static final float TOTAL = 92f, T0 = 20f, T1 = 64f, PSI0 = -28f, PSI1 = 720f;
    public static final float R_IN = 2.8f * Scale.KF, R_OUT = 6.4f * Scale.KF;

    private DangerZone zone;
    private Vec3d center;
    private final Map<UUID, Long> lastHit = new HashMap<>();

    @Override public String animation() { return "cyclone"; }
    @Override public float total() { return TOTAL; }

    private static float smooth(float x) { x = MathHelper.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    private static float psiAt(float clock) { return PSI0 + (PSI1 - PSI0) * smooth((clock - T0) / (T1 - T0)); }

    @Override
    public void start(QumaraEntity b, LivingEntity target) {
        center = b.getPos();
        double y = b.groundY(center.x, center.z);
        Vec3d d0 = armDir(b, PSI0), d1 = armDir(b, PSI0 + 1f);
        double a0 = Math.atan2(d0.z, d0.x), a1 = Math.atan2(d1.z, d1.x);
        double sign = Math.signum(MathHelper.wrapDegrees((float) Math.toDegrees(a1 - a0)));
        zone = show(b, new DangerShape.Arc(center.x, center.z, R_IN, R_OUT, (float) a0, (float) (sign * Math.PI * 2 - sign * 1e-3)), y, T0, (T1 - T0) + 8f, DangerZone.SWEEP);
        b.sound(ModSounds.WARNING, 3.0f, 0.7f);
        b.sound(ModSounds.ARM_MOVE, 4.0f, 0.6f);
    }

    private static Vec3d armDir(QumaraEntity b, float psiDeg) {
        double a = Math.toRadians(psiDeg);
        return b.right().multiply(Math.cos(a)).add(b.forward().multiply(Math.sin(a)));
    }

    @Override
    public void tick(QumaraEntity b, float prev, float now) {
        if (crossed(prev, now, T0 - 6f)) b.sound(ModSounds.WARNING, 3.0f, 1.1f);
        if (now < T0 || prev > T1 + 1) return;
        float pn = psiAt(Math.min(now, T1)), pp = psiAt(Math.max(prev, T0));
        float lo = Math.min(pp, pn) - 8f, width = Math.abs(pn - pp) + 16f;
        long t = b.sw().getTime();
        for (int arm = 0; arm < 2; arm++) {
            float psi = pn + arm * 180f;
            Vec3d tip = center.add(armDir(b, psi).multiply(5.4 * Scale.K));
            Vfx.drag(b.sw(), new Vec3d(tip.x, b.groundY(tip.x, tip.z), tip.z), armDir(b, psi));
            Vfx.drag(b.sw(), center.add(armDir(b, psi).multiply(3.4 * Scale.K)), armDir(b, psi));
        }
        if ((int) now % 6 == 0) b.sound(ModSounds.ARM_MOVE, 2.5f, 0.55f + 0.3f * smooth((now - T0) / (T1 - T0)));
        for (ServerPlayerEntity p : b.zones().playersIn(zone)) {
            Long last = lastHit.get(p.getUuid());
            if (last != null && t - last < b.ticks(15f)) continue;
            Vec3d rel = p.getPos().subtract(center).multiply(1, 0, 1);
            double psiP = Math.toDegrees(Math.atan2(rel.dotProduct(b.forward()), rel.dotProduct(b.right())));
            for (int arm = 0; arm < 2; arm++) {
                double diff = ((psiP - arm * 180.0 - lo) % 360 + 360) % 360;
                if (diff > width) continue;
                lastHit.put(p.getUuid(), t);
                b.hurtPlayer(p, 11f);
                Vec3d out = rel.lengthSquared() < 1e-4 ? b.forward() : rel.normalize();
                double a = Math.toRadians(psiP + 90);
                Vec3d tangent = b.right().multiply(Math.cos(a)).add(b.forward().multiply(Math.sin(a)));
                p.setVelocity(out.x * 0.7 + tangent.x * 0.9, 0.5, out.z * 0.7 + tangent.z * 0.9); p.velocityModified = true;
                b.sound(ModSounds.IMPACT, 3.0f, 1.2f);
                break;
            }
        }
        if (crossed(prev, now, T1)) b.shake(12, 0.4f);
    }
}
