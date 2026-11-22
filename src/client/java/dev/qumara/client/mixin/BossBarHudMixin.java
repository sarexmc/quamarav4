package dev.qumara.client.mixin;

import dev.qumara.client.hud.BossBarHudAccess;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Oculta SOLO la barra vanilla de Qumara (la sustituye la barra con arte propio); el resto de barras de jefe se siguen
 * dibujando con normalidad. Las barras se retiran del mapa durante render() y se restauran al terminar.
 */
@Mixin(BossBarHud.class)
public abstract class BossBarHudMixin implements BossBarHudAccess {
    @Shadow @Final private Map<UUID, ClientBossBar> bossBars;
    @org.spongepowered.asm.mixin.Unique private final Map<UUID, ClientBossBar> qumara$stash = new java.util.LinkedHashMap<>();

    @Override
    public Map<UUID, ClientBossBar> qumara$bars() { return bossBars; }

    @Inject(method = "render", at = @At("HEAD"))
    private void qumara$hide(DrawContext context, CallbackInfo ci) {
        String name = Text.translatable("entity.qumara.qumara").getString();
        List<UUID> hide = new ArrayList<>();
        for (Map.Entry<UUID, ClientBossBar> e : bossBars.entrySet())
            if (e.getValue().getName().getString().equals(name)) hide.add(e.getKey());
        for (UUID id : hide) qumara$stash.put(id, bossBars.remove(id));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void qumara$restore(DrawContext context, CallbackInfo ci) {
        bossBars.putAll(qumara$stash);
        qumara$stash.clear();
    }
}
