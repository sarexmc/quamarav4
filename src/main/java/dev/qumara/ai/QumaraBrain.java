package dev.qumara.ai;

import dev.qumara.combat.attack.AttackType;
import dev.qumara.entity.QumaraEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * IA del jefe (solo servidor): elige objetivo (cercania + dano reciente + algo de azar) y ataque (pesos por distancia,
 * cooldown por habilidad, penalizacion a repetir los ultimos ataques).
 */
public class QumaraBrain {
    private final QumaraEntity boss;
    private final Map<UUID, Float> aggro = new HashMap<>();
    private final EnumMap<AttackType, Long> readyAt = new EnumMap<>(AttackType.class);
    private final Deque<AttackType> history = new ArrayDeque<>();
    private final Map<UUID, Long> recent = new HashMap<>();
    private ServerPlayerEntity target;

    public QumaraBrain(QumaraEntity boss) { this.boss = boss; }

    public ServerPlayerEntity target() { return target; }

    public void registerDamage(ServerPlayerEntity p, float amount) { aggro.merge(p.getUuid(), amount, Float::sum); }

    public void tick() {
        long now = boss.sw().getTime();
        if (now % 20 == 0) aggro.replaceAll((k, v) -> v * 0.93f);
        if (target != null && (!target.isAlive() || target.isSpectator() || !boss.players().contains(target))) target = null;
        if (target == null || now % 40 == 0) chooseTarget();
    }

    private void chooseTarget() {
        List<ServerPlayerEntity> ps = boss.players();
        if (ps.isEmpty()) { target = null; return; }
        ServerPlayerEntity best = null; double bestScore = -1;
        for (ServerPlayerEntity p : ps) {
            double d = p.distanceTo(boss);
            double s = 20.0 / (d + 3) + Math.min(aggro.getOrDefault(p.getUuid(), 0f), 250f) * 0.05 + (p == target ? 2.0 : 0) + boss.rng().nextDouble() * 3.0
                    - (boss.sw().getTime() - recent.getOrDefault(p.getUuid(), -9999L) < 240 ? 4.5 : 0);     // no cebarse siempre con el mismo
            if (s > bestScore) { bestScore = s; best = p; }
        }
        target = best;
        if (best != null) recent.put(best.getUuid(), boss.sw().getTime());
    }

    /** @return ataque elegido o null si no hay ninguno utilizable (jugadores fuera de alcance). */
    public AttackType pick() {
        if (target == null) return null;
        long now = boss.sw().getTime();
        double dist = Math.hypot(target.getX() - boss.getX(), target.getZ() - boss.getZ());
        List<ServerPlayerEntity> all = boss.players();
        int players = all.size(), behind = 0;
        for (ServerPlayerEntity p : all) {                      // jugadores a la espalda del jefe: no los alcanzan los ataques frontales
            Vec3d rel = p.getPos().subtract(boss.getPos()).multiply(1, 0, 1);
            if (rel.lengthSquared() > 1e-4 && rel.normalize().dotProduct(boss.forward()) < -0.2) behind++;
        }
        double fb = players == 0 ? 0 : behind / (double) players;
        double total = 0; EnumMap<AttackType, Double> w = new EnumMap<>(AttackType.class);
        for (AttackType t : AttackType.values()) {
            if (readyAt.getOrDefault(t, 0L) > now) continue;
            double x = t.weight(dist, players, boss.phase());
            if (x <= 0) continue;
            if (t.area()) x *= 1.0 + 3.0 * fb;                   // mucha gente detras -> ataques de area
            Iterator<AttackType> it = history.descendingIterator();
            if (it.hasNext() && it.next() == t) x *= 0.15;          // no repetir el mismo ataque seguido
            else if (history.contains(t)) x *= 0.6;
            w.put(t, x); total += x;
        }
        if (total <= 0) return null;
        double r = boss.rng().nextDouble() * total;
        for (Map.Entry<AttackType, Double> e : w.entrySet()) { r -= e.getValue(); if (r <= 0) return e.getKey(); }
        return w.keySet().iterator().next();
    }

    public void used(AttackType t) {
        long now = boss.sw().getTime();
        readyAt.put(t, now + (long) (t.cooldownTicks * (boss.rage() ? 0.55 : (boss.fury() ? 0.7 : 1.0))));
        history.addLast(t);
        while (history.size() > 3) history.removeFirst();
    }
}
