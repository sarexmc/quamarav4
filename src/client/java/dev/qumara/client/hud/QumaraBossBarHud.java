package dev.qumara.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.qumara.QumaraMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * BossBar personalizada de Qumara con el arte del usuario (flor + marco de enredaderas). La vida viene de la BossBar
 * vanilla sincronizada por el servidor (ServerBossBar), asi que todos los jugadores del encuentro la ven igual.
 */
public final class QumaraBossBarHud {
    private static final Identifier OVERLAY = QumaraMod.id("textures/gui/bossbar_overlay.png");
    private static final Identifier FILL = QumaraMod.id("textures/gui/bossbar_fill.png");
    // dimensiones reales de las texturas (ver tools/out/bossbar_info.txt)
    private static final int TW = 724, TH = 192, FILL_X0 = 181, FILL_X1 = 707;
    private static final int DRAW_W = 340;
    private static float shown = 1f;
    private static float flash = 0f;

    public static ClientBossBar find(MinecraftClient mc) {
        String name = Text.translatable("entity.qumara.qumara").getString();
        for (ClientBossBar b : ((BossBarHudAccess) mc.inGameHud.getBossBarHud()).qumara$bars().values())
            if (b.getName().getString().equals(name)) return b;
        return null;
    }

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.world == null) return;
        ClientBossBar bar = find(mc);
        if (bar == null) { shown = 1f; return; }
        float target = bar.getPercent();
        if (target < shown) flash = 1f;
        shown = MathHelper.lerp(0.12f, shown, target);                                 // el relleno baja suavemente
        flash = Math.max(0, flash - 0.06f);

        float s = DRAW_W / (float) TW;
        int w = DRAW_W, h = Math.round(TH * s);
        int x = (ctx.getScaledWindowWidth() - w) / 2 + 0, y = 6;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // 1) fondo vacio: el mismo relleno oscurecido
        ctx.setShaderColor(0.22f, 0.05f, 0.13f, 1f);
        ctx.drawTexture(FILL, x, y, w, h, 0, 0, TW, TH, TW, TH);
        // 2) relleno hasta el porcentaje de vida
        int cut = Math.round(FILL_X0 + (FILL_X1 - FILL_X0 + 1) * shown);
        float tint = bar.getColor() == net.minecraft.entity.boss.BossBar.Color.RED ? 1f : 0f;
        ctx.setShaderColor(1f, 1f - 0.35f * tint, 1f - 0.45f * tint, 1f);
        if (cut > 0) ctx.drawTexture(FILL, x, y, Math.round(cut * s), h, 0, 0, cut, TH, TW, TH);
        // parpadeo al recibir dano: franja entre la vida mostrada y la real
        if (flash > 0.01f) {
            int cutReal = Math.round(FILL_X0 + (FILL_X1 - FILL_X0 + 1) * target);
            ctx.setShaderColor(1f, 0.9f, 0.9f, flash * 0.9f);
            ctx.drawTexture(FILL, x + Math.round(cutReal * s), y, Math.max(1, Math.round((cut - cutReal) * s)), h, cutReal, 0, Math.max(1, cut - cutReal), TH, TW, TH);
        }
        // 3) marco, enredaderas e icono
        ctx.setShaderColor(1f, 1f, 1f, 1f);
        ctx.drawTexture(OVERLAY, x, y, w, h, 0, 0, TW, TH, TW, TH);
        // nombre
        Text name = Text.translatable("entity.qumara.qumara");
        ctx.drawCenteredTextWithShadow(mc.textRenderer, name, x + Math.round((FILL_X0 + FILL_X1) / 2f * s), y + h - 14, 0xFFF3C4E0);
        RenderSystem.disableBlend();
    }

    private QumaraBossBarHud() {}
}
