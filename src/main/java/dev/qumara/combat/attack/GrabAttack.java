package dev.qumara.combat.attack;

import dev.qumara.combat.DangerShape;
import dev.qumara.combat.Scale;
import dev.qumara.combat.DangerZone;
import dev.qumara.combat.RootedManager;
import dev.qumara.entity.QumaraEntity;
import dev.qumara.registry.ModSounds;
import dev.qumara.vfx.Vfx;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** ATAQUE DE AGARRE: el brazo atrapa y estruja a quien la lengua arrastro y luego lo lanza. */
public class GrabAttack extends BossAttack {
    public static final float SQUEEZE = 20f, THROW = 36f, TOTAL = 48f;
    public static final double FORWARD = 3.5 * Scale.K, SIDE = 0.71 * Scale.K;
    public static final float RADIUS = 3.0f * Scale.KF;

    private final LivingEntity victim;
    private char side = 'r';
    private Vec3d center;
    private DangerZone zone;

    public GrabAttack(LivingEntity victim) { this.victim = victim; }

    @Override public String animation() { return "grab_" + side; }
    @Override public float total() { return TOTAL; }

    @Override
    public void start(QumaraEntity b, LivingEntity target) {
        Vec3d d = victim.getPos().subtract(b.getPos());
        side = d.dotProduct(b.right()) >= 0 ? 'r' : 'l';
        center = b.getPos().add(b.forward().multiply(FORWARD)).add(b.right().multiply(side == 'r' ? SIDE : -SIDE));
        double y = b.groundY(center.x, center.z);
        center = new Vec3d(center.x, y, center.z);
        zone = show(b, new DangerShape.Circle(center.x, center.z, RADIUS), y, SQUEEZE, 18f, DangerZone.GRAB);
        b.sound(ModSounds.ARM_MOVE, 3.0f, 0.9f);
    }

    @Override
    public void tick(QumaraEntity b, float prev, float now) {
        if (crossed(prev, now, SQUEEZE)) {
            for (ServerPlayerEntity p : b.zones().playersIn(zone)) {
                b.hurtPlayer(p, 12f);
                RootedManager.root(p, b.ticks(THROW - SQUEEZE));
            }
            Vfx.burst(b.sw(), Vfx.GREEN_DUST, center.add(0, 1.2, 0), 30, 1.2, 0.02);
            Vfx.burst(b.sw(), Vfx.ROOT, center.add(0, 1.5, 0), 20, 1.0, 0.1);
            b.sound(ModSounds.IMPACT, 3.0f, 1.3f);
            b.shake(8, 0.3f);
        }
        if (crossed(prev, now, THROW)) {
            for (ServerPlayerEntity p : b.zones().playersIn(zone)) {
                RootedManager.release(p);
                Vec3d v = b.forward().multiply(1.1);
                p.setVelocity(v.x, 0.75, v.z); p.velocityModified = true;
            }
            b.sound(ModSounds.TONGUE_LASH, 3.0f, 0.7f);
        }
    }
}
