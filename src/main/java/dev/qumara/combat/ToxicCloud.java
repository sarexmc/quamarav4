package dev.qumara.combat;

import dev.qumara.entity.QumaraEntity;
import dev.qumara.vfx.Vfx;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Nube toxica que se expande desde el jefe hasta rMax y permanece 'life' ticks. Se esquiva saliendo de ella a tiempo. */
public class ToxicCloud implements Lingering {
    private final Vec3d c; private final double r0, rMax; private final int grow, life; private int t = 0;

    public ToxicCloud(Vec3d center, double r0, double rMax, int growTicks, int lifeTicks) { c = center; this.r0 = r0; this.rMax = rMax; grow = Math.max(1, growTicks); life = lifeTicks; }

    @Override
    public boolean tick(QumaraEntity b) {
        t++;
        double u = Math.min(1.0, t / (double) grow), r = r0 + (rMax - r0) * (1 - (1 - u) * (1 - u));
        if (t % 2 == 0) {
            Vfx.ring(b.sw(), Vfx.POISON_DUST, c, r, (int) (r * 5), 0.5 + 1.5 * b.rng().nextDouble(), 0.01);
            for (int i = 0; i < Math.max(2, (int) (14 * Vfx.density)); i++) {
                double a = b.rng().nextDouble() * Math.PI * 2, d = Math.sqrt(b.rng().nextDouble()) * r;
                b.sw().spawnParticles(ParticleTypes.SNEEZE, c.x + Math.cos(a) * d, c.y + 0.4 + b.rng().nextDouble() * 3.0, c.z + Math.sin(a) * d, 1, 0.1, 0.1, 0.1, 0.01);
            }
        }
        if (t % 10 == 0) {
            for (ServerPlayerEntity p : b.players()) {
                if (Math.hypot(p.getX() - c.x, p.getZ() - c.z) <= r && Math.abs(p.getY() - c.y) < 8.0) {
                    VenomManager.apply(b, p, 80, 1);
                    b.hurtPlayer(p, 2.5f);
                }
            }
        }
        return t >= grow + life;
    }
}
