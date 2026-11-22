package dev.qumara.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.qumara.combat.DangerShape;
import dev.qumara.combat.DangerZone;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;

/**
 * Dibuja los indicadores rojos de peligro sobre el terreno. La geometria sale de DangerShape (la misma clase con la que
 * el servidor aplica el dano), de modo que lo que se ve es exactamente la zona que golpea.
 * Elementos: relleno pulsante, anillo de progreso que se cierra hasta el impacto, borde brillante animado y patron
 * propio por ataque (radios, chevrones, espirales...). Animaciones de aparicion y desaparicion.
 */
public final class DangerZoneRenderer {
    private static final float LIFT = 0.06f;

    public static void render(WorldRenderContext ctx) {
        if (ClientDangerZones.ZONES.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        float pt = mc.getRenderTickCounter().getTickDelta(false);
        double now = mc.world.getTime() + pt;
        TOP_CACHE.clear();
        Vec3d cam = ctx.camera().getPos();
        MatrixStack ms = ctx.matrixStack();
        ms.push();
        ms.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ms.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        boolean any = false;
        for (ClientDangerZones.Entry e : ClientDangerZones.ZONES.values()) {
            double age = now - e.clientStart;
            double appear = MathHelper.clamp(age / 5.0, 0, 1);                                       // aparicion
            double fade = 1.0;
            double endAt = e.removedAt >= 0 ? Math.min(e.removedAt, e.clientEnd()) : e.clientEnd();
            if (now > endAt) fade = MathHelper.clamp(1.0 - (now - endAt) / 6.0, 0, 1);               // desaparicion
            if (appear <= 0 || fade <= 0) continue;
            any = true;
            draw(bb, m, mc, e, age, appear, fade);
        }
        if (any) BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ms.pop();
    }

    // ------------------------------------------------------------------------------------------------------------
    private static void draw(BufferBuilder bb, Matrix4f m, MinecraftClient mc, ClientDangerZones.Entry e, double age, double appear, double fade) {
        DangerZone z = e.zone;
        double dur = Math.max(1, z.duration());
        double prog = MathHelper.clamp(age / dur, 0, 1);                                           // 0 -> impacto
        boolean impacted = age >= dur;
        boolean outlineOnly = z.style() == DangerZone.RING;
        // pulsacion: cada vez mas rapida al acercarse al impacto
        double pulse = 0.5 + 0.5 * Math.sin(age * (0.45 + prog * 1.6));
        float base = (float) (appear * fade);
        float fillA = outlineOnly ? 0.0f : (float) ((0.16 + 0.12 * pulse + 0.20 * prog) * base);
        float edgeA = (float) ((outlineOnly ? 0.55 : 0.85) * base);
        float r = 0.95f, g = (float) (0.05 + 0.10 * pulse + (impacted ? 0.25 : 0)), b = 0.05f;
        if (z.style() == DangerZone.OBSTACLE) { fillA *= 0.7f; g += 0.08f; }
        float grow = (float) (0.65 + 0.35 * easeOut(appear));                                      // el borde "se abre" al aparecer
        float edgeW = (float) (0.16 + 0.06 * pulse);

        switch (z.shape()) {
            case DangerShape.Circle c -> circle(bb, m, mc, z, c, g, b, r, fillA, edgeA, edgeW, grow, prog, age, base, outlineOnly);
            case DangerShape.Arc a -> arc(bb, m, mc, z, a, g, b, r, fillA, edgeA, edgeW, grow, prog, age, base);
            case DangerShape.Strip s -> strip(bb, m, mc, z, s, g, b, r, fillA, edgeA, edgeW, grow, prog, age, base);
        }
    }

    private static double easeOut(double x) { return 1 - (1 - x) * (1 - x); }

    private static final java.util.HashMap<Long, Integer> TOP_CACHE = new java.util.HashMap<>();      // cache por frame: con 40 zonas hay miles de consultas

    private static float gy(MinecraftClient mc, double x, double z, double ref) {
        int bx = MathHelper.floor(x), bz = MathHelper.floor(z);
        long key = ((long) bx << 32) ^ (bz & 0xffffffffL);
        Integer top = TOP_CACHE.get(key);
        if (top == null) { top = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz); TOP_CACHE.put(key, top); }
        return (float) (Math.abs(top - ref) > 3 ? ref : top) + LIFT;
    }

    private static void v(BufferBuilder bb, Matrix4f m, float x, float y, float zz, float r, float g, float b, float a) {
        bb.vertex(m, x, y, zz).color(r, g, b, a);
    }

    private static void tri(BufferBuilder bb, Matrix4f m, float[] p0, float[] p1, float[] p2, float r, float g, float b, float a) {
        v(bb, m, p0[0], p0[1], p0[2], r, g, b, a); v(bb, m, p1[0], p1[1], p1[2], r, g, b, a); v(bb, m, p2[0], p2[1], p2[2], r, g, b, a);
    }

    private static void quad(BufferBuilder bb, Matrix4f m, float[] a, float[] b2, float[] c, float[] d, float r, float g, float b, float al) {
        tri(bb, m, a, b2, c, r, g, b, al); tri(bb, m, a, c, d, r, g, b, al);
    }

    private static float[] pt(MinecraftClient mc, double x, double z, double refY) { return new float[]{(float) x, gy(mc, x, z, refY), (float) z}; }

    // ---------------------------------------------------------------------------------------------- circulos
    private static void circle(BufferBuilder bb, Matrix4f m, MinecraftClient mc, DangerZone z, DangerShape.Circle c, float g, float b, float r,
                               float fillA, float edgeA, float edgeW, float grow, double prog, double age, float base, boolean outlineOnly) {
        int N = 40; double R = c.radius() * grow;
        double refY = z.y();
        // relleno interior que crece hasta el borde (progreso exacto hacia el impacto)
        if (!outlineOnly) {
            double Rp = R * (0.12 + 0.88 * prog);
            float[] ctr = pt(mc, c.x(), c.z(), refY);
            for (int i = 0; i < N; i++) {
                double a0 = Math.PI * 2 * i / N, a1 = Math.PI * 2 * (i + 1) / N;
                tri(bb, m, ctr, pt(mc, c.x() + Math.cos(a0) * R, c.z() + Math.sin(a0) * R, refY), pt(mc, c.x() + Math.cos(a1) * R, c.z() + Math.sin(a1) * R, refY), r, g, b, fillA * 0.55f);
                tri(bb, m, ctr, pt(mc, c.x() + Math.cos(a0) * Rp, c.z() + Math.sin(a0) * Rp, refY), pt(mc, c.x() + Math.cos(a1) * Rp, c.z() + Math.sin(a1) * Rp, refY), r, g, b, fillA * 0.9f);
            }
        }
        ring(bb, m, mc, c.x(), c.z(), R - edgeW, R, refY, N, r, g, b, edgeA);                        // borde exterior
        if (!outlineOnly) {
            double Rp = R * (0.12 + 0.88 * prog);
            ring(bb, m, mc, c.x(), c.z(), Math.max(0.05, Rp - 0.1), Rp, refY, N, 1f, 0.35f, 0.25f, edgeA * 0.7f);   // anillo de progreso
            pattern(bb, m, mc, z, c, R, age, prog, base, r, g, b);
        }
    }

    private static void ring(BufferBuilder bb, Matrix4f m, MinecraftClient mc, double cx, double cz, double r0, double r1, double refY, int N, float r, float g, float b, float a) {
        for (int i = 0; i < N; i++) {
            double a0 = Math.PI * 2 * i / N, a1 = Math.PI * 2 * (i + 1) / N;
            quad(bb, m, pt(mc, cx + Math.cos(a0) * r0, cz + Math.sin(a0) * r0, refY), pt(mc, cx + Math.cos(a0) * r1, cz + Math.sin(a0) * r1, refY),
                    pt(mc, cx + Math.cos(a1) * r1, cz + Math.sin(a1) * r1, refY), pt(mc, cx + Math.cos(a1) * r0, cz + Math.sin(a1) * r0, refY), r, g, b, a);
        }
    }

    /** Patron interior propio de cada ataque (la identidad visual), siempre en rojo. */
    private static void pattern(BufferBuilder bb, Matrix4f m, MinecraftClient mc, DangerZone z, DangerShape.Circle c, double R, double age, double prog, float base, float r, float g, float b) {
        double rot = age * (0.04 + prog * 0.10);
        float a = (float) (0.55 * base);
        switch (z.style()) {
            case DangerZone.SLAM, DangerZone.GRAB -> {                       // grietas radiales que se abren
                int n = 8;
                for (int i = 0; i < n; i++) wedge(bb, m, mc, c, z.y(), Math.PI * 2 * i / n + 0.2, 0.05, R * (0.2 + 0.8 * prog), 0.07, r, g, b, a);
            }
            case DangerZone.ROOTS -> {                                        // raices retorcidas (espiral de tres brazos)
                for (int k = 0; k < 3; k++) spiral(bb, m, mc, c, z.y(), R, rot + k * Math.PI * 2 / 3, 0.9, 0.06, r, g, b, a);
            }
            case DangerZone.VINE -> {                                         // espiral doble: la enredadera que cierra
                for (int k = 0; k < 2; k++) spiral(bb, m, mc, c, z.y(), R, -rot * 1.4 + k * Math.PI, 1.6, 0.07, r, g, b, a);
            }
            case DangerZone.GARDEN, DangerZone.THORN -> {                     // petalos / espinas
                int n = z.style() == DangerZone.GARDEN ? 6 : 8;
                for (int i = 0; i < n; i++) wedge(bb, m, mc, c, z.y(), rot + Math.PI * 2 * i / n, 0.16, R * 0.9, 0.0, r, g, b, a * 0.8f);
            }
            default -> {
                for (int i = 0; i < 6; i++) wedge(bb, m, mc, c, z.y(), rot + Math.PI * 2 * i / 6, 0.06, R * 0.8, 0.05, r, g, b, a * 0.7f);
            }
        }
    }

    private static void wedge(BufferBuilder bb, Matrix4f m, MinecraftClient mc, DangerShape.Circle c, double refY, double ang, double halfAng, double len, double w, float r, float g, float b, float a) {
        double r0 = len * 0.12;
        double hw = w > 0 ? w : 0.0;
        double a0 = ang - halfAng, a1 = ang + halfAng;
        float[] p0 = pt(mc, c.x() + Math.cos(a0) * r0, c.z() + Math.sin(a0) * r0, refY);
        float[] p1 = pt(mc, c.x() + Math.cos(a1) * r0, c.z() + Math.sin(a1) * r0, refY);
        float[] p2 = pt(mc, c.x() + Math.cos(ang) * len + Math.cos(ang + 1.5708) * hw, c.z() + Math.sin(ang) * len + Math.sin(ang + 1.5708) * hw, refY);
        tri(bb, m, p0, p1, p2, r, g, b, a);
    }

    private static void spiral(BufferBuilder bb, Matrix4f m, MinecraftClient mc, DangerShape.Circle c, double refY, double R, double phase, double turns, double w, float r, float g, float b, float a) {
        int N = 26;
        for (int i = 0; i < N; i++) {
            double t0 = i / (double) N, t1 = (i + 1) / (double) N;
            double a0 = phase + t0 * turns * Math.PI * 2, a1 = phase + t1 * turns * Math.PI * 2;
            double r0 = R * 0.12 + R * 0.78 * t0, r1 = R * 0.12 + R * 0.78 * t1;
            quad(bb, m, pt(mc, c.x() + Math.cos(a0) * r0, c.z() + Math.sin(a0) * r0, refY), pt(mc, c.x() + Math.cos(a0) * (r0 + w), c.z() + Math.sin(a0) * (r0 + w), refY),
                    pt(mc, c.x() + Math.cos(a1) * (r1 + w), c.z() + Math.sin(a1) * (r1 + w), refY), pt(mc, c.x() + Math.cos(a1) * r1, c.z() + Math.sin(a1) * r1, refY), r, g, b, a);
        }
    }

    // ---------------------------------------------------------------------------------------------- sector (arrastre)
    private static void arc(BufferBuilder bb, Matrix4f m, MinecraftClient mc, DangerZone z, DangerShape.Arc A, float g, float b, float r,
                            float fillA, float edgeA, float edgeW, float grow, double prog, double age, float base) {
        int N = 36; double refY = z.y();
        double rIn = A.rIn(), rOut = A.rOut();
        double mid = (rIn + rOut) / 2, half = (rOut - rIn) / 2 * grow;
        rIn = mid - half; rOut = mid + half;
        for (int i = 0; i < N; i++) {
            double a0 = A.start() + A.sweep() * i / N, a1 = A.start() + A.sweep() * (i + 1) / N;
            float[] p0 = pt(mc, A.x() + Math.cos(a0) * rIn, A.z() + Math.sin(a0) * rIn, refY), p1 = pt(mc, A.x() + Math.cos(a0) * rOut, A.z() + Math.sin(a0) * rOut, refY);
            float[] p2 = pt(mc, A.x() + Math.cos(a1) * rOut, A.z() + Math.sin(a1) * rOut, refY), p3 = pt(mc, A.x() + Math.cos(a1) * rIn, A.z() + Math.sin(a1) * rIn, refY);
            // el relleno se enciende a lo largo de la direccion del barrido
            double frac = (i + 0.5) / N;
            float lit = (float) MathHelper.clamp((prog * 1.15) - frac * 0.5, 0.0, 1.0);
            quad(bb, m, p0, p1, p2, p3, r, g, b, fillA * (0.6f + 0.8f * lit));
            // bordes laterales (interior y exterior)
            quad(bb, m, p0, pt(mc, A.x() + Math.cos(a0) * (rIn + edgeW), A.z() + Math.sin(a0) * (rIn + edgeW), refY), pt(mc, A.x() + Math.cos(a1) * (rIn + edgeW), A.z() + Math.sin(a1) * (rIn + edgeW), refY), p3, r, g, b, edgeA);
            quad(bb, m, pt(mc, A.x() + Math.cos(a0) * (rOut - edgeW), A.z() + Math.sin(a0) * (rOut - edgeW), refY), p1, p2, pt(mc, A.x() + Math.cos(a1) * (rOut - edgeW), A.z() + Math.sin(a1) * (rOut - edgeW), refY), r, g, b, edgeA);
        }
        // remates del sector
        for (int k = 0; k < 2; k++) {
            double ang = A.start() + (k == 0 ? 0 : A.sweep());
            quad(bb, m, pt(mc, A.x() + Math.cos(ang) * rIn, A.z() + Math.sin(ang) * rIn, refY), pt(mc, A.x() + Math.cos(ang) * rOut, A.z() + Math.sin(ang) * rOut, refY),
                    pt(mc, A.x() + Math.cos(ang + 0.03 * Math.signum(A.sweep())) * rOut, A.z() + Math.sin(ang + 0.03 * Math.signum(A.sweep())) * rOut, refY),
                    pt(mc, A.x() + Math.cos(ang + 0.03 * Math.signum(A.sweep())) * rIn, A.z() + Math.sin(ang + 0.03 * Math.signum(A.sweep())) * rIn, refY), r, g, b, edgeA);
        }
        // chevrones que recorren la franja en el sentido del barrido (direccion y alcance)
        int chev = 6;
        for (int i = 0; i < chev; i++) {
            double f = ((age * 0.045 + i / (double) chev) % 1.0);
            double ang = A.start() + A.sweep() * f;
            double tx = -Math.sin(ang) * Math.signum(A.sweep()), tz = Math.cos(ang) * Math.signum(A.sweep());     // tangente en el sentido del barrido
            double cx = A.x() + Math.cos(ang) * mid, cz = A.z() + Math.sin(ang) * mid;
            double span = (rOut - rIn) * 0.42, len = 0.55;
            float[] tip = pt(mc, cx + tx * len, cz + tz * len, refY);
            float[] l = pt(mc, cx + Math.cos(ang) * span, cz + Math.sin(ang) * span, refY), rr = pt(mc, cx - Math.cos(ang) * span, cz - Math.sin(ang) * span, refY);
            float[] l2 = pt(mc, l[0] + (float) (tx * 0.18) - (float) (Math.cos(ang) * 0.22), l[2] + (float) (tz * 0.18) - (float) (Math.sin(ang) * 0.22), refY);
            float[] r2 = pt(mc, rr[0] + (float) (tx * 0.18) + (float) (Math.cos(ang) * 0.22), rr[2] + (float) (tz * 0.18) + (float) (Math.sin(ang) * 0.22), refY);
            float ca = (float) (0.85 * base);
            quad(bb, m, l, tip, pt(mc, tip[0] - (float) (tx * 0.2), tip[2] - (float) (tz * 0.2), refY), l2, 1f, 0.3f, 0.2f, ca);
            quad(bb, m, rr, tip, pt(mc, tip[0] - (float) (tx * 0.2), tip[2] - (float) (tz * 0.2), refY), r2, 1f, 0.3f, 0.2f, ca);
        }
    }

    // ---------------------------------------------------------------------------------------------- linea (lengua)
    private static void strip(BufferBuilder bb, Matrix4f m, MinecraftClient mc, DangerZone z, DangerShape.Strip S, float g, float b, float r,
                              float fillA, float edgeA, float edgeW, float grow, double prog, double age, float base) {
        double refY = z.y();
        double hw = S.halfWidth() * grow, L = S.length();
        double rx = -S.dz(), rz = S.dx();                              // perpendicular
        int seg = 14;
        for (int i = 0; i < seg; i++) {
            double a0 = L * i / seg, a1 = L * (i + 1) / seg;
            float[] p0 = pt(mc, S.x() + S.dx() * a0 + rx * hw, S.z() + S.dz() * a0 + rz * hw, refY), p1 = pt(mc, S.x() + S.dx() * a0 - rx * hw, S.z() + S.dz() * a0 - rz * hw, refY);
            float[] p2 = pt(mc, S.x() + S.dx() * a1 - rx * hw, S.z() + S.dz() * a1 - rz * hw, refY), p3 = pt(mc, S.x() + S.dx() * a1 + rx * hw, S.z() + S.dz() * a1 + rz * hw, refY);
            double lit = MathHelper.clamp(prog * 1.3 - (i / (double) seg) * 0.4, 0, 1);                // el relleno avanza hacia la punta
            quad(bb, m, p0, p1, p2, p3, r, g, b, fillA * (0.6f + 0.9f * (float) lit));
        }
        // bordes laterales
        for (int s = -1; s <= 1; s += 2) {
            double o0 = hw, o1 = hw - edgeW;
            quad(bb, m, pt(mc, S.x() + rx * o0 * s, S.z() + rz * o0 * s, refY), pt(mc, S.x() + rx * o1 * s, S.z() + rz * o1 * s, refY),
                    pt(mc, S.x() + S.dx() * L + rx * o1 * s, S.z() + S.dz() * L + rz * o1 * s, refY), pt(mc, S.x() + S.dx() * L + rx * o0 * s, S.z() + S.dz() * L + rz * o0 * s, refY), r, g, b, edgeA);
        }
        // remate de la punta
        quad(bb, m, pt(mc, S.x() + S.dx() * (L - edgeW) + rx * hw, S.z() + S.dz() * (L - edgeW) + rz * hw, refY), pt(mc, S.x() + S.dx() * (L - edgeW) - rx * hw, S.z() + S.dz() * (L - edgeW) - rz * hw, refY),
                pt(mc, S.x() + S.dx() * L - rx * hw, S.z() + S.dz() * L - rz * hw, refY), pt(mc, S.x() + S.dx() * L + rx * hw, S.z() + S.dz() * L + rz * hw, refY), r, g, b, edgeA);
        // chevrones corriendo hacia la punta
        int n = 5;
        for (int i = 0; i < n; i++) {
            double f = ((age * (0.05 + prog * 0.06) + i / (double) n) % 1.0) * L;
            double cx = S.x() + S.dx() * f, cz = S.z() + S.dz() * f;
            float[] tip = pt(mc, cx + S.dx() * 0.6, cz + S.dz() * 0.6, refY);
            float[] l = pt(mc, cx + rx * hw * 0.75, cz + rz * hw * 0.75, refY), rr = pt(mc, cx - rx * hw * 0.75, cz - rz * hw * 0.75, refY);
            float[] l2 = pt(mc, cx + rx * hw * 0.75 - S.dx() * 0.25, cz + rz * hw * 0.75 - S.dz() * 0.25, refY), r2 = pt(mc, cx - rx * hw * 0.75 - S.dx() * 0.25, cz - rz * hw * 0.75 - S.dz() * 0.25, refY);
            float ca = (float) (0.85 * base);
            tri(bb, m, l, tip, l2, 1f, 0.3f, 0.2f, ca);
            tri(bb, m, rr, tip, r2, 1f, 0.3f, 0.2f, ca);
        }
    }

    private DangerZoneRenderer() {}
}
