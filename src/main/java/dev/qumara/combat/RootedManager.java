package dev.qumara.combat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import dev.qumara.QumaraMod;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Inmoviliza jugadores (raices / enredaderas / plantas) con modificadores de atributo temporales. */
public final class RootedManager {
    private static final Identifier MOD_SPEED = QumaraMod.id("rooted_speed");
    private static final Identifier MOD_JUMP = QumaraMod.id("rooted_jump");
    private static final Map<UUID, Long> EXPIRY = new HashMap<>();
    private static final Map<UUID, ServerPlayerEntity> PLAYERS = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (EXPIRY.isEmpty()) return;
            for (Iterator<Map.Entry<UUID, Long>> it = EXPIRY.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Long> e = it.next();
                ServerPlayerEntity p = PLAYERS.get(e.getKey());
                if (p == null || p.isRemoved() || !p.isAlive() || p.getServerWorld().getTime() >= e.getValue()) {
                    if (p != null) release(p);
                    PLAYERS.remove(e.getKey());
                    it.remove();
                } else if (p.age % 4 == 0) {
                    ServerWorld w = p.getServerWorld();
                    w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.MANGROVE_ROOTS.getDefaultState()),
                            p.getX(), p.getY() + 0.1, p.getZ(), 4, 0.4, 0.1, 0.4, 0.02);
                }
            }
        });
    }

    public static void root(ServerPlayerEntity p, int ticks) {
        long until = p.getServerWorld().getTime() + ticks;
        EXPIRY.merge(p.getUuid(), until, Math::max);
        PLAYERS.put(p.getUuid(), p);
        apply(p);
    }

    public static boolean isRooted(ServerPlayerEntity p) { return EXPIRY.containsKey(p.getUuid()); }

    private static void apply(ServerPlayerEntity p) {
        EntityAttributeInstance speed = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(MOD_SPEED))
            speed.addTemporaryModifier(new EntityAttributeModifier(MOD_SPEED, -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        EntityAttributeInstance jump = p.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH);
        if (jump != null && !jump.hasModifier(MOD_JUMP))
            jump.addTemporaryModifier(new EntityAttributeModifier(MOD_JUMP, -1.0, EntityAttributeModifier.Operation.ADD_VALUE));
    }

    public static void release(ServerPlayerEntity p) {
        EntityAttributeInstance speed = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(MOD_SPEED);
        EntityAttributeInstance jump = p.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH);
        if (jump != null) jump.removeModifier(MOD_JUMP);
    }

    private RootedManager() {}
}
