package dev.qumara.combat;

import dev.qumara.entity.QumaraEntity;
import dev.qumara.vfx.Vfx;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Onda de choque expansiva: golpea (una vez) a los jugadores en el suelo cuando el anillo pasa por ellos; se esquiva saltando. */
public class Shockwave implements Lingering {
    private final Vec3d center; private final double r0, r1; private final int ticks; private final float damage;
    private final Set<UUID> hit = new HashSet<>();
    private final Consumer<ServerPlayerEntity> onHit;
    private int t = 0;

    public Shockwave(Vec3d center, double r0, double r1, int ticks, float damage) { this(center, r0, r1, ticks, damage, null); }

    public Shockwave(Vec3d center, double r0, double r1, int ticks, float damage, Consumer<ServerPlayerEntity> onHit) {
        this.center = center; this.r0 = r0; this.r1 = r1; this.ticks = Math.max(1, ticks); this.damage = damage; this.onHit = onHit;
    }

    @Override
    public boolean tick(QumaraEntity boss) {
        t++;
        double r = r0 + (r1 - r0) * Math.min(1.0, t / (double) ticks);
        Vfx.ring(boss.sw(), Vfx.DIRT, center, r, (int) (r * 7), 0.25, 0.04);
        Vfx.ring(boss.sw(), Vfx.BROWN_DUST, center, r, (int) (r * 3), 0.5, 0.0);
        for (ServerPlayerEntity p : boss.sw().getPlayers()) {
            if (p.isSpectator() || !p.isAlive() || hit.contains(p.getUuid())) continue;
            double d = Math.hypot(p.getX() - center.x, p.getZ() - center.z);
            if (Math.abs(d - r) <= 1.2 && p.isOnGround() && Math.abs(p.getY() - center.y) < 2.5) {
                hit.add(p.getUuid());
                if (damage > 0) boss.hurtPlayer(p, damage);
                if (onHit != null) onHit.accept(p);
                Vec3d away = new Vec3d(p.getX() - center.x, 0, p.getZ() - center.z).normalize();
                p.setVelocity(away.x * 0.9, 0.55, away.z * 0.9); p.velocityModified = true;
            }
        }
        return t >= ticks + 2;
    }
}
