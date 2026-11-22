package dev.qumara.world;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Plantas, raices y enredaderas visuales usando entidades vanilla BlockDisplay (el cliente interpola la escala:
 * animacion de crecimiento sin necesitar renderers extra). Todo el estado vive en el servidor.
 */
public class PlantManager {
    /** Un "brote": un BlockDisplay que crece, vive y se retrae. */
    private static final class Sprout {
        DisplayEntity.BlockDisplayEntity ent; Vec3d base; float w, h; float tiltX, tiltZ; float yawDeg;
        long born, growAt; int grow, life, retract; boolean growStarted, retractStarted;
    }

    private static final int MAX_ACTIVE = 150;     // tope de brotes simultaneos (rendimiento con muchos jugadores)
    private final ServerWorld world;
    private final List<Sprout> sprouts = new ArrayList<>();
    private final List<DisplayEntity.BlockDisplayEntity> free = new ArrayList<>();

    public PlantManager(ServerWorld world) { this.world = world; }

    /**
     * Crea un brote. 'delay' = ticks hasta que empieza a crecer, 'grow' = duracion del crecimiento, 'life' = ticks
     * totales que permanece una vez crecido, 'retract' = duracion de la retraccion.
     */
    public void sprout(BlockState state, Vec3d base, float w, float h, float tiltXdeg, float tiltZdeg, float yawDeg, int delay, int grow, int life, int retract) {
        if (sprouts.size() >= MAX_ACTIVE) return;
        DisplayEntity.BlockDisplayEntity e = EntityType.BLOCK_DISPLAY.create(world);
        if (e == null) return;
        e.refreshPositionAndAngles(base.x, base.y, base.z, 0, 0);
        Sprout s = new Sprout();
        s.ent = e; s.base = base; s.w = w; s.h = h; s.tiltX = tiltXdeg; s.tiltZ = tiltZdeg; s.yawDeg = yawDeg;
        s.born = world.getTime(); s.growAt = s.born + Math.max(2, delay); s.grow = grow; s.life = life; s.retract = retract;
        NbtCompound n = e.writeNbt(new NbtCompound());
        n.put("block_state", NbtHelper.fromBlockState(state));
        n.put("transformation", transform(s, 0.12f, 0.02f));
        n.putInt("interpolation_duration", 0);
        n.putFloat("view_range", 2.5f);
        e.readNbt(n);
        world.spawnEntity(e);
        sprouts.add(s);
    }

    public void tick() {
        long now = world.getTime();
        for (Iterator<Sprout> it = sprouts.iterator(); it.hasNext(); ) {
            Sprout s = it.next();
            if (s.ent.isRemoved()) { it.remove(); continue; }
            if (!s.growStarted && now >= s.growAt) { s.growStarted = true; apply(s, 1f, 1f, s.grow); }
            long retractAt = s.growAt + s.life;
            if (!s.retractStarted && now >= retractAt) { s.retractStarted = true; apply(s, 0.1f, 0.01f, s.retract); }
            if (now >= retractAt + s.retract + 2) { s.ent.discard(); it.remove(); }
        }
    }

    private void apply(Sprout s, float fw, float fh, int interp) {
        NbtCompound n = s.ent.writeNbt(new NbtCompound());
        n.put("transformation", transform(s, fw, fh));
        n.putInt("start_interpolation", 0);
        n.putInt("interpolation_duration", Math.max(1, interp));
        s.ent.readNbt(n);
    }

    /** Transformacion centrada en la base: escala (w*fw, h*fh, w*fw) y rotacion de inclinacion. */
    private NbtCompound transform(Sprout s, float fw, float fh) {
        float sx = s.w * fw, sy = s.h * fh;
        // quaternion = yaw(Y) * tiltX * tiltZ
        double ry = Math.toRadians(s.yawDeg) / 2, rx = Math.toRadians(s.tiltX) / 2, rz = Math.toRadians(s.tiltZ) / 2;
        double[] qy = {0, Math.sin(ry), 0, Math.cos(ry)}, qx = {Math.sin(rx), 0, 0, Math.cos(rx)}, qz = {0, 0, Math.sin(rz), Math.cos(rz)};
        double[] q = mul(qy, mul(qx, qz));
        // centro de la base del bloque (0.5,0,0.5) escalado -> se compensa para que crezca desde el suelo
        float tx = -sx / 2f, tz = -sx / 2f;
        NbtCompound t = new NbtCompound();
        t.put("translation", floats(tx, 0f, tz));
        t.put("scale", floats(sx, sy, sx));
        t.put("left_rotation", floats((float) q[0], (float) q[1], (float) q[2], (float) q[3]));
        t.put("right_rotation", floats(0f, 0f, 0f, 1f));
        return t;
    }

    private static double[] mul(double[] a, double[] b) {
        return new double[]{
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }

    private static NbtList floats(float... v) {
        NbtList l = new NbtList();
        for (float f : v) l.add(NbtFloat.of(f));
        return l;
    }

    /** Vuelve a mover una "pieza" de enredadera: BlockDisplay persistente con traslacion interpolada. */
    public DisplayEntity.BlockDisplayEntity createFloating(BlockState state, Vec3d at, float size) {
        DisplayEntity.BlockDisplayEntity e = EntityType.BLOCK_DISPLAY.create(world);
        if (e == null) return null;
        e.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        NbtCompound n = e.writeNbt(new NbtCompound());
        n.put("block_state", NbtHelper.fromBlockState(state));
        NbtCompound t = new NbtCompound();
        t.put("translation", floats(-size / 2f, -size / 2f, -size / 2f));
        t.put("scale", floats(0.01f, 0.01f, 0.01f));
        t.put("left_rotation", floats(0, 0, 0, 1)); t.put("right_rotation", floats(0, 0, 0, 1));
        n.put("transformation", t);
        n.putFloat("view_range", 2.5f);
        e.readNbt(n);
        world.spawnEntity(e);
        free.add(e);
        return e;
    }

    /** Coloca una pieza flotante en 'worldPos' (relativa a su ancla) con escala 'size', interpolando 'interp' ticks. */
    public static void moveFloating(DisplayEntity.BlockDisplayEntity e, Vec3d worldPos, float size, int interp) {
        Vec3d a = e.getPos();
        NbtCompound n = e.writeNbt(new NbtCompound());
        NbtCompound t = new NbtCompound();
        t.put("translation", floats((float) (worldPos.x - a.x) - size / 2f, (float) (worldPos.y - a.y) - size / 2f, (float) (worldPos.z - a.z) - size / 2f));
        t.put("scale", floats(size, size, size));
        t.put("left_rotation", floats(0, 0, 0, 1)); t.put("right_rotation", floats(0, 0, 0, 1));
        n.put("transformation", t);
        n.putInt("start_interpolation", 0);
        n.putInt("interpolation_duration", interp);
        e.readNbt(n);
    }

    public void clearAll() {
        for (Sprout s : sprouts) if (!s.ent.isRemoved()) s.ent.discard();
        sprouts.clear();
        for (DisplayEntity.BlockDisplayEntity e : free) if (!e.isRemoved()) e.discard();
        free.clear();
    }

    public void discardFloating(DisplayEntity.BlockDisplayEntity e) {
        if (e != null && !e.isRemoved()) e.discard();
        free.remove(e);
    }
}
