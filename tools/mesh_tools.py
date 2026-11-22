"""Herramientas de malla: OBJ -> malla decimada (QEM por clustering, preserva islas UV) -> cuads -> binario QMSH."""
import numpy as np, scipy.sparse as sp, struct, time
from scipy.sparse.csgraph import connected_components

def parse_obj(path):
    with open(path, 'rb') as f: lines = f.read().split(b'\n')
    v = [l[2:] for l in lines if l.startswith(b'v ')]; vt = [l[3:] for l in lines if l.startswith(b'vt ')]; fl = [l[2:] for l in lines if l.startswith(b'f ')]
    V = np.array(b' '.join(v).split(), dtype=np.float32).reshape(-1, 3)
    VT = np.array(b' '.join(vt).split(), dtype=np.float32).reshape(-1, 2)
    arr = np.array(b' '.join(fl).replace(b'/', b' ').split(), dtype=np.int64).reshape(len(fl), -1)
    assert arr.shape[1] == 9, 'el OBJ debe estar triangulado con formato v/vt/vn'
    return dict(V=V, VT=VT, vi=(arr[:, 0::3] - 1).astype(np.int32), ti=(arr[:, 1::3] - 1).astype(np.int32))

def decimate(raw, cells=90):
    V = raw['V'].astype(np.float64); VT = raw['VT'].astype(np.float64); vi = raw['vi']; ti = raw['ti']; nT = len(vi)
    q = np.round(V * 1e5).astype(np.int64)
    _, inv = np.unique(q, axis=0, return_inverse=True); inv = inv.ravel()
    P = np.zeros((inv.max() + 1, 3)); P[inv] = V
    tri = inv[vi]
    fn = np.cross(P[tri[:, 1]] - P[tri[:, 0]], P[tri[:, 2]] - P[tri[:, 0]]); a2 = np.linalg.norm(fn, axis=1); good = a2 > 1e-14
    fnu = np.zeros_like(fn); fnu[good] = fn[good] / a2[good, None]
    nvt = len(VT); a, b, c = ti[:, 0], ti[:, 1], ti[:, 2]
    g = sp.coo_matrix((np.ones(nT * 3), (np.r_[a, b, c], np.r_[b, c, a])), shape=(nvt, nvt))
    nisl, isl_vt = connected_components(g, directed=False); isl = isl_vt[ti[:, 0]]
    lo = P.min(0) - 1e-6; hi = P.max(0) + 1e-6; h = (hi - lo).max() / cells
    cell = np.floor((P - lo) / h).astype(np.int64); dims = np.ceil((hi - lo) / h).astype(np.int64) + 1
    cid = (cell[:, 0] * dims[1] + cell[:, 1]) * dims[2] + cell[:, 2]
    dd = -(fnu * P[tri[:, 0]]).sum(1); w = a2 * 0.5 * good
    n_ = fnu
    qt = np.stack([n_[:, 0] ** 2, n_[:, 0] * n_[:, 1], n_[:, 0] * n_[:, 2], n_[:, 0] * dd, n_[:, 1] ** 2, n_[:, 1] * n_[:, 2], n_[:, 1] * dd, n_[:, 2] ** 2, n_[:, 2] * dd, dd ** 2], 1) * w[:, None]
    Q = np.zeros((len(P), 10))
    for k in range(3): np.add.at(Q, tri[:, k], qt / 3)
    ucell, cinv = np.unique(cid, return_inverse=True); nc = len(ucell)
    QC = np.zeros((nc, 10)); np.add.at(QC, cinv, Q)
    CN = np.bincount(cinv, minlength=nc).astype(float); CEN = np.zeros((nc, 3)); np.add.at(CEN, cinv, P); CEN /= CN[:, None]
    A = np.zeros((nc, 3, 3)); bv = np.zeros((nc, 3))
    A[:, 0, 0] = QC[:, 0]; A[:, 0, 1] = A[:, 1, 0] = QC[:, 1]; A[:, 0, 2] = A[:, 2, 0] = QC[:, 2]
    A[:, 1, 1] = QC[:, 4]; A[:, 1, 2] = A[:, 2, 1] = QC[:, 5]; A[:, 2, 2] = QC[:, 7]
    bv[:, 0] = -QC[:, 3]; bv[:, 1] = -QC[:, 6]; bv[:, 2] = -QC[:, 8]
    eps = 1e-2 * (np.trace(A, axis1=1, axis2=2) + 1e-12) / 3
    sol = np.linalg.solve(A + eps[:, None, None] * np.eye(3)[None], (bv + eps[:, None] * CEN)[:, :, None])[:, :, 0]
    cl = np.array([ucell // (dims[1] * dims[2]), (ucell // dims[2]) % dims[1], ucell % dims[2]]).T
    POS = np.clip(sol, lo + cl * h - 0.25 * h, lo + (cl + 1) * h + 0.25 * h)
    cpos_t = cinv[tri]
    key = cpos_t.astype(np.int64) * nisl + isl[:, None]
    ukey, kinv = np.unique(key.ravel(), return_inverse=True); kinv = kinv.reshape(-1, 3); nout = len(ukey)
    UV = np.zeros((nout, 2)); cnt = np.zeros(nout); np.add.at(UV, kinv.ravel(), VT[ti.ravel()]); np.add.at(cnt, kinv.ravel(), 1); UV /= cnt[:, None]
    NR = np.zeros((nout, 3))
    for k in range(3): np.add.at(NR, kinv[:, k], fnu * w[:, None])
    nl = np.linalg.norm(NR, axis=1); nl[nl == 0] = 1; NR /= nl[:, None]
    OP = POS[ukey // nisl]
    T = kinv.copy()
    ok = (T[:, 0] != T[:, 1]) & (T[:, 1] != T[:, 2]) & (T[:, 0] != T[:, 2]) & ~((cpos_t[:, 0] == cpos_t[:, 1]) & (cpos_t[:, 1] == cpos_t[:, 2]))
    T = T[ok]; s = np.sort(T, axis=1); _, ui = np.unique(s, axis=0, return_index=True); T = T[np.sort(ui)]
    used = np.unique(T.ravel()); rm = -np.ones(nout, np.int64); rm[used] = np.arange(len(used))
    return dict(P=OP[used].astype(np.float32), UV=UV[used].astype(np.float32), N=NR[used].astype(np.float32), T=rm[T].astype(np.int32))

def pair_quads(P, T):
    """Une pares de triangulos adyacentes (misma orientacion) en cuads: (p,q,r,s) -> tris (p,q,r),(r,s,p). Sobrantes: (a,b,c,c)."""
    n = len(T)
    e2t = {}
    for i in range(n):
        for k in range(3): e2t[(int(T[i, k]), int(T[i, (k + 1) % 3]))] = (i, k)
    nrm = np.cross(P[T[:, 1]] - P[T[:, 0]], P[T[:, 2]] - P[T[:, 0]]); nrm /= (np.linalg.norm(nrm, axis=1, keepdims=True) + 1e-12)
    used = np.zeros(n, bool); quads = []
    for i in range(n):
        if used[i]: continue
        best = None; bd = 0.6
        for k in range(3):
            a, b = int(T[i, k]), int(T[i, (k + 1) % 3])
            o = e2t.get((b, a))
            if o is None: continue
            j, m = o
            if used[j] or j == i: continue
            d = float(nrm[i] @ nrm[j])
            if d > bd: bd = d; best = (k, j, m)
        used[i] = True
        if best is None:
            quads.append((T[i, 0], T[i, 1], T[i, 2], T[i, 2])); continue
        k, j, m = best; used[j] = True
        a, b, c = int(T[i, k]), int(T[i, (k + 1) % 3]), int(T[i, (k + 2) % 3])
        s = int(T[j, (m + 2) % 3])
        quads.append((b, c, a, s))
    return np.array(quads, np.int32)

def write_qmsh(path, P_px, UV, N, bidx, bw, quads):
    nv = len(P_px)
    with open(path, 'wb') as f:
        f.write(b'QMSH'); f.write(struct.pack('>iii', 1, nv, len(quads)))
        f.write(P_px.astype('>f4').tobytes())
        uv = UV.copy(); uv[:, 1] = 1.0 - uv[:, 1]                         # origen de V arriba (Minecraft)
        f.write(uv.astype('>f4').tobytes())
        f.write(np.clip(np.round(N * 127), -127, 127).astype(np.int8).tobytes())
        f.write(bidx.astype(np.uint8).tobytes())                          # nv x 4
        f.write(np.clip(np.round(bw * 255), 0, 255).astype(np.uint8).tobytes())   # nv x 4
        f.write(quads.astype('>i4').tobytes())
