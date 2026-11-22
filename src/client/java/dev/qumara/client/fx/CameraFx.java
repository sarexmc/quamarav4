package dev.qumara.client.fx;

import dev.qumara.net.ShakePayload;
import dev.qumara.combat.Scale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/**
 * Temblor de camara (impactos, cinematica) y giro suave de la mirada hacia Qumara durante la aparicion.
 * Es un efecto local; no altera la posicion ni la logica de combate (servidor autoritativo).
 */
public final class CameraFx {
    private static final Random RNG = new Random();
    private static int shakeLeft, shakeTotal, lookLeft, lookTotal, lookId = -1;
    private static float intensity;

    public static void start(ShakePayload p) {
        if (p.durationTicks() > 0 && p.intensity() >= intensity * (shakeLeft / (float) Math.max(1, shakeTotal))) {
            shakeLeft = shakeTotal = p.durationTicks(); intensity = p.intensity();
        }
        if (p.lookAtEntityId() >= 0 && p.lookTicks() > 0) { lookId = p.lookAtEntityId(); lookLeft = lookTotal = p.lookTicks(); }
    }

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity pl = mc.player;
        if (pl == null || mc.world == null) { shakeLeft = lookLeft = 0; return; }
        if (lookLeft > 0) {
            lookLeft--;
            Entity e = mc.world.getEntityById(lookId);
            if (e != null) {
                double dx = e.getX() - pl.getX(), dz = e.getZ() - pl.getZ();
                double dy = (e.getY() + Scale.HEAD_Y) - pl.getEyeY();
                float yaw = (float) (Math.atan2(dz, dx) * 57.29577951308232) - 90f;
                float pitch = (float) -(Math.atan2(dy, Math.hypot(dx, dz)) * 57.29577951308232);
                float fadeIn = Math.min(1f, (lookTotal - lookLeft) / 20f), fadeOut = Math.min(1f, lookLeft / 15f);
                float k = 0.07f * Math.min(fadeIn, fadeOut);
                pl.setYaw(MathHelper.lerpAngleDegrees(k, pl.getYaw(), yaw));
                pl.setPitch(MathHelper.lerp(k, pl.getPitch(), MathHelper.clamp(pitch, -35f, 35f)));
            }
        }
        if (shakeLeft > 0) {
            float f = shakeLeft / (float) shakeTotal;
            float a = intensity * f * 1.6f;
            pl.setYaw(pl.getYaw() + (RNG.nextFloat() - 0.5f) * a);
            pl.setPitch(MathHelper.clamp(pl.getPitch() + (RNG.nextFloat() - 0.5f) * a, -90f, 90f));
            if (--shakeLeft == 0) intensity = 0;
        }
    }

    private CameraFx() {}
}
