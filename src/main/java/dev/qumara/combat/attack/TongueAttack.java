package dev.qumara.combat.attack;

import dev.qumara.combat.DangerShape;
import dev.qumara.combat.Scale;
import dev.qumara.combat.DangerZone;
import dev.qumara.entity.QumaraEntity;
import dev.qumara.registry.ModSounds;
import dev.qumara.vfx.Vfx;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * ATAQUE 2 - LENGUA DEPREDADORA. Linea roja con la trayectoria (bloqueada al empezar el aviso: se puede esquivar).
 * Si alcanza al jugador lo arrastra hasta Qumara y se encadena el ataque de AGARRE.
 */
public class TongueAttack extends BossAttack {
    public static final float IMPACT = 25f, PULL_END = 38f, TOTAL = 64f;
    public static final double START = 1.8 * Scale.K, LENGTH = 7.0 * Scale.K, HALF_WIDTH = 1.1 * Scale.K, MOUTH_FWD = 1.3 * Scale.K;

    private DangerZone zone;
    private Vec3d dir;
    private final List<ServerPlayerEntity> pulled = new ArrayList<>();

    @Override public String animation() { return "idle"; }              // el cuerpo no cambia: la lengua tiene su propio controlador
    @Override public String tongueAnimation() { return "lengua_atacar"; }
    @Override public float total() { return TOTAL; }

    @Override
    public void start(QumaraEntity b, LivingEntity target) {
        dir = b.forward();
        Vec3d o = b.getPos().add(dir.multiply(START));
        double y = b.groundY(o.x, o.z);
        zone = show(b, new DangerShape.Strip(o.x, o.z, (float) dir.x, (float) dir.z, (float) LENGTH, (float) HALF_WIDTH), y, IMPACT, 14f, DangerZone.TONGUE);
        b.sound(ModSounds.WARNING, 2.0f, 0.9f);
    }

    @Override
    public void tick(QumaraEntity b, float prev, float now) {
        if (crossed(prev, now, 14f)) b.sound(ModSounds.ARM_MOVE, 2.0f, 1.2f);
        if (crossed(prev, now, IMPACT - 3f)) b.sound(ModSounds.TONGUE_LASH, 3.5f, 1.0f);
        if (crossed(prev, now, IMPACT)) {
            Vec3d mouth = b.getPos().add(dir.multiply(MOUTH_FWD)).add(0, Scale.MOUTH_Y, 0);
            Vec3d tip = b.getPos().add(dir.multiply(START + LENGTH)).add(0, 0.8, 0);
            Vfx.tongueTrail(b.sw(), mouth, tip);
            for (ServerPlayerEntity p : b.zones().playersIn(zone)) {
                b.hurtPlayer(p, 8f);
                pulled.add(p);
            }
            if (!pulled.isEmpty()) b.sound(ModSounds.IMPACT, 2.5f, 1.4f);
        }
        if (now > IMPACT && now <= PULL_END) {
            Vec3d anchor = b.getPos().add(dir.multiply(2.6 * Scale.K));
            for (ServerPlayerEntity p : pulled) {
                Vec3d d = anchor.subtract(p.getPos()).multiply(1, 0, 1);
                if (d.length() < 0.9) continue;
                Vec3d v = d.normalize().multiply(1.0);
                p.setVelocity(v.x, Math.max(p.getVelocity().y, 0.08), v.z); p.velocityModified = true;
            }
        }
    }

    @Override
    public BossAttack next(QumaraEntity b) {
        if (pulled.isEmpty()) return null;
        boolean any = false;
        for (ServerPlayerEntity p : pulled) if (p.isAlive() && p.getPos().distanceTo(b.getPos()) < 6.5 * Scale.K) any = true;
        return any ? new GrabAttack(pulled.get(0)) : null;
    }
}
