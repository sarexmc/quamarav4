package dev.qumara.combat.attack;

import dev.qumara.combat.DangerShape;
import dev.qumara.combat.DangerZone;
import dev.qumara.combat.Scale;
import dev.qumara.entity.QumaraEntity;
import dev.qumara.registry.ModSounds;
import dev.qumara.vfx.Vfx;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** ABANICO DE LENGUA: tres latigazos seguidos (derecha, izquierda, centro); cada uno con su propia linea roja. */
public class TongueFanAttack extends BossAttack {
    public static final float[] HIT = {30f, 54f, 78f};
    public static final float[] SHOW = {0f, 30f, 54f};
    public static final double[] YAW_RIGHT_DEG = {22, -22, 0};      // positivo = hacia la derecha del jefe
    public static final float TOTAL = 100f;

    private final DangerZone[] zones = new DangerZone[3];
    private final Vec3d[] dirs = new Vec3d[3];
    private final List<ServerPlayerEntity> pulled = new ArrayList<>();
    private float pullUntil = -1f;
    private Vec3d pullDir;

    @Override public String animation() { return "idle"; }
    @Override public String tongueAnimation() { return "lengua_abanico"; }
    @Override public float total() { return TOTAL; }

    @Override public void start(QumaraEntity b, LivingEntity target) { show(b, 0); }

    private void show(QumaraEntity b, int i) {
        Vec3d dir = aim(b, YAW_RIGHT_DEG[i]);
        dirs[i] = dir;
        Vec3d o = b.getPos().add(dir.multiply(TongueAttack.START));
        double y = b.groundY(o.x, o.z);
        zones[i] = show(b, new DangerShape.Strip(o.x, o.z, (float) dir.x, (float) dir.z, (float) TongueAttack.LENGTH, (float) TongueAttack.HALF_WIDTH), y, HIT[i] - SHOW[i], 12f, DangerZone.TONGUE);
        b.sound(ModSounds.WARNING, 2.5f, 0.9f + 0.15f * i);
    }

    @Override
    public void tick(QumaraEntity b, float prev, float now) {
        for (int i = 1; i < 3; i++) if (crossed(prev, now, SHOW[i])) show(b, i);
        for (int i = 0; i < 3; i++) {
            if (crossed(prev, now, HIT[i] - 4f)) b.sound(ModSounds.TONGUE_LASH, 3.5f, 0.9f + 0.1f * i);
            if (crossed(prev, now, HIT[i])) {
                Vec3d mouth = b.getPos().add(dirs[i].multiply(TongueAttack.MOUTH_FWD)).add(0, Scale.MOUTH_Y, 0);
                Vec3d tip = b.getPos().add(dirs[i].multiply(TongueAttack.START + TongueAttack.LENGTH)).add(0, 0.8, 0);
                Vfx.tongueTrail(b.sw(), mouth, tip);
                for (ServerPlayerEntity p : b.zones().playersIn(zones[i])) {
                    b.hurtPlayer(p, 9f);
                    if (!pulled.contains(p)) pulled.add(p);
                    pullUntil = now + 8f;
                }
                b.sound(ModSounds.IMPACT, 2.5f, 1.5f);
                b.shake(5, 0.2f);
            }
        }
        if (now < pullUntil) {
            Vec3d anchor = b.getPos().add(b.forward().multiply(3.0 * Scale.K));
            for (ServerPlayerEntity p : pulled) {
                Vec3d d = anchor.subtract(p.getPos()).multiply(1, 0, 1);
                if (d.length() < 1.0) continue;
                Vec3d v = d.normalize().multiply(0.75);
                p.setVelocity(v.x, Math.max(p.getVelocity().y, 0.05), v.z); p.velocityModified = true;
            }
        } else if (!pulled.isEmpty()) pulled.clear();
    }
}
