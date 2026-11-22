package dev.qumara.combat.attack;

import dev.qumara.combat.DangerShape;
import dev.qumara.combat.DangerZone;
import dev.qumara.combat.Scale;
import dev.qumara.combat.ToxicCloud;
import dev.qumara.entity.QumaraEntity;
import dev.qumara.registry.ModSounds;
import dev.qumara.vfx.Vfx;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * NUBE TOXICA: se encoge, golpea el suelo y expulsa una nube de esporas que crece hasta 6.5 K bloques y dura ~5 s. Dentro de la nube
 * se acumula veneno cada medio segundo. El anillo rojo marca el alcance maximo: hay que salir de el (o curarse con leche).
 */
public class PoisonCloudAttack extends BossAttack {
    public static final float BURST = 24f, TOTAL = 64f;
    public static final double R = 6.5 * Scale.K;

    @Override public String animation() { return "poison_cloud"; }
    @Override public float total() { return TOTAL; }

    @Override
    public void start(QumaraEntity b, LivingEntity target) {
        Vec3d c = b.getPos();
        show(b, new DangerShape.Circle(c.x, c.z, (float) R), b.groundY(c.x, c.z), BURST, 40f, DangerZone.RING);
        b.sound(ModSounds.WARNING, 3.5f, 0.6f);
        b.sound(ModSounds.SPAWN_RUMBLE, 4.0f, 0.9f);
    }

    @Override
    public void tick(QumaraEntity b, float prev, float now) {
        if (now > 4f && now < BURST && (int) now % 3 == 0) Vfx.burst(b.sw(), Vfx.POISON_DUST, b.getPos().add(0, Scale.MOUTH_Y * 0.6, 0), 8, 3.0, 0.02);
        if (crossed(prev, now, BURST)) {
            Vec3d c = b.getPos();
            b.addLingering(new ToxicCloud(new Vec3d(c.x, b.groundY(c.x, c.z), c.z), 3.0 * Scale.K, R, b.ticks(40f), b.ticks(110f)));
            Vfx.poisonBurst(b.sw(), c, 5.0);
            b.sound(ModSounds.FURY, 5.0f, 1.3f);
            b.sound(ModSounds.GARDEN_BLOOM, 4.0f, 0.5f);
            b.shake(20, 0.5f);
        }
    }
}
