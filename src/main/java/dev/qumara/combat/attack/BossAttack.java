package dev.qumara.combat.attack;

import dev.qumara.combat.DangerShape;
import dev.qumara.combat.DangerZone;
import dev.qumara.entity.QumaraEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Un ataque del jefe. El "reloj" esta en ticks de ANIMACION (20 por segundo de animacion): el servidor lo avanza
 * 'speed' por tick (1.0 normal, 1.25 en furia), de modo que los eventos (impacto, zonas) quedan sincronizados con
 * el momento exacto de la animacion correspondiente.
 */
public abstract class BossAttack {
    protected final List<DangerZone> zs = new ArrayList<>();

    public abstract String animation();
    public String tongueAnimation() { return null; }
    public abstract float total();
    public abstract void start(QumaraEntity boss, LivingEntity target);
    public abstract void tick(QumaraEntity boss, float prev, float now);
    /** Ataque encadenado (combos / variantes de furia). */
    public BossAttack next(QumaraEntity boss) { return null; }

    public void end(QumaraEntity boss) {
        for (DangerZone z : zs) boss.zones().remove(z);
        zs.clear();
    }

    protected DangerZone show(QumaraEntity b, DangerShape shape, double y, float clockImpact, float clockLinger, int style) {
        DangerZone z = b.zones().show(shape, y, b.ticks(clockImpact), b.ticks(clockLinger), style);
        zs.add(z);
        return z;
    }

    /** Direccion horizontal girada 'degRight' grados a la derecha del frente del jefe (negativo = izquierda). */
    protected static Vec3d aim(QumaraEntity b, double degRight) {
        double a = Math.toRadians(degRight);
        return b.forward().multiply(Math.cos(a)).add(b.right().multiply(Math.sin(a)));
    }

    protected static boolean crossed(float prev, float now, float t) { return prev < t && now >= t; }
}
