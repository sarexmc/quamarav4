package dev.qumara.combat;

import dev.qumara.net.DangerZonePayload;
import dev.qumara.net.DangerZoneRemovePayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gestiona las zonas de peligro de UN jefe. El servidor es la unica fuente de verdad: crea la zona, la envia a todos los
 * jugadores cercanos (mismas posiciones y tiempos) y la usa para aplicar el dano. Se reenvia cada segundo para los que
 * entran en el radio a mitad de ataque.
 */
public class DangerZoneManager {
    private static final AtomicInteger IDS = new AtomicInteger(1);
    private static final double RADIUS = 128.0;
    private final ServerWorld world;
    private final List<DangerZone> active = new ArrayList<>();
    private Vec3d anchor = Vec3d.ZERO;

    public DangerZoneManager(ServerWorld world) { this.world = world; }

    public void setAnchor(Vec3d p) { this.anchor = p; }

    public DangerZone show(DangerShape shape, double y, int durationTicks, int lingerTicks, int style) {
        DangerZone z = new DangerZone(IDS.getAndIncrement(), shape, y, world.getTime(), durationTicks, lingerTicks, style);
        active.add(z);
        broadcast(z);
        return z;
    }

    /** Sustituye la forma de una zona (p. ej. la enredadera que sigue al jugador) conservando id y temporizacion. */
    public DangerZone update(DangerZone old, DangerShape shape, double y) {
        DangerZone z = new DangerZone(old.id(), shape, y, old.start(), old.duration(), old.linger(), old.style());
        int i = active.indexOf(old);
        if (i >= 0) active.set(i, z); else active.add(z);
        broadcast(z);
        return z;
    }

    public void remove(DangerZone z) {
        if (active.remove(z)) {
            for (ServerPlayerEntity p : PlayerLookup.around(world, anchor, RADIUS)) ServerPlayNetworking.send(p, new DangerZoneRemovePayload(z.id()));
        }
    }

    public void clear() {
        if (active.isEmpty()) return;
        active.clear();
        for (ServerPlayerEntity p : PlayerLookup.around(world, anchor, RADIUS)) ServerPlayNetworking.send(p, new DangerZoneRemovePayload(-1));
    }

    public void tick() {
        long now = world.getTime();
        for (Iterator<DangerZone> it = active.iterator(); it.hasNext(); ) if (now > it.next().endTime() + 12) it.remove();
        if (now % 40 == 0) for (DangerZone z : active) broadcast(z);
    }

    private void broadcast(DangerZone z) {
        int age = (int) (world.getTime() - z.start());
        DangerZonePayload p = new DangerZonePayload(z.id(), z.shape(), z.y(), age, z.duration(), z.linger(), z.style());
        for (ServerPlayerEntity pl : PlayerLookup.around(world, anchor, RADIUS)) ServerPlayNetworking.send(pl, p);
    }

    /** Jugadores (vivos, no espectadores) dentro de la zona. Misma geometria que ve el cliente. */
    public List<ServerPlayerEntity> playersIn(DangerZone z) {
        List<ServerPlayerEntity> res = new ArrayList<>();
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (z.contains(p.getPos())) res.add(p);
        }
        return res;
    }
}
