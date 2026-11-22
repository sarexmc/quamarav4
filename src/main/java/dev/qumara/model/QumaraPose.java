package dev.qumara.model;

import java.util.Arrays;

/**
 * Estado de animacion de UNA instancia de Qumara (cliente): capa principal (cuerpo + brazo), capa de lengua y capa aditiva de
 * golpe recibido, con fundidos entre animaciones. Produce las matrices de hueso (3x4 por hueso) para el skinning.
 */
public final class QumaraPose {
    private static final class Layer {
        String name = null; int seq = -1; float start; float[] fromRot, fromPos; float[] lastRot, lastPos; float fade;
    }
    private final QumaraRig rig; private final int nb;
    private final boolean[] mainMask, tongueMask, allMask;
    private final Layer main = new Layer(), tongue = new Layer();
    private final float[] rot, pos, tmpRot, tmpPos, hRot, hPos;
    private int hurtSeq = Integer.MIN_VALUE; private float hurtStart = -100f;
    /** nb * 12: R (3x3 por filas) + t(3) */
    public final float[] mats;

    public QumaraPose() {
        rig = QumaraRig.get(); nb = rig.nb;
        mainMask = new boolean[nb]; tongueMask = new boolean[nb]; allMask = new boolean[nb];
        for (int i = 0; i < nb; i++) { boolean t = rig.names[i].startsWith("lengua"); tongueMask[i] = t; mainMask[i] = !t; allMask[i] = true; }
        rot = new float[nb * 4]; pos = new float[nb * 3]; tmpRot = new float[nb * 4]; tmpPos = new float[nb * 3]; hRot = new float[nb * 4]; hPos = new float[nb * 3];
        main.lastRot = new float[nb * 4]; main.lastPos = new float[nb * 3]; tongue.lastRot = new float[nb * 4]; tongue.lastPos = new float[nb * 3];
        mats = new float[nb * 12];
    }

    private static void identity(float[] r, float[] p) { Arrays.fill(p, 0f); for (int i = 0; i < r.length; i += 4) { r[i] = 0; r[i + 1] = 0; r[i + 2] = 0; r[i + 3] = 1; } }

    private void layer(Layer L, String name, int seq, float now, float speed, float fadeLen, boolean[] mask) {
        QumaraRig.Anim a = rig.anims.get(name);
        if (a == null) a = rig.anims.get("idle");
        if (L.seq != seq || L.name == null || !L.name.equals(name)) {
            boolean first = L.name == null;
            L.fromRot = L.lastRot.clone(); L.fromPos = L.lastPos.clone(); L.fade = first ? 0f : fadeLen; L.start = now; L.name = name; L.seq = seq;
        }
        identity(tmpRot, tmpPos);
        rig.sample(a, (now - L.start) * speed, tmpRot, tmpPos, mask);
        float k = L.fade <= 0 ? 1f : Math.min(1f, (now - L.start) / L.fade); k = k * k * (3 - 2 * k);
        if (k < 1f) {
            for (int b = 0; b < nb; b++) {
                if (!mask[b]) continue;
                float dx = 0; for (int j = 0; j < 4; j++) dx += L.fromRot[b * 4 + j] * tmpRot[b * 4 + j];
                float sg = dx < 0 ? -1f : 1f, n = 0;
                for (int j = 0; j < 4; j++) { float v = L.fromRot[b * 4 + j] * sg * (1 - k) + tmpRot[b * 4 + j] * k; tmpRot[b * 4 + j] = v; n += v * v; }
                n = (float) Math.sqrt(n); for (int j = 0; j < 4; j++) tmpRot[b * 4 + j] /= n;
                for (int j = 0; j < 3; j++) tmpPos[b * 3 + j] = L.fromPos[b * 3 + j] * (1 - k) + tmpPos[b * 3 + j] * k;
            }
        }
        System.arraycopy(tmpRot, 0, L.lastRot, 0, tmpRot.length); System.arraycopy(tmpPos, 0, L.lastPos, 0, tmpPos.length);
        for (int b = 0; b < nb; b++) {
            if (!mask[b]) continue;
            System.arraycopy(tmpRot, b * 4, rot, b * 4, 4); System.arraycopy(tmpPos, b * 3, pos, b * 3, 3);
        }
    }

    /** @param now tiempo en segundos (monotono) */
    public void update(String mainName, int mainSeq, float mainSpeed, String tongueName, int tongueSeq, float tongueSpeed, int hurtCounter, float now) {
        identity(rot, pos);
        boolean hold = mainName.equals("spawn") || mainName.equals("death");
        layer(main, mainName, mainSeq, now, mainSpeed, hold ? 0.05f : 0.25f, mainMask);
        layer(tongue, tongueName, tongueSeq, now, tongueSpeed, 0.18f, tongueMask);
        if (hurtSeq == Integer.MIN_VALUE) hurtSeq = hurtCounter;
        if (hurtCounter != hurtSeq) { hurtSeq = hurtCounter; hurtStart = now; }
        QumaraRig.Anim h = rig.anims.get("hurt");
        boolean hurt = h != null && now - hurtStart < h.length;
        if (hurt) { identity(hRot, hPos); rig.sample(h, now - hurtStart, hRot, hPos, mainMask); }
        // matrices de hueso
        for (int b = 0; b < nb; b++) {
            float x = rot[b * 4], y = rot[b * 4 + 1], z = rot[b * 4 + 2], w = rot[b * 4 + 3];
            float px = pos[b * 3], py = pos[b * 3 + 1], pz = pos[b * 3 + 2];
            if (hurt && mainMask[b]) {
                float hx = hRot[b * 4], hy = hRot[b * 4 + 1], hz = hRot[b * 4 + 2], hw = hRot[b * 4 + 3];
                float nx = hw * x + hx * w + hy * z - hz * y, ny = hw * y - hx * z + hy * w + hz * x, nz = hw * z + hx * y - hy * x + hz * w, nw = hw * w - hx * x - hy * y - hz * z;
                x = nx; y = ny; z = nz; w = nw; px += hPos[b * 3]; py += hPos[b * 3 + 1]; pz += hPos[b * 3 + 2];
            }
            float r00 = 1 - 2 * (y * y + z * z), r01 = 2 * (x * y - z * w), r02 = 2 * (x * z + y * w);
            float r10 = 2 * (x * y + z * w), r11 = 1 - 2 * (x * x + z * z), r12 = 2 * (y * z - x * w);
            float r20 = 2 * (x * z - y * w), r21 = 2 * (y * z + x * w), r22 = 1 - 2 * (x * x + y * y);
            float vx = rig.pivot[b * 3], vy = rig.pivot[b * 3 + 1], vz = rig.pivot[b * 3 + 2];
            float tx = vx + px - (r00 * vx + r01 * vy + r02 * vz), ty = vy + py - (r10 * vx + r11 * vy + r12 * vz), tz = vz + pz - (r20 * vx + r21 * vy + r22 * vz);
            int o = b * 12, p = rig.parent[b];
            if (p < 0) {
                set(o, r00, r01, r02, r10, r11, r12, r20, r21, r22, tx, ty, tz);
            } else {
                int q = p * 12;
                float a00 = mats[q], a01 = mats[q + 1], a02 = mats[q + 2], a10 = mats[q + 3], a11 = mats[q + 4], a12 = mats[q + 5], a20 = mats[q + 6], a21 = mats[q + 7], a22 = mats[q + 8];
                set(o, a00 * r00 + a01 * r10 + a02 * r20, a00 * r01 + a01 * r11 + a02 * r21, a00 * r02 + a01 * r12 + a02 * r22,
                        a10 * r00 + a11 * r10 + a12 * r20, a10 * r01 + a11 * r11 + a12 * r21, a10 * r02 + a11 * r12 + a12 * r22,
                        a20 * r00 + a21 * r10 + a22 * r20, a20 * r01 + a21 * r11 + a22 * r21, a20 * r02 + a21 * r12 + a22 * r22,
                        a00 * tx + a01 * ty + a02 * tz + mats[q + 9], a10 * tx + a11 * ty + a12 * tz + mats[q + 10], a20 * tx + a21 * ty + a22 * tz + mats[q + 11]);
            }
        }
    }

    private void set(int o, float a, float b, float c, float d, float e, float f, float g, float h, float i, float x, float y, float z) {
        mats[o] = a; mats[o + 1] = b; mats[o + 2] = c; mats[o + 3] = d; mats[o + 4] = e; mats[o + 5] = f; mats[o + 6] = g; mats[o + 7] = h; mats[o + 8] = i;
        mats[o + 9] = x; mats[o + 10] = y; mats[o + 11] = z;
    }
}
