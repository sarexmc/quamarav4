package dev.qumara.combat.attack;

import dev.qumara.combat.DangerShape;
import dev.qumara.combat.DangerZone;
import dev.qumara.combat.Scale;
import dev.qumara.entity.QumaraEntity;
import dev.qumara.registry.ModSounds;
import dev.qumara.vfx.Vfx;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * LLUVIA DE ESPINAS: cinco andanadas. Cada una marca circulos rojos; 22 ticks despues caen espinas en ellos (dano + veneno).
 * Los circulos de cada andanada se muestran con tiempo de reaccion completo.
 */
public class ThornRainAttack extends BossAttack {
    public static final float[] VOLLEY = {20f, 27f, 34f, 41f, 48f};
    public static final float FLIGHT = 22f, TOTAL = 78f, RADIUS = 2.9f;

    private record Spike(DangerZone zone, Vec3d at, float impact) {}
    private final List<Spike> spikes = new ArrayList<>();

    @Override public String animation() { return "thorn_rain"; }
    @Override public float total() { return TOTAL; }

    @Override
    public void start(QumaraEntity b, LivingEntity target) {
        b.sound(ModSounds.WARNING, 3.0f, 0.8f);
        b.sound(ModSounds.GARDEN_BLOOM, 3.5f, 0.6f);
    }

    @Override
    public void tick(QumaraEntity b, float prev, float now) {
        for (float v : VOLLEY) if (crossed(prev, now, v)) volley(b, v);
        for (int i = spikes.size() - 1; i >= 0; i--) {
            Spike s = spikes.get(i);
            if (now < s.impact && (int) now % 2 == 0) {                            // espinas cayendo sobre el circulo
                double h = Math.max(0.5, (s.impact - now) * 0.9);
                b.sw().spawnParticles(Vfx.GREEN_DUST, s.at.x, s.at.y + h, s.at.z, 3, 0.5, 0.2, 0.5, 0);
                b.sw().spawnParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, s.at.x, s.at.y + h + 1.5, s.at.z, 1, 0.6, 0.3, 0.6, 0);
            }
            if (crossed(prev, now, s.impact)) {
                impact(b, s);
                spikes.remove(i);
            }
        }
    }

    private void volley(QumaraEntity b, float at) {
        List<ServerPlayerEntity> ps = b.players();
        int n = Math.min(36, 4 + b.phase() * 2 + Math.min(ps.size() / 2, 18));
        List<Vec3d> pts = new ArrayList<>();
        int guard = 0;
        while (pts.size() < n && guard++ < 120) {
            Vec3d p;
            if (pts.size() < Math.min(Math.max(2, ps.size() / 6), ps.size())) {
                ServerPlayerEntity t = ps.get(b.rng().nextInt(ps.size()));
                p = t.getPos().add(t.getVelocity().multiply(b.ticks(FLIGHT) * 0.5, 0, b.ticks(FLIGHT) * 0.5));
            } else {
                double a = b.rng().nextDouble() * Math.PI * 2, r = (3.0 + b.rng().nextDouble() * 10.0) * Scale.K;
                Vec3d base = (!ps.isEmpty() && b.rng().nextFloat() < 0.5f) ? ps.get(b.rng().nextInt(ps.size())).getPos() : b.getPos();
                if (base != b.getPos()) r = 1.0 + b.rng().nextDouble() * 8.0;
                p = base.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            }
            if (p.squaredDistanceTo(b.getPos().x, p.y, b.getPos().z) < 5.0 * 5.0) continue;
            boolean ok = true;
            for (Vec3d q : pts) if (q.squaredDistanceTo(p.x, q.y, p.z) < 4.6 * 4.6) { ok = false; break; }
            if (ok) pts.add(p);
        }
        for (Vec3d p : pts) {
            double y = b.groundY(p.x, p.z);
            DangerZone z = show(b, new DangerShape.Circle(p.x, p.z, RADIUS), y, FLIGHT, 5f, DangerZone.ROOTS);
            spikes.add(new Spike(z, new Vec3d(p.x, y, p.z), at + FLIGHT));
        }
        b.sound(ModSounds.WARNING, 2.5f, 1.0f);
        b.sound(ModSounds.VINE_WHIP, 2.0f, 0.8f);
    }

    private void impact(QumaraEntity b, Spike s) {
        Vfx.rootEruption(b.sw(), s.at);
        Vfx.burst(b.sw(), Vfx.GREEN_DUST, s.at.add(0, 1.0, 0), 20, 1.4, 0.05);
        b.plants().sprout(Blocks.POINTED_DRIPSTONE.getDefaultState(), s.at, 1.3f * Scale.KF, 3.0f * Scale.KF, 0, 0, b.rng().nextFloat() * 360, 0, 3, 26, 8);
        b.plants().sprout(Blocks.POINTED_DRIPSTONE.getDefaultState(), s.at.add(0.6, 0, 0.4), 0.9f * Scale.KF, 2.2f * Scale.KF, 10, -10, b.rng().nextFloat() * 360, 1, 3, 25, 8);
        for (ServerPlayerEntity p : b.zones().playersIn(s.zone)) {
            b.hurtPlayer(p, 10f);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 80 + b.phase() * 40, 1));
            p.setVelocity(0, 0.45, 0); p.velocityModified = true;
        }
        b.sound(ModSounds.ROOTS_GROW, 2.5f, 1.3f);
    }
}
