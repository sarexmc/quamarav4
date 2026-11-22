package dev.qumara.model;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Esqueleto + animaciones de Qumara (archivo assets/qumara/model/qumara.qani, generado por tools/build_model.py).
 * Java puro (sin Minecraft): lo usan el cliente (render) y el servidor (posicion de la mano para agarres).
 */
public final class QumaraRig {
    public static final class Anim {
        public String name; public float length; public int loop;      // 0 una vez, 1 bucle, 2 mantener ultimo fotograma
        public int frames;
        public int[] rotBone; public float[][] rot;                    // por hueso: frames*4 (x,y,z,w)
        public int[] posBone; public float[][] pos;                    // por hueso: frames*3
        public float[] hand;                                           // frames*3: palma en px de modelo
    }

    public static final float FPS = 20f;
    public final int nb;
    public final String[] names;
    public final int[] parent;
    public final float[] pivot;                                        // nb*3 (px de modelo)
    public final Map<String, Anim> anims = new HashMap<>();
    public final Map<String, Integer> index = new HashMap<>();

    private static QumaraRig instance;

    public static synchronized QumaraRig get() {
        if (instance == null) {
            try (InputStream in = QumaraRig.class.getResourceAsStream("/assets/qumara/model/qumara.qani")) {
                if (in == null) throw new IOException("falta assets/qumara/model/qumara.qani (ejecuta tools/build_model.py)");
                instance = new QumaraRig(new DataInputStream(new BufferedInputStream(in, 1 << 16)));
            } catch (IOException e) { throw new RuntimeException("No se pudo cargar qumara.qani", e); }
        }
        return instance;
    }

    private QumaraRig(DataInputStream in) throws IOException {
        byte[] magic = new byte[4]; in.readFully(magic);
        if (magic[0] != 'Q' || magic[1] != 'A' || magic[2] != 'N' || magic[3] != 'I') throw new IOException("formato QANI invalido");
        in.readInt();
        nb = in.readInt(); names = new String[nb]; parent = new int[nb]; pivot = new float[nb * 3];
        for (int i = 0; i < nb; i++) {
            names[i] = in.readUTF(); index.put(names[i], i); parent[i] = in.readShort();
            for (int k = 0; k < 3; k++) pivot[i * 3 + k] = in.readFloat();
        }
        int na = in.readInt();
        for (int a = 0; a < na; a++) {
            Anim an = new Anim(); an.name = in.readUTF(); an.length = in.readFloat(); an.loop = in.readByte(); an.frames = in.readInt();
            int nr = in.readShort(); an.rotBone = new int[nr]; an.rot = new float[nr][an.frames * 4];
            for (int i = 0; i < nr; i++) { an.rotBone[i] = in.readShort(); for (int f = 0; f < an.frames * 4; f++) an.rot[i][f] = in.readFloat(); }
            int np = in.readShort(); an.posBone = new int[np]; an.pos = new float[np][an.frames * 3];
            for (int i = 0; i < np; i++) { an.posBone[i] = in.readShort(); for (int f = 0; f < an.frames * 3; f++) an.pos[i][f] = in.readFloat(); }
            if (in.readByte() != 0) { an.hand = new float[an.frames * 3]; for (int f = 0; f < an.hand.length; f++) an.hand[f] = in.readFloat(); }
            anims.put(an.name, an);
        }
    }

    /** Tiempo de animacion segun el tipo de bucle. */
    public static float wrap(Anim a, float t) {
        if (a.loop == 1) { t = t % a.length; if (t < 0) t += a.length; return t; }
        return Math.max(0f, Math.min(a.length, t));
    }

    /** Posicion de la palma (px de modelo, +Z frente, -X lado del brazo) en el segundo 't' de la animacion; null si no existe. */
    public float[] handAt(String anim, float t) {
        Anim a = anims.get(anim);
        if (a == null || a.hand == null) return null;
        float f = wrap(a, t) * FPS; int i0 = Math.min((int) f, a.frames - 1), i1 = Math.min(i0 + 1, a.frames - 1); float u = f - i0;
        float[] o = new float[3];
        for (int k = 0; k < 3; k++) o[k] = a.hand[i0 * 3 + k] * (1 - u) + a.hand[i1 * 3 + k] * u;
        return o;
    }

    /** Escribe en rot (nb*4) / pos (nb*3) los huesos de la animacion permitidos por la mascara (los demas no se tocan). */
    public void sample(Anim a, float t, float[] rot, float[] pos, boolean[] mask) {
        float f = wrap(a, t) * FPS; int i0 = Math.min((int) f, a.frames - 1), i1 = Math.min(i0 + 1, a.frames - 1); float u = f - i0;
        for (int i = 0; i < a.rotBone.length; i++) {
            int b = a.rotBone[i]; if (!mask[b]) continue;
            float[] d = a.rot[i];
            float x0 = d[i0 * 4], y0 = d[i0 * 4 + 1], z0 = d[i0 * 4 + 2], w0 = d[i0 * 4 + 3];
            float x1 = d[i1 * 4], y1 = d[i1 * 4 + 1], z1 = d[i1 * 4 + 2], w1 = d[i1 * 4 + 3];
            if (x0 * x1 + y0 * y1 + z0 * z1 + w0 * w1 < 0) { x1 = -x1; y1 = -y1; z1 = -z1; w1 = -w1; }
            float x = x0 + (x1 - x0) * u, y = y0 + (y1 - y0) * u, z = z0 + (z1 - z0) * u, w = w0 + (w1 - w0) * u;
            float n = (float) Math.sqrt(x * x + y * y + z * z + w * w); if (n < 1e-6f) { x = y = z = 0; w = 1; n = 1; }
            rot[b * 4] = x / n; rot[b * 4 + 1] = y / n; rot[b * 4 + 2] = z / n; rot[b * 4 + 3] = w / n;
        }
        for (int i = 0; i < a.posBone.length; i++) {
            int b = a.posBone[i]; if (!mask[b]) continue;
            float[] d = a.pos[i];
            for (int k = 0; k < 3; k++) pos[b * 3 + k] = d[i0 * 3 + k] * (1 - u) + d[i1 * 3 + k] * u;
        }
    }
}
