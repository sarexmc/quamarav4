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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * ESCUPITAJO VENENOSO: echa la cabeza atras y lanza 3 (fase 0), 4 (furia) o 5 (ira) gotas en parabola en abanico hacia el objetivo.
 * Cada gota marca su circulo rojo al salir; al caer hace dano, da veneno y deja un charco toxico.
 */
public class PoisonSpitAttack extends BossAttack {
    public static final float[] SPIT = {20f, 26f, 32f, 38f, 44f};
    public static final float FLIGHT = 18f, TOTAL = 70f;
    public static final float RADIUS = 1.5f * Scale.KF;

    private record Glob(DangerZone zone, Vec3d from, Vec3d at, float launch, float impact) {}
    private final List<Glob> globs = new ArrayList<>();
    private LivingEntity target;
    private int count;

    @Override public String animation() { return "poison_spit"; }
    @Override public String tongueAnimation() { return "lengua_veneno"; }
    @Override public float total() { return TOTAL; }

    @Override
    public void start(QumaraEntity b, LivingEntity target) {
        this.target = target;
        count = Math.min(SPIT.length, 3 + b.phase());
        b.sound(ModSounds.WARNING, 3.0f, 0.7f);
        b.sound(ModSounds.GARDEN_BLOOM, 3.0f, 0.5f);
    }

    @Override
    public void tick(QumaraEntity b, float prev, float now) {
        for (int i = 0; i < count; i++) if (crossed(prev, now, SPIT[i])) launch(b, i, SPIT[i]);
        for (int i = globs.size() - 1; i >= 0; i--) {
            Glob g = globs.get(i);
            if (now >= g.launch && now < g.impact) {
                double u = (now - g.launch) / (g.impact - g.launch);
                Vec3d p = g.from.lerp(g.at, u).add(0, 4.0 * u * (1 - u) * (g.from.distanceTo(g.at) * 0.4 + 2.0), 0);
                Vfx.glob(b.sw(), p);
            }
            if (crossed(prev, now, g.impact)) { impact(b, g); globs.remove(i); }
        }
    }

    private void launch(QumaraEntity b, int i, float at) {
        Vec3d base = target != null && target.isAlive() ? target.getPos().add(target.getVelocity().multiply(b.ticks(FLIGHT) * 0.6, 0, b.ticks(FLIGHT) * 0.6)) : b.getPos().add(b.forward().multiply(10.0 * Scale.K));
        Vec3d dir = base.subtract(b.getPos()).multiply(1, 0, 1);
        dir = dir.lengthSquared() < 1e-3 ? b.forward() : dir.normalize();
        Vec3d side = new Vec3d(-dir.z, 0, dir.x);
        double spread = (i - (count - 1) / 2.0) * 4.5;                       // abanico
        Vec3d p = base.add(side.multiply(spread)).add(dir.multiply((i % 2 == 0 ? 0 : 3.0)));
        double y = b.groundY(p.x, p.z);
        Vec3d from = b.getPos().add(b.forward().multiply(5.0)).add(0, Scale.MOUTH_Y + 1.5, 0);
        DangerZone z = show(b, new DangerShape.Circle(p.x, p.z, RADIUS), y, FLIGHT, 6f, DangerZone.ROOTS);
        globs.add(new Glob(z, from, new Vec3d(p.x, y, p.z), at, at + FLIGHT));
        b.sound(ModSounds.VINE_WHIP, 2.5f, 0.6f + 0.1f * i);
        Vfx.burst(b.sw(), Vfx.POISON_DUST, from, 12, 0.8, 0.1);
    }

    private void impact(QumaraEntity b, Glob g) {
        for (ServerPlayerEntity p : b.zones().playersIn(g.zone)) {
            b.hurtPlayer(p, 10f);
            VenomManager.apply(b, p, 100 + 20 * b.phase(), 1);
            p.setVelocity(0, 0.4, 0); p.velocityModified = true;
        }
        Vfx.poisonBurst(b.sw(), g.at, RADIUS);
        b.addLingering(new PoisonPool(g.at, RADIUS, 140));
        b.sound(ModSounds.ROOTS_GROW, 2.5f, 1.6f);
    }
}
