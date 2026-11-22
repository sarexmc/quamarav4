package dev.qumara.combat.attack;

import dev.qumara.combat.DangerShape;
import dev.qumara.combat.Scale;
import dev.qumara.combat.DangerZone;
import dev.qumara.combat.RootedManager;
import dev.qumara.entity.QumaraEntity;
import dev.qumara.registry.ModSounds;
import dev.qumara.vfx.Vfx;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** ATAQUE 1 - RAICES ATRAPADORAS. Circulos rojos donde emergen las raices; atrapan e inmovilizan. */
public class RootsAttack extends BossAttack {
    public static final float IMPACT = 26f, TOTAL = 48f, RADIUS = 2.3f;
    private final List<DangerZone> roots = new ArrayList<>();

    @Override public String animation() { return "roots_cast"; }
    @Override public float total() { return TOTAL; }

    @Override
    public void start(QumaraEntity b, LivingEntity target) {
        List<ServerPlayerEntity> ps = b.players();
        List<Vec3d> pts = new ArrayList<>();
        int total = Math.min(40, (b.phase() >= 2 ? 14 : (b.fury() ? 11 : 7)) + Math.min(ps.size() / 2, 24));
        // 1) una raiz sobre la posicion prevista de cada objetivo (max 3)
        List<ServerPlayerEntity> shuffled = new ArrayList<>(ps);
        java.util.Collections.shuffle(shuffled, b.rng());
        for (int i = 0; i < Math.min(Math.max(3, ps.size() / 5), shuffled.size()); i++) {      // varios objetivos a la vez si hay multitud
            ServerPlayerEntity p = shuffled.get(i);
            Vec3d lead = p.getPos().add(p.getVelocity().multiply(b.ticks(IMPACT) * 0.5, 0, b.ticks(IMPACT) * 0.5));
            tryAdd(pts, new Vec3d(lead.x, p.getY(), lead.z), b, 2.0);
        }
        // 2) resto repartidas alrededor de los jugadores / arena
        int guard = 0;
        while (pts.size() < total && guard++ < 80) {
            Vec3d c = ps.isEmpty() ? b.getPos() : ps.get(b.rng().nextInt(ps.size())).getPos();
            double a = b.rng().nextDouble() * Math.PI * 2, r = (2.5 + b.rng().nextDouble() * 6.5) * Scale.K;
            tryAdd(pts, new Vec3d(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r), b, 3.2);
        }
        for (Vec3d p : pts) {
            double y = b.groundY(p.x, p.z);
            roots.add(show(b, new DangerShape.Circle(p.x, p.z, RADIUS), y, IMPACT, 8f, DangerZone.ROOTS));
        }
        b.sound(ModSounds.WARNING, 2.0f, 0.8f);
        b.sound(ModSounds.EARTH_CRACK, 2.5f, 1.2f);
    }

    private void tryAdd(List<Vec3d> pts, Vec3d p, QumaraEntity b, double minSep) {
        if (p.squaredDistanceTo(b.getPos().x, p.y, b.getPos().z) < 5.2 * 5.2 || p.squaredDistanceTo(b.getPos().x, p.y, b.getPos().z) > 32 * 32) return;
        for (Vec3d q : pts) if (q.squaredDistanceTo(p.x, q.y, p.z) < minSep * minSep) return;
        pts.add(p);
    }

    @Override
    public void tick(QumaraEntity b, float prev, float now) {
        if (crossed(prev, now, 10f)) b.sound(ModSounds.ROOTS_GROW, 2.5f, 0.7f);
        // pequeñas sacudidas de tierra durante el aviso
        if (now < IMPACT && (int) now % 3 == 0)
            for (DangerZone z : roots) if (z.shape() instanceof DangerShape.Circle c)
                Vfx.burst(b.sw(), Vfx.DIRT, new Vec3d(c.x(), z.y() + 0.1, c.z()), 2, 0.7, 0.03);
        if (crossed(prev, now, IMPACT)) {
            for (DangerZone z : roots) {
                DangerShape.Circle c = (DangerShape.Circle) z.shape();
                Vec3d at = new Vec3d(c.x(), z.y(), c.z());
                Vfx.rootEruption(b.sw(), at);
                float[] h = {3.2f, 2.5f, 2.0f}, tx = {0, 16, -10}, tz = {0, -8, 18}, yaw = {0, 120, 240};
                for (int i = 0; i < 3; i++)
                    b.plants().sprout(Blocks.MANGROVE_ROOTS.getDefaultState(), at.add(Math.cos(Math.toRadians(yaw[i])) * 0.45 * i, 0, Math.sin(Math.toRadians(yaw[i])) * 0.45 * i),
                            (1.1f - 0.15f * i) * Scale.KF, h[i] * Scale.KF, tx[i], tz[i], yaw[i], 0, 5, 36, 10);
                for (ServerPlayerEntity p : b.zones().playersIn(z)) {
                    b.hurtPlayer(p, 9f);
                    RootedManager.root(p, 50);
                    p.setVelocity(0, 0.25, 0); p.velocityModified = true;
                }
            }
            b.sound(ModSounds.ROOTS_GROW, 3.5f, 0.9f);
            b.sound(ModSounds.EARTH_CRACK, 3.0f, 1.0f);
            b.shake(10, 0.3f);
        }
    }
}
