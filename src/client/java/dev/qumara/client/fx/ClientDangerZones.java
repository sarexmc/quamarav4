package dev.qumara.client.fx;

import dev.qumara.combat.DangerZone;
import dev.qumara.net.DangerZonePayload;
import net.minecraft.client.MinecraftClient;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Zonas de peligro recibidas del servidor. El tiempo se mide con el reloj del cliente a partir de 'ageTicks'. */
public final class ClientDangerZones {
    public static final class Entry {
        public final DangerZone zone; public final long clientStart; public long removedAt = -1;
        Entry(DangerZone z, long clientStart) { this.zone = z; this.clientStart = clientStart; }
        public long clientImpact() { return clientStart + zone.duration(); }
        public long clientEnd() { return clientStart + zone.duration() + zone.linger(); }
    }

    public static final Map<Integer, Entry> ZONES = new ConcurrentHashMap<>();

    public static void put(DangerZonePayload p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        long now = mc.world.getTime();
        long start = now - p.ageTicks();
        DangerZone z = new DangerZone(p.id(), p.shape(), p.y(), start, p.duration(), p.linger(), p.style());
        Entry old = ZONES.get(p.id());
        ZONES.put(p.id(), new Entry(z, start));
        if (old != null && old.removedAt >= 0) ZONES.get(p.id()).removedAt = -1;
    }

    public static void remove(int id) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = mc.world == null ? 0 : mc.world.getTime();
        if (id < 0) { for (Entry e : ZONES.values()) if (e.removedAt < 0) e.removedAt = now; return; }
        Entry e = ZONES.get(id);
        if (e != null && e.removedAt < 0) e.removedAt = now;
    }

    public static void tick(MinecraftClient mc) {
        if (mc.world == null) { ZONES.clear(); return; }
        long now = mc.world.getTime();
        for (Iterator<Entry> it = ZONES.values().iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if ((e.removedAt >= 0 && now > e.removedAt + 8) || now > e.clientEnd() + 10) it.remove();
        }
    }

    private ClientDangerZones() {}
}
