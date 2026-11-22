package dev.qumara.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public final class QumaraNetworking {
    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(DangerZonePayload.ID, DangerZonePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DangerZoneRemovePayload.ID, DangerZoneRemovePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShakePayload.ID, ShakePayload.CODEC);
    }

    public static void shake(ServerWorld world, Vec3d center, double radius, int ticks, float intensity, int lookAtId, int lookTicks) {
        ShakePayload p = new ShakePayload(ticks, intensity, lookAtId, lookTicks);
        for (ServerPlayerEntity pl : PlayerLookup.around(world, center, radius)) ServerPlayNetworking.send(pl, p);
    }

    private QumaraNetworking() {}
}
