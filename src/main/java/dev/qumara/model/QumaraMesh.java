package dev.qumara.model;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Malla skinned (assets/qumara/model/qumara.mesh): cuads con 4 pesos de hueso por vertice. Java puro. */
public final class QumaraMesh {
    public final int nv, nq;
    public final float[] pos, uv, nrm, weight;     // pos nv*3, uv nv*2, nrm nv*3, weight nv*4
    public final int[] bone;                       // nv*4
    public final int[] quads;                      // nq*4
    private static QumaraMesh instance;

    public static synchronized QumaraMesh get() {
        if (instance == null) {
            try (InputStream in = QumaraMesh.class.getResourceAsStream("/assets/qumara/model/qumara.mesh")) {
                if (in == null) throw new IOException("falta assets/qumara/model/qumara.mesh (ejecuta tools/build_model.py)");
                instance = new QumaraMesh(new DataInputStream(new BufferedInputStream(in, 1 << 16)));
            } catch (IOException e) { throw new RuntimeException("No se pudo cargar qumara.mesh", e); }
        }
        return instance;
    }

    private QumaraMesh(DataInputStream in) throws IOException {
        byte[] m = new byte[4]; in.readFully(m);
        if (m[0] != 'Q' || m[1] != 'M' || m[2] != 'S' || m[3] != 'H') throw new IOException("formato QMSH invalido");
        in.readInt(); nv = in.readInt(); nq = in.readInt();
        pos = new float[nv * 3]; uv = new float[nv * 2]; nrm = new float[nv * 3]; weight = new float[nv * 4]; bone = new int[nv * 4]; quads = new int[nq * 4];
        for (int i = 0; i < pos.length; i++) pos[i] = in.readFloat();
        for (int i = 0; i < uv.length; i++) uv[i] = in.readFloat();
        for (int i = 0; i < nrm.length; i++) nrm[i] = in.readByte() / 127f;
        for (int i = 0; i < bone.length; i++) bone[i] = in.readUnsignedByte();
        for (int i = 0; i < weight.length; i++) weight[i] = in.readUnsignedByte() / 255f;
        for (int i = 0; i < quads.length; i++) quads[i] = in.readInt();
    }

    /** Skinning lineal (LBS) en CPU. mats = nb*12 de QumaraPose. */
    public void skin(float[] mats, float[] outPos, float[] outNrm) {
        for (int v = 0; v < nv; v++) {
            float px = pos[v * 3], py = pos[v * 3 + 1], pz = pos[v * 3 + 2], nx = nrm[v * 3], ny = nrm[v * 3 + 1], nz = nrm[v * 3 + 2];
            float ox = 0, oy = 0, oz = 0, mx = 0, my = 0, mz = 0;
            for (int k = 0; k < 4; k++) {
                float w = weight[v * 4 + k]; if (w <= 0f) continue;
                int o = bone[v * 4 + k] * 12;
                ox += w * (mats[o] * px + mats[o + 1] * py + mats[o + 2] * pz + mats[o + 9]);
                oy += w * (mats[o + 3] * px + mats[o + 4] * py + mats[o + 5] * pz + mats[o + 10]);
                oz += w * (mats[o + 6] * px + mats[o + 7] * py + mats[o + 8] * pz + mats[o + 11]);
                mx += w * (mats[o] * nx + mats[o + 1] * ny + mats[o + 2] * nz);
                my += w * (mats[o + 3] * nx + mats[o + 4] * ny + mats[o + 5] * nz);
                mz += w * (mats[o + 6] * nx + mats[o + 7] * ny + mats[o + 8] * nz);
            }
            outPos[v * 3] = ox; outPos[v * 3 + 1] = oy; outPos[v * 3 + 2] = oz;
            float l = (float) Math.sqrt(mx * mx + my * my + mz * mz); if (l < 1e-6f) l = 1f;
            outNrm[v * 3] = mx / l; outNrm[v * 3 + 1] = my / l; outNrm[v * 3 + 2] = mz / l;
        }
    }
}
