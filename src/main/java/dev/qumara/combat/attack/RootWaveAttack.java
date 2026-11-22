package dev.qumara.combat.attack;

import dev.qumara.combat.DangerShape;
import dev.qumara.combat.DangerZone;
import dev.qumara.combat.RootedManager;
import dev.qumara.combat.Scale;
import dev.qumara.entity.QumaraEntity;
import dev.qumara.registry.ModSounds;
import dev.qumara.vfx.Vfx;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * GRIETA DE RAICES: los brazos clavan el suelo y una ola de raices avanza en abanico (3, 5 o 7 franjas segun la fase).
 * Cada franja es una zona roja; el dano se aplica progresivamente: solo golpea donde la ola ya ha llegado.
 */
public class RootWaveAttack extends BossAttack {
    public static final float IMPACT = 24f, TOTAL = 60f, WAVE_TICKS = 14f;
    public static final double START = 2.2 * Scale.K, LENGTH = 11.0 * Scale.K, HALF = 1.5 * Scale.K;

    private record Lane(DangerZone zone, Vec3d origin, Vec3d dir) {}
    private final List<Lane> lanes = new ArrayList<>();
    private final Set<UUID> hit = new HashSet<>();
    private float lastFront = 0f;

    @Override public String animation() { return "root_wave"; }
    @Override public float total() { return TOTAL; }

    @Override
    public void start(QumaraEntity b, LivingEntity target) {
        int n = b.phase() >= 2 ? 7 : (b.fury() ? 5 : 3);
        double spread = n >= 5 ? 24 : 30;
        for (int i = 0; i < n; i++) {
            double ang = (i - (n - 1) / 2.0) * spread;
            Vec3d dir = aim(b, ang);
            Vec3d o = b.getPos().add(dir.multiply(START));
            double y = b.groundY(o.x, o.z);
            DangerZone z = show(b, new DangerShape.Strip(o.x, o.z, (float) dir.x, (float) dir.z, (float) LENGTH, (float) HALF), y, IMPACT, WAVE_TICKS + 8f, DangerZone.ROOTS);
            lanes.add(new Lane(z, new Vec3d(o.x, y, o.z), dir));
        }
        b.sound(ModSounds.WARNING, 2.5f, 0.7f);
        b.sound(ModSounds.EARTH_CRACK, 3.0f, 0.8f);
    }

    @Override
    public void tick(QumaraEntity b, float prev, float now) {
        if (now < IMPACT && (int) now % 4 == 0)
            for (Lane l : lanes) Vfx.burst(b.sw(), Vfx.DIRT, l.origin.add(l.dir.multiply(LENGTH * 0.5)).add(0, 0.2, 0), 3, 1.4, 0.02);
        if (crossed(prev, now, IMPACT)) { b.sound(ModSounds.IMPACT, 4.5f, 0.8f); b.shake(14, 0.5f); }
        if (now < IMPACT) return;
        float f = Math.min(1f, (now - IMPACT) / WAVE_TICKS);
        if (f <= lastFront && f < 1f) return;
        double from = lastFront * LENGTH, to = f * LENGTH;
        for (Lane l : lanes) {
            for (double d = Math.floor(from / 3.0) * 3.0 + 3.0; d <= to + 1e-6 && d <= LENGTH; d += 3.0) {      // un brote cada 3 bloques de la ola
                Vec3d at = l.origin.add(l.dir.multiply(d));
                at = new Vec3d(at.x, b.groundY(at.x, at.z), at.z);
                Vfx.rootEruption(b.sw(), at);
                for (int k = 0; k < 2; k++)
                    b.plants().sprout(Blocks.MANGROVE_ROOTS.getDefaultState(), at.add(l.dir.multiply(0.3 * k)), (1.2f - 0.3f * k) * Scale.KF, (2.6f - 0.6f * k) * Scale.KF,
                            (k == 0 ? 0 : 14), (k == 0 ? 0 : -12), k * 140f, 0, 4, 20, 8);
            }
            for (ServerPlayerEntity p : b.zones().playersIn(l.zone)) {
                if (hit.contains(p.getUuid())) continue;
                double along = p.getPos().subtract(l.origin).dotProduct(l.dir);
                if (along > to + 1.0) continue;                                      // la ola aun no ha llegado hasta ahi
                hit.add(p.getUuid());
                b.hurtPlayer(p, 12f);
                RootedManager.root(p, 45);
                p.setVelocity(0, 0.55, 0); p.velocityModified = true;
            }
        }
        if (to > from) b.sound(ModSounds.ROOTS_GROW, 3.0f, 0.8f + 0.4f * f);
        lastFront = f;
    }
}
