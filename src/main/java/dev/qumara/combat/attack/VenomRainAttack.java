package dev.qumara.combat.attack;

import dev.qumara.combat.DangerShape;
import dev.qumara.combat.DangerZone;
import dev.qumara.combat.PoisonPool;
import dev.qumara.combat.Scale;
import dev.qumara.combat.VenomManager;
import dev.qumara.entity.QumaraEntity;
import dev.qumara.registry.ModSounds;
import dev.qumara.vfx.Vfx;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * LLUVIA DE VENENO: lanza el veneno al cielo en 4 andanadas; las gotas caen sobre circulos rojos (la mitad sobre jugadores) y
 * cada una deja un charco toxico. Escala con el numero de jugadores. Artilleria de area: obliga a no quedarse quieto.
 */
public class VenomRainAttack extends BossAttack {
    public static final float[] VOLLEY = {16f, 26f, 36f, 46f};
    public static final float FLIGHT = 22f, TOTAL = 72f, RADIUS = 2.6f;

    private record Drop(DangerZone zone, Vec3d at, float impact) {}
    private final List<Drop> drops = new ArrayList<>();

    @Override public String animation() { return "venom_rain"; }
    @Override public float total() { return TOTAL; }

    @Override
    public void start(QumaraEntity b, LivingEntity target) {
        b.sound(ModSounds.WARNING, 3.0f, 0.7f);
        b.sound(ModSounds.GARDEN_BLOOM, 3.5f, 0.5f);
    }

    @Override
    public void tick(QumaraEntity b, float prev, float now) {
        for (float v : VOLLEY) if (crossed(prev, now, v)) volley(b, v);
        for (int i = drops.size() - 1; i >= 0; i--) {
            Drop d = drops.get(i);
            if (now < d.impact && (int) now % 2 == 0) {
                double h = Math.max(0.5, (d.impact - now) * 1.2);
                b.sw().spawnParticles(Vfx.POISON_DUST, d.at.x, d.at.y + h, d.at.z, 3, 0.4, 0.2, 0.4, 0);
                b.sw().spawnParticles(ParticleTypes.ITEM_SLIME, d.at.x, d.at.y + h, d.at.z, 1, 0.3, 0.2, 0.3, 0);
            }
            if (crossed(prev, now, d.impact)) { impact(b, d); drops.remove(i); }
        }
    }

    private void volley(QumaraEntity b, float at) {
        List<ServerPlayerEntity> ps = b.players();
        int n = Math.min(30, 4 + b.phase() * 2 + Math.min(ps.size() / 2, 16));
        List<Vec3d> pts = new ArrayList<>();
        int guard = 0;
        while (pts.size() < n && guard++ < 120) {
            Vec3d p;
            if (!ps.isEmpty() && pts.size() < Math.min(Math.max(2, ps.size() / 3), ps.size())) {
                ServerPlayerEntity t = ps.get(b.rng().nextInt(ps.size()));
                p = t.getPos().add(t.getVelocity().multiply(b.ticks(FLIGHT) * 0.5, 0, b.ticks(FLIGHT) * 0.5));
            } else {
                double a = b.rng().nextDouble() * Math.PI * 2, r = (3.0 + b.rng().nextDouble() * 5.0) * Scale.K;
                p = b.getPos().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            }
            if (p.squaredDistanceTo(b.getPos().x, p.y, b.getPos().z) < 6.0 * 6.0) continue;
            boolean ok = true;
            for (Vec3d q : pts) if (q.squaredDistanceTo(p.x, q.y, p.z) < 5.0 * 5.0) { ok = false; break; }
            if (ok) pts.add(p);
        }
        for (Vec3d p : pts) {
            double y = b.groundY(p.x, p.z);
            DangerZone z = show(b, new DangerShape.Circle(p.x, p.z, RADIUS), y, FLIGHT, 5f, DangerZone.ROOTS);
            drops.add(new Drop(z, new Vec3d(p.x, y, p.z), at + FLIGHT));
        }
        b.sound(ModSounds.WARNING, 2.5f, 1.0f);
        b.sound(ModSounds.VINE_WHIP, 2.0f, 0.6f);
    }

    private void impact(QumaraEntity b, Drop d) {
        for (ServerPlayerEntity p : b.zones().playersIn(d.zone)) {
            b.hurtPlayer(p, 9f);
            VenomManager.apply(b, p, 100 + 20 * b.phase(), 1);
            p.setVelocity(0, 0.4, 0); p.velocityModified = true;
        }
        Vfx.poisonBurst(b.sw(), d.at, RADIUS);
        b.addLingering(new PoisonPool(d.at, RADIUS, 100));
        b.sound(ModSounds.ROOTS_GROW, 2.0f, 1.6f);
    }
}
