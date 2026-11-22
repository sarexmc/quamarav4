package dev.qumara.combat.attack;

import dev.qumara.combat.DangerShape;
import dev.qumara.combat.Scale;
import dev.qumara.combat.DangerZone;
import dev.qumara.combat.RootedManager;
import dev.qumara.entity.QumaraEntity;
import dev.qumara.registry.ModSounds;
import dev.qumara.vfx.Vfx;
import dev.qumara.world.PlantManager;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * ATAQUE 6 - ENREDADERA CAZADORA. El circulo rojo sigue al objetivo durante el principio del aviso y se BLOQUEA en
 * el ultimo tramo; la enredadera (cadena de BlockDisplay) se lanza hacia el circulo, atrapa y se retrae.
 */
public class VineAttack extends BossAttack {
    public static final float IMPACT = 30f, LOCK = 18f, LAUNCH = 22f, RETRACT_END = 46f, TOTAL = 52f, RADIUS = 4.4f;
    private static final int PIECES = 12;

    private int left;                       // enredaderas que faltan por lanzar (varias si hay multitud)
    private LivingEntity target;
    private DangerZone zone;
    private Vec3d center;
    private Vec3d origin;
    private final List<DisplayEntity.BlockDisplayEntity> pieces = new ArrayList<>();

    public VineAttack() { this(-1); }
    private VineAttack(int left) { this.left = left; }

    @Override public String animation() { return "vine_cast"; }
    @Override public float total() { return TOTAL; }

    @Override
    public void start(QumaraEntity b, LivingEntity t) {
        List<ServerPlayerEntity> ps = b.players();
        if (left < 0) { left = 1 + Math.min(ps.size() / 8, 4) + (b.fury() ? 1 : 0); target = t; }
        else target = ps.isEmpty() ? t : ps.get(b.rng().nextInt(ps.size()));
        Vec3d p = target == null ? b.getPos().add(b.forward().multiply(8)) : target.getPos();
        center = new Vec3d(p.x, b.groundY(p.x, p.z), p.z);
        zone = show(b, new DangerShape.Circle(center.x, center.z, RADIUS), center.y, IMPACT, 10f, DangerZone.VINE);
        origin = b.getPos().add(b.forward().multiply(-1.4 * Scale.K)).add(b.right().multiply(-0.5 * Scale.K)).add(0, Scale.STALK_Y, 0);
        b.sound(ModSounds.WARNING, 2.0f, 0.9f);
        b.sound(ModSounds.VINE_WHIP, 2.5f, 0.6f);
    }

    @Override
    public void tick(QumaraEntity b, float prev, float now) {
        // seguimiento breve del objetivo (el circulo se desplaza suavemente) hasta LOCK
        if (now < LOCK && target != null && target.isAlive() && (int) now % 3 == 0) {
            Vec3d goal = new Vec3d(target.getX(), center.y, target.getZ());
            Vec3d d = goal.subtract(center);
            double step = Math.min(d.length(), 2.6);
            if (d.length() > 1e-3) center = center.add(d.normalize().multiply(step));
            center = new Vec3d(center.x, b.groundY(center.x, center.z), center.z);
            zone = b.zones().update(zone, new DangerShape.Circle(center.x, center.z, RADIUS), center.y);
            zs.clear(); zs.add(zone);
        }
        if (crossed(prev, now, LAUNCH)) {
            for (int i = 0; i < PIECES; i++) {
                DisplayEntity.BlockDisplayEntity e = b.plants().createFloating(i % 3 == 0 ? Blocks.MOSS_BLOCK.getDefaultState() : Blocks.CAVE_VINES_PLANT.getDefaultState(), origin, 1.0f - i * 0.035f);
                if (e != null) pieces.add(e);
            }
            b.sound(ModSounds.VINE_WHIP, 3.5f, 0.9f);
        }
        if (now >= LAUNCH && now <= RETRACT_END && !pieces.isEmpty()) {
            float p = now <= IMPACT ? (now - LAUNCH) / (IMPACT - LAUNCH)
                    : (now <= IMPACT + 4 ? 1f : 1f - (now - IMPACT - 4) / (RETRACT_END - IMPACT - 4));
            moveVine(b, MathHelper.clamp(p, 0f, 1f), now);
        }
        if (crossed(prev, now, IMPACT)) {
            Vfx.rootEruption(b.sw(), center);
            Vfx.burst(b.sw(), Vfx.GREEN_DUST, center.add(0, 0.8, 0), 40, 1.8, 0.05);
            for (ServerPlayerEntity p : b.zones().playersIn(zone)) {
                b.hurtPlayer(p, 10f);
                RootedManager.root(p, 40);
            }
            b.sound(ModSounds.IMPACT, 3.0f, 1.5f);
            b.shake(6, 0.2f);
        }
    }

    private void moveVine(QumaraEntity b, float p, float clock) {
        Vec3d end = center.add(0, 0.4, 0);
        Vec3d mid = origin.add(end).multiply(0.5).add(0, 4.8 * Scale.K / 1.5, 0);
        for (int i = 0; i < pieces.size(); i++) {
            float u = Math.max(0f, p - i * 0.065f);
            Vec3d a = origin.lerp(mid, u), c = mid.lerp(end, u);
            Vec3d pos = a.lerp(c, u);
            double wob = Math.sin(clock * 0.9 + i * 0.8) * 0.5 * (1 - Math.abs(u - 0.5) * 1.6);
            pos = pos.add(b.right().multiply(wob)).add(0, Math.cos(clock * 0.7 + i) * 0.15, 0);
            PlantManager.moveFloating(pieces.get(i), pos, 1.0f - i * 0.035f, 2);
            if (i == 0 && p > 0.02) Vfx.vineTrail(b.sw(), pos);
        }
    }

    @Override
    public void end(QumaraEntity b) {
        for (DisplayEntity.BlockDisplayEntity e : pieces) b.plants().discardFloating(e);
        pieces.clear();
        super.end(b);
    }

    @Override
    public BossAttack next(QumaraEntity b) { return left > 1 ? new VineAttack(left - 1) : null; }
}
