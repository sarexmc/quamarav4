package dev.qumara.combat.attack;

import dev.qumara.combat.DangerShape;
import dev.qumara.combat.DangerZone;
import dev.qumara.combat.Scale;
import dev.qumara.combat.Shockwave;
import dev.qumara.entity.QumaraEntity;
import dev.qumara.registry.ModSounds;
import dev.qumara.vfx.Vfx;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;

/** RUGIDO ENSORDECEDOR: se golpea el pecho y ruge; una onda de choque enorme sale desde su cuerpo (se esquiva saltando). */
public class RoarAttack extends BossAttack {
    public static final float IMPACT = 44f, TOTAL = 80f, RADIUS = 14f * Scale.KF;

    @Override public String animation() { return "taunt"; }
    @Override public float total() { return TOTAL; }

    @Override
    public void start(QumaraEntity b, LivingEntity target) {
        double y = b.groundY(b.getX(), b.getZ());
        show(b, new DangerShape.Circle(b.getX(), b.getZ(), RADIUS), y, IMPACT, 16f, DangerZone.RING);
        b.sound(ModSounds.ARM_MOVE, 4.0f, 0.5f);
    }

    @Override
    public void tick(QumaraEntity b, float prev, float now) {
        if (crossed(prev, now, 18f)) { b.sound(ModSounds.IMPACT, 4.0f, 1.4f); b.shake(6, 0.25f); }          // golpe al pecho 1
        if (crossed(prev, now, 32f)) { b.sound(ModSounds.IMPACT, 4.0f, 1.2f); b.shake(6, 0.3f); }          // golpe al pecho 2
        if (crossed(prev, now, IMPACT - 6f)) b.sound(ModSounds.WARNING, 3.0f, 1.2f);
        if (now > 40f && now < IMPACT && (int) now % 2 == 0)
            Vfx.ring(b.sw(), Vfx.RED_DUST, b.getPos(), 2.5 + (now - 40f), 18, 0.4, 0);
        if (crossed(prev, now, IMPACT)) {
            b.sound(ModSounds.FURY, 8.0f, 0.5f);
            b.sound(ModSounds.IMPACT, 5.0f, 0.6f);
            b.shake(30, 0.9f);
            Vfx.slamImpact(b.sw(), b.getPos().add(b.forward().multiply(4.0 * Scale.K)));
            Vfx.ring(b.sw(), Vfx.BROWN_DUST, b.getPos().add(0, 0.2, 0), 3, 40, 0.2, 0);
            b.addLingering(new Shockwave(b.getPos(), 2.0 * Scale.K, RADIUS, b.ticks(16f), 9f,
                    p -> p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1))));
        }
    }
}
