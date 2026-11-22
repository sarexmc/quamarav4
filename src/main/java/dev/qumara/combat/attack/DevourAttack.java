package dev.qumara.combat.attack;

import dev.qumara.combat.DangerShape;
import dev.qumara.combat.DangerZone;
import dev.qumara.combat.Scale;
import dev.qumara.entity.QumaraEntity;
import dev.qumara.registry.ModSounds;
import dev.qumara.vfx.Vfx;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * DEVORAR: abre la boca y succiona a todos los jugadores cercanos hacia su cabeza; al cerrar las fauces muerde el cono
 * frontal (zona roja). Se esquiva saliendo del cono (resistiendo la succion) o alejandose lateralmente.
 */
public class DevourAttack extends BossAttack {
    public static final float SUCK0 = 20f, CHOMP = 62f, TOTAL = 80f;
    public static final float R_IN = 1.0f * Scale.KF, R_OUT = 8.5f * Scale.KF, HALF_ANGLE = 55f;
    public static final double PULL_RANGE = 15.0 * Scale.K;

    private DangerZone zone;
    private Vec3d mouth;

    @Override public String animation() { return "devour"; }
    @Override public String tongueAnimation() { return "lengua_devorar"; }
    @Override public float total() { return TOTAL; }

    @Override
    public void start(QumaraEntity b, LivingEntity target) {
        Vec3d f = b.forward();
        double a = Math.atan2(f.z, f.x), half = Math.toRadians(HALF_ANGLE);
        double y = b.groundY(b.getX(), b.getZ());
        zone = show(b, new DangerShape.Arc(b.getX(), b.getZ(), R_IN, R_OUT, (float) (a - half), (float) (2 * half)), y, CHOMP, 10f, DangerZone.GRAB);
        mouth = b.getPos().add(f.multiply(1.6 * Scale.K)).add(0, Scale.MOUTH_Y, 0);
        b.sound(ModSounds.WARNING, 3.0f, 0.6f);
        b.sound(ModSounds.SPAWN_RUMBLE, 4.0f, 0.7f);
    }

    @Override
    public void tick(QumaraEntity b, float prev, float now) {
        if (now >= SUCK0 && now < CHOMP) {
            double progress = (now - SUCK0) / (CHOMP - SUCK0);
            double strength = 0.10 + 0.08 * progress;
            for (ServerPlayerEntity p : b.players()) {
                Vec3d d = mouth.subtract(p.getPos()).multiply(1, 0, 1);
                double dist = d.length();
                if (dist > PULL_RANGE || dist < 1.2) continue;
                Vec3d v = d.normalize().multiply(strength);
                Vec3d cur = p.getVelocity();
                double hx = cur.x + v.x, hz = cur.z + v.z, sp = Math.hypot(hx, hz);
                if (sp > 0.55) { hx *= 0.55 / sp; hz *= 0.55 / sp; }
                p.setVelocity(hx, cur.y, hz); p.velocityModified = true;
            }
            if ((int) now % 2 == 0) {                                            // anillos de succion que se cierran hacia la boca
                for (int ring = 0; ring < 3; ring++) {
                    double r = R_OUT * (1.0 - ((progress * 3 + ring / 3.0) % 1.0)) + 0.8;
                    Vec3d f = b.forward();
                    double a0 = Math.atan2(f.z, f.x);
                    for (int k = -4; k <= 4; k++) {
                        double a = a0 + Math.toRadians(HALF_ANGLE) * k / 4.0;
                        double x = b.getX() + Math.cos(a) * r, z = b.getZ() + Math.sin(a) * r;
                        b.sw().spawnParticles(Vfx.GREEN_DUST, x, b.groundY(x, z) + 0.4 + (1 - r / R_OUT) * 3.0, z, 1, 0.05, 0.05, 0.05, 0);
                    }
                }
                b.sw().spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, mouth.x, mouth.y, mouth.z, 4, 0.8, 0.8, 0.8, 0);
            }
            if (crossed(prev, now, SUCK0 + 20f)) b.sound(ModSounds.SPAWN_RUMBLE, 4.0f, 0.9f);
            if (crossed(prev, now, CHOMP - 6f)) b.sound(ModSounds.WARNING, 3.0f, 1.3f);
        }
        if (crossed(prev, now, CHOMP)) {
            for (ServerPlayerEntity p : b.zones().playersIn(zone)) {
                b.hurtPlayer(p, 22f);
                if (p.getPos().distanceTo(b.getPos()) < 3.2 * Scale.K) b.hurtPlayer(p, 10f);      // las fauces los atrapan de lleno
                Vec3d f = b.forward();
                p.setVelocity(f.x * 1.5, 0.7, f.z * 1.5); p.velocityModified = true;
            }
            Vfx.burst(b.sw(), Vfx.GREEN_DUST, mouth, 50, 2.2, 0.05);
            Vfx.burst(b.sw(), Vfx.LEAVES, mouth, 40, 2.5, 0.2);
            b.sw().spawnParticles(ParticleTypes.CRIT, mouth.x, mouth.y - 0.5, mouth.z, 30, 1.6, 0.6, 1.6, 0.3);
            b.sound(ModSounds.IMPACT, 6.0f, 1.0f);
            b.sound(ModSounds.FURY, 4.0f, 1.2f);
            b.shake(16, 0.7f);
        }
    }
}
