package dev.qumara.combat.attack;

import dev.qumara.combat.DangerShape;
import dev.qumara.combat.DangerZone;
import dev.qumara.combat.Scale;
import dev.qumara.combat.VenomManager;
import dev.qumara.entity.QumaraEntity;
import dev.qumara.registry.ModSounds;
import dev.qumara.vfx.Vfx;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * ALIENTO TOXICO: mantiene la boca abierta y barre un cono de gas de izquierda a derecha (80 grados en total). La zona roja marca
 * todo el sector; el cono real avanza por el. Quien esta dentro del cono recibe dano y veneno cada 4 ticks.
 */
public class PoisonBreathAttack extends BossAttack {
    public static final float T0 = 16f, T1 = 60f, TOTAL = 80f;
    public static final float START_DEG = -40f, END_DEG = 40f;      // grados a la DERECHA del frente (negativo = izquierda)
    public static final double HALF_ANGLE = 14.0;                   // semiapertura del cono
    public static final double R_IN = 2.0 * Scale.K, R_OUT = 9.0 * Scale.K;

    @Override public String animation() { return "poison_breath"; }
    @Override public float total() { return TOTAL; }

    private static Vec3d dirAt(QumaraEntity b, float now) {
        double u = MathHelper.clamp((now - T0) / (T1 - T0), 0f, 1f);
        return aim(b, START_DEG + (END_DEG - START_DEG) * u);
    }

    @Override
    public void start(QumaraEntity b, LivingEntity target) {
        Vec3d d0 = aim(b, START_DEG), d1 = aim(b, START_DEG + 1f);
        double a0 = Math.atan2(d0.z, d0.x), a1 = Math.atan2(d1.z, d1.x);
        double sign = Math.signum(MathHelper.wrapDegrees((float) Math.toDegrees(a1 - a0)));
        float sweep = (float) (sign * Math.toRadians(END_DEG - START_DEG));
        show(b, new DangerShape.Arc(b.getX(), b.getZ(), (float) R_IN, (float) R_OUT, (float) a0, sweep), b.groundY(b.getX(), b.getZ()), T0, (T1 - T0) + 6f, DangerZone.SWEEP);
        b.sound(ModSounds.WARNING, 3.0f, 0.6f);
        b.sound(ModSounds.SPAWN_RUMBLE, 3.5f, 1.1f);
    }

    @Override
    public void tick(QumaraEntity b, float prev, float now) {
        if (now < T0 || now > T1 + 1f) return;
        Vec3d dir = dirAt(b, now);
        Vec3d mouth = b.getPos().add(dir.multiply(4.5)).add(0, Scale.MOUTH_Y * 0.9, 0);
        for (double d = 0; d < R_OUT; d += 2.2) {                                   // gas que sale de la boca y se ensancha
            double spread = 0.4 + d * 0.16;
            Vec3d p = mouth.add(dir.multiply(d)).add(0, -d * 0.18, 0);
            b.sw().spawnParticles(ParticleTypes.SNEEZE, p.x, p.y, p.z, 1, spread * 0.5, spread * 0.3, spread * 0.5, 0.01);
            if (((int) (d / 2.2) + (int) now) % 2 == 0) b.sw().spawnParticles(Vfx.POISON_DUST, p.x, p.y, p.z, 2, spread * 0.4, spread * 0.25, spread * 0.4, 0);
        }
        if (crossed(prev, now, T0) || ((int) now % 12 == 0 && (int) prev != (int) now)) b.sound(ModSounds.TONGUE_LASH, 3.0f, 0.5f);
        if ((int) now % 4 == 0 && (int) prev != (int) now) {
            double cosMax = Math.cos(Math.toRadians(HALF_ANGLE));
            for (ServerPlayerEntity p : b.players()) {
                Vec3d rel = p.getPos().subtract(b.getPos()).multiply(1, 0, 1);
                double dist = rel.length();
                if (dist < R_IN || dist > R_OUT || Math.abs(p.getY() - b.groundY(p.getX(), p.getZ())) > 9.0) continue;
                if (rel.normalize().dotProduct(dir) < cosMax) continue;
                b.hurtPlayer(p, 3f);
                VenomManager.apply(b, p, 80, 1);
            }
        }
    }
}
