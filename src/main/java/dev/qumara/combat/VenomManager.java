package dev.qumara.combat;

import dev.qumara.entity.QumaraEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * VENENO DE QUMARA. A diferencia del veneno vanilla (que nunca mata), este hace dano real cada segundo (ignora la armadura, como
 * todo el dano de Qumara) y se ACUMULA: cada contacto suma un nivel (maximo 3) y renueva la duracion. Se cura con leche (o cualquier
 * cosa que quite el efecto Veneno), con lo que el jugador tiene una forma de defenderse.
 */
public final class VenomManager {
    public static final int MAX_LEVEL = 3;
    public static final float DAMAGE_PER_LEVEL = 1.5f;      // por segundo y nivel (para 20 de vida; escala con los corazones como el resto)

    private static final class Venom { long until; int level; WeakReference<QumaraEntity> boss; WeakReference<ServerPlayerEntity> player; }
    private static final Map<UUID, Venom> ACTIVE = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ACTIVE.isEmpty()) return;
            for (Iterator<Map.Entry<UUID, Venom>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
                Venom v = it.next().getValue();
                ServerPlayerEntity p = v.player.get(); QumaraEntity b = v.boss.get();
                if (p == null || p.isRemoved() || !p.isAlive() || b == null || b.isRemoved() || !b.isAlive()
                        || p.getServerWorld().getTime() >= v.until || !p.hasStatusEffect(StatusEffects.POISON)) { it.remove(); continue; }
                if (p.age % 4 == 0) p.getServerWorld().spawnParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.3, 0.5, 0.3, 0.02);
                if (p.age % 20 == 0) b.hurtPlayer(p, DAMAGE_PER_LEVEL * v.level);
            }
        });
    }

    /** Suma 'addLevel' niveles (tope 3) y deja el veneno activo al menos 'ticks' ticks mas. */
    public static void apply(QumaraEntity boss, ServerPlayerEntity p, int ticks, int addLevel) {
        long now = p.getServerWorld().getTime();
        Venom v = ACTIVE.computeIfAbsent(p.getUuid(), k -> new Venom());
        if (v.until <= now) v.level = 0;
        v.level = Math.min(MAX_LEVEL, v.level + addLevel);
        v.until = Math.max(v.until, now + ticks);
        v.boss = new WeakReference<>(boss); v.player = new WeakReference<>(p);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, (int) (v.until - now), 0));
    }

    public static int level(ServerPlayerEntity p) { Venom v = ACTIVE.get(p.getUuid()); return v == null ? 0 : v.level; }

    private VenomManager() {}
}
