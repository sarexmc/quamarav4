package dev.qumara.combat;

import dev.qumara.entity.QumaraEntity;
import dev.qumara.vfx.Vfx;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Charco de veneno en el suelo: quien lo pisa recibe veneno y un poco de dano periodico. */
public class PoisonPool implements Lingering {
    private final Vec3d c; private final double r; private int left;

    public PoisonPool(Vec3d center, double radius, int ticks) { this.c = center; this.r = radius; this.left = ticks; }

    @Override
    public boolean tick(QumaraEntity b) {
        left--;
        if (left % 3 == 0) {
            for (int i = 0; i < Math.max(2, (int) (r * 1.2 * Vfx.density)); i++) {
                double a = b.rng().nextDouble() * Math.PI * 2, d = Math.sqrt(b.rng().nextDouble()) * r;
                b.sw().spawnParticles(Vfx.POISON_DUST, c.x + Math.cos(a) * d, c.y + 0.15, c.z + Math.sin(a) * d, 1, 0.05, 0.02, 0.05, 0);
            }
            b.sw().spawnParticles(ParticleTypes.ITEM_SLIME, c.x, c.y + 0.3, c.z, 1, r * 0.5, 0.1, r * 0.5, 0);
        }
        if (left % 10 == 0) {
            for (ServerPlayerEntity p : b.players()) {
                if (Math.hypot(p.getX() - c.x, p.getZ() - c.z) <= r && Math.abs(p.getY() - c.y) < 3.0) {
                    VenomManager.apply(b, p, 60, 1);
                    b.hurtPlayer(p, 1.5f);
                }
            }
        }
        return left <= 0;
    }
}
