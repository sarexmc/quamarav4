package dev.qumara.combat.attack;

import dev.qumara.combat.Lingering;
import dev.qumara.combat.PoisonPool;
import dev.qumara.combat.RootedManager;
import dev.qumara.combat.DangerShape;
import dev.qumara.combat.DangerZone;
import dev.qumara.combat.Scale;
import dev.qumara.combat.Shockwave;
import dev.qumara.combat.VenomManager;
import dev.qumara.entity.QumaraEntity;
import dev.qumara.registry.ModSounds;
import dev.qumara.vfx.Vfx;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * GARRA MORTAL - agarrar y lanzar. La garra cae sobre un circulo rojo delante del jefe; quien este dentro queda ATRAPADO en la mano
 * (se mueve con ella: la posicion sale de la trayectoria real de la animacion) y sufre estrujones.
 *   slam=false (LANZAR):  lo sube, lo sacude y lo lanza lejos; al caer recibe dano de impacto, veneno y deja un charco.
 *   slam=true  (ESTRELLAR, furia+): lo golpea dos veces contra el suelo con onda de choque y lo arroja a un lado.
 * Se escapa esquivando el circulo; con varios jugadores atrapa hasta 1 + fase.
 */
public class GripAttack extends BossAttack {
    public static final float CATCH = 23f, THROW = 56f, TOTAL = 78f;
    public static final float[] SHAKE = {40f, 44f, 48f};              // estrujones (lanzar)
    public static final float SLAM1 = 41f, SLAM2 = 57f, TOSS = 67f;   // estrellar
    public static final double FORWARD_BLOCKS = 9.0;                  // debe coincidir con GRIP_P de tools/anims.py
    public static final float RADIUS = 2.2f * Scale.KF;

    private final boolean slam;
    private final List<ServerPlayerEntity> held = new ArrayList<>();
    private DangerZone zone; private Vec3d center;

    public GripAttack(boolean slam) { this.slam = slam; }

    @Override public String animation() { return slam ? "grip_slam" : "grip_throw"; }
    @Override public float total() { return TOTAL; }

    @Override
    public void start(QumaraEntity b, LivingEntity target) {
        center = b.getPos().add(b.forward().multiply(FORWARD_BLOCKS));
        double y = b.groundY(center.x, center.z);
        center = new Vec3d(center.x, y, center.z);
        zone = show(b, new DangerShape.Circle(center.x, center.z, RADIUS), y, CATCH, 14f, DangerZone.GRAB);
        b.sound(ModSounds.ARM_MOVE, 3.5f, 0.6f);
        b.sound(ModSounds.WARNING, 3.0f, 0.7f);
    }

    @Override
    public void tick(QumaraEntity b, float prev, float now) {
        if (crossed(prev, now, CATCH - 4f)) b.sound(ModSounds.WARNING, 3.0f, 1.2f);
        if (crossed(prev, now, CATCH)) {
            List<ServerPlayerEntity> in = new ArrayList<>(b.zones().playersIn(zone));
            in.sort(Comparator.comparingDouble(p -> p.squaredDistanceTo(center)));
            for (int i = 0; i < Math.min(in.size(), 1 + b.phase()); i++) {
                ServerPlayerEntity p = in.get(i);
                held.add(p);
                b.hurtPlayer(p, 8f);
                RootedManager.root(p, b.ticks((slam ? TOSS : THROW) - CATCH) + 20);
            }
            Vfx.burst(b.sw(), Vfx.GREEN_DUST, center.add(0, 1.2, 0), 30, 1.6, 0.03);
            b.sound(ModSounds.IMPACT, 4.0f, 1.2f);
            b.shake(10, 0.4f);
        }
        float carryEnd = slam ? TOSS : THROW;
        if (now > CATCH && now < carryEnd) carry(b, now);
        if (!slam) {
            for (float t : SHAKE) if (crossed(prev, now, t)) squeeze(b);
            if (crossed(prev, now, THROW)) release(b, b.forward().multiply(2.0), 0.9);
        } else {
            if (crossed(prev, now, SLAM1) || crossed(prev, now, SLAM2)) smash(b, now);
            if (crossed(prev, now, TOSS)) release(b, b.right().multiply(1.8), 0.8);
        }
    }

    private void carry(QumaraEntity b, float now) {
        Vec3d h = b.handPos(animation(), now);
        if (h == null) return;
        for (ServerPlayerEntity p : held) {
            if (!p.isAlive()) continue;
            p.requestTeleport(h.x, h.y - 1.6, h.z);
            p.setVelocity(Vec3d.ZERO); p.velocityModified = true; p.fallDistance = 0;
        }
        if ((int) now % 3 == 0) Vfx.burst(b.sw(), Vfx.GREEN_DUST, h, 3, 0.8, 0.0);
    }

    private void squeeze(QumaraEntity b) {
        for (ServerPlayerEntity p : held) {
            if (!p.isAlive()) continue;
            b.hurtPlayer(p, 5f);
            VenomManager.apply(b, p, 80, 1);
        }
        b.sound(ModSounds.IMPACT, 2.5f, 1.5f);
        b.shake(6, 0.25f);
    }

    private void smash(QumaraEntity b, float now) {
        Vec3d h = b.handPos(animation(), now);
        Vec3d at = h == null ? center : new Vec3d(h.x, b.groundY(h.x, h.z), h.z);
        for (ServerPlayerEntity p : held) {
            if (!p.isAlive()) continue;
            p.requestTeleport(at.x, at.y + 0.1, at.z);
            b.hurtPlayer(p, 20f);
            VenomManager.apply(b, p, 100, 1);
        }
        Vfx.slamImpact(b.sw(), at);
        b.sound(ModSounds.IMPACT, 5.0f, 0.8f);
        b.sound(ModSounds.EARTH_CRACK, 4.0f, 0.8f);
        b.shake(16, 0.7f);
        b.addLingering(new Shockwave(at, 2.0, 4.0 * Scale.K, b.ticks(10f), 7f));
    }

    private void release(QumaraEntity b, Vec3d dir, double up) {
        for (ServerPlayerEntity p : held) {
            RootedManager.release(p);
            if (!p.isAlive()) continue;
            p.setVelocity(dir.x, up, dir.z); p.velocityModified = true;
            b.addLingering(new ThrowImpact(p));
        }
        held.clear();
        b.sound(ModSounds.TONGUE_LASH, 4.0f, 0.6f);
        b.shake(8, 0.3f);
    }

    @Override
    public void end(QumaraEntity b) {
        for (ServerPlayerEntity p : held) RootedManager.release(p);
        held.clear();
        super.end(b);
    }

    /** Vigila al jugador lanzado: cuando toca el suelo o una pared recibe el golpe, veneno y se forma un charco. */
    private static final class ThrowImpact implements Lingering {
        private final ServerPlayerEntity p; private int t = 0;
        ThrowImpact(ServerPlayerEntity p) { this.p = p; }

        @Override
        public boolean tick(QumaraEntity b) {
            t++;
            if (!p.isAlive() || p.isRemoved() || t > 100) return true;
            if (t % 2 == 0) Vfx.burst(b.sw(), Vfx.GREEN_DUST, p.getPos().add(0, 1, 0), 3, 0.4, 0.0);
            if (t >= 4 && (p.isOnGround() || p.horizontalCollision)) {
                b.hurtPlayer(p, 16f);
                VenomManager.apply(b, p, 100, 1);
                Vfx.poisonBurst(b.sw(), p.getPos(), 3.0);
                b.sound(ModSounds.IMPACT, 3.0f, 1.0f);
                b.addLingering(new PoisonPool(new Vec3d(p.getX(), p.getY(), p.getZ()), 3.2, 120));
                return true;
            }
            return false;
        }
    }
}
