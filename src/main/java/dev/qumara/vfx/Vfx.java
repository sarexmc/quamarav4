package dev.qumara.vfx;

import net.minecraft.block.Blocks;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/** Efectos de particulas del servidor. Cada ataque usa una "paleta" distinta (identidad visual propia). */
public final class Vfx {
    public static final ParticleEffect DIRT = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DIRT.getDefaultState());
    public static final ParticleEffect PODZOL = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PODZOL.getDefaultState());
    public static final ParticleEffect MOSS = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.MOSS_BLOCK.getDefaultState());
    public static final ParticleEffect LEAVES = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.getDefaultState());
    public static final ParticleEffect AZALEA = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.FLOWERING_AZALEA_LEAVES.getDefaultState());
    public static final ParticleEffect ROOT = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.MANGROVE_ROOTS.getDefaultState());
    public static final ParticleEffect STONE = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.getDefaultState());
    public static final ParticleEffect PINK_DUST = new DustParticleEffect(new Vector3f(0.95f, 0.25f, 0.62f), 1.6f);
    public static final ParticleEffect GREEN_DUST = new DustParticleEffect(new Vector3f(0.30f, 0.75f, 0.20f), 1.4f);
    public static final ParticleEffect RED_DUST = new DustParticleEffect(new Vector3f(0.95f, 0.10f, 0.10f), 1.8f);
    public static final ParticleEffect POISON_DUST = new DustParticleEffect(new Vector3f(0.45f, 0.95f, 0.12f), 1.8f);
    public static final ParticleEffect BROWN_DUST = new DustParticleEffect(new Vector3f(0.45f, 0.30f, 0.15f), 2.2f);

    /** Factor 0.3-1.0 segun jugadores presentes: con 100 jugadores cada particula se envia a todos, asi que se reduce la cantidad. */
    public static volatile double density = 1.0;
    private static int d(int n) { return n <= 1 ? n : Math.max(1, (int) Math.round(n * density)); }

    public static void burst(ServerWorld w, ParticleEffect p, Vec3d at, int count, double spread, double speed) {
        w.spawnParticles(p, at.x, at.y, at.z, d(count), spread, spread * 0.3, spread, speed);
    }

    /** Anillo de particulas en el suelo. */
    public static void ring(ServerWorld w, ParticleEffect p, Vec3d c, double r, int points, double dy, double speed) {
        points = Math.max(6, d(points));
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            w.spawnParticles(p, c.x + Math.cos(a) * r, c.y + dy, c.z + Math.sin(a) * r, 1, 0.05, 0.05, 0.05, speed);
        }
    }

    /** Impacto pesado (brazo aplastante): tierra, polvo, humo y grietas radiales. */
    public static void slamImpact(ServerWorld w, Vec3d c) {
        burst(w, DIRT, c.add(0, 0.3, 0), 130, 3.4, 0.28);
        burst(w, STONE, c.add(0, 0.5, 0), 60, 2.6, 0.38);
        burst(w, BROWN_DUST, c.add(0, 0.5, 0), 60, 3.6, 0.05);
        w.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.4, c.z, 3, 1.0, 0.1, 1.0, 0);
        w.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 0.5, c.z, 22, 2.6, 0.1, 2.6, 0.03);
        for (int i = 0; i < 10; i++) {                     // grietas radiales
            double a = Math.PI * 2 * i / 10 + Math.random() * 0.3;
            for (double d = 1.8; d < 9.0; d += 0.6)
                w.spawnParticles(DIRT, c.x + Math.cos(a) * d, c.y + 0.1, c.z + Math.sin(a) * d, 1, 0.05, 0.02, 0.05, 0.02);
        }
    }

    /** Arrastre del brazo: polvo, tierra, hojas y fragmentos a lo largo de la punta. */
    public static void drag(ServerWorld w, Vec3d tip, Vec3d dir) {
        burst(w, DIRT, tip.add(0, 0.2, 0), 8, 0.8, 0.12);
        burst(w, LEAVES, tip.add(0, 0.6, 0), 6, 0.9, 0.10);
        burst(w, STONE, tip.add(0, 0.3, 0), 4, 0.6, 0.18);
        w.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, tip.x, tip.y + 0.3, tip.z, 1, 0.6, 0.1, 0.6, 0.01);
        w.spawnParticles(ParticleTypes.COMPOSTER, tip.x, tip.y + 0.8, tip.z, 3, 0.7, 0.4, 0.7, 0.05);
    }

    public static void rootEruption(ServerWorld w, Vec3d c) {
        burst(w, DIRT, c.add(0, 0.2, 0), 28, 1.0, 0.22);
        burst(w, ROOT, c.add(0, 0.6, 0), 16, 0.8, 0.18);
        w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 0.8, c.z, 8, 0.8, 0.8, 0.8, 0.0);
        w.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, c.x, c.y + 1.2, c.z, 10, 1.0, 0.8, 1.0, 0.0);
    }

    public static void gardenBloom(ServerWorld w, Vec3d c) {
        burst(w, AZALEA, c.add(0, 0.8, 0), 22, 0.9, 0.12);
        burst(w, PINK_DUST, c.add(0, 1.2, 0), 14, 0.9, 0.02);
        w.spawnParticles(ParticleTypes.CHERRY_LEAVES, c.x, c.y + 1.5, c.z, 8, 1.0, 1.0, 1.0, 0.02);
        w.spawnParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, c.x, c.y + 2.5, c.z, 6, 1.2, 0.6, 1.2, 0.0);
    }

    public static void tongueTrail(ServerWorld w, Vec3d a, Vec3d b) {
        Vec3d d = b.subtract(a); int n = Math.max(2, (int) (d.length() * 2));
        for (int i = 0; i <= n; i++) {
            Vec3d p = a.add(d.multiply(i / (double) n));
            w.spawnParticles(PINK_DUST, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0);
            if (i % 3 == 0) w.spawnParticles(ParticleTypes.SPIT, p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.04);
        }
    }

    public static void vineTrail(ServerWorld w, Vec3d p) {
        w.spawnParticles(GREEN_DUST, p.x, p.y, p.z, 3, 0.15, 0.15, 0.15, 0);
        w.spawnParticles(LEAVES, p.x, p.y, p.z, 2, 0.2, 0.2, 0.2, 0.02);
    }

    /** Escupitajo de veneno: gota verde con estela. */
    public static void glob(ServerWorld w, Vec3d p) {
        w.spawnParticles(POISON_DUST, p.x, p.y, p.z, 4, 0.2, 0.2, 0.2, 0);
        w.spawnParticles(ParticleTypes.ITEM_SLIME, p.x, p.y, p.z, 3, 0.25, 0.25, 0.25, 0.02);
    }

    public static void poisonBurst(ServerWorld w, Vec3d c, double r) {
        burst(w, POISON_DUST, c.add(0, 0.8, 0), 40, r * 0.5, 0.06);
        burst(w, MOSS, c.add(0, 0.3, 0), 24, r * 0.4, 0.2);
        w.spawnParticles(ParticleTypes.SNEEZE, c.x, c.y + 0.8, c.z, d(16), r * 0.5, 0.5, r * 0.5, 0.05);
        w.spawnParticles(ParticleTypes.ITEM_SLIME, c.x, c.y + 0.5, c.z, d(20), r * 0.5, 0.3, r * 0.5, 0.15);
    }

    public static void furyAura(ServerWorld w, Vec3d head) {
        w.spawnParticles(RED_DUST, head.x, head.y, head.z, d(8), 2.8, 2.0, 2.8, 0);
        w.spawnParticles(ParticleTypes.CRIMSON_SPORE, head.x, head.y - 1.5, head.z, d(9), 3.4, 2.6, 3.4, 0);
        w.spawnParticles(ParticleTypes.ANGRY_VILLAGER, head.x, head.y + 1.0, head.z, 1, 1.2, 0.4, 1.2, 0);
    }

    private Vfx() {}
}
