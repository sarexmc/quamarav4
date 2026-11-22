package dev.qumara.client;

import dev.qumara.client.fx.CameraFx;
import dev.qumara.client.fx.ClientDangerZones;
import dev.qumara.client.fx.DangerZoneRenderer;
import dev.qumara.client.hud.QumaraBossBarHud;
import dev.qumara.client.render.QumaraRenderer;
import dev.qumara.net.DangerZonePayload;
import dev.qumara.net.DangerZoneRemovePayload;
import dev.qumara.net.ShakePayload;
import dev.qumara.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public class QumaraClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.QUMARA, QumaraRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(DangerZonePayload.ID, (payload, ctx) ->
                ctx.client().execute(() -> ClientDangerZones.put(payload)));
        ClientPlayNetworking.registerGlobalReceiver(DangerZoneRemovePayload.ID, (payload, ctx) ->
                ctx.client().execute(() -> ClientDangerZones.remove(payload.id())));
        ClientPlayNetworking.registerGlobalReceiver(ShakePayload.ID, (payload, ctx) ->
                ctx.client().execute(() -> CameraFx.start(payload)));

        WorldRenderEvents.AFTER_TRANSLUCENT.register(DangerZoneRenderer::render);
        HudRenderCallback.EVENT.register(QumaraBossBarHud::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CameraFx.tick(client);
            ClientDangerZones.tick(client);
        });
    }
}
