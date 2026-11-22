import numpy as np, scipy.sparse as sp
from qumara_rig import *

def seg_dist(P, a, b):
    ab = b - a; t = np.clip(((P - a) @ ab) / (ab @ ab + 1e-12), 0, 1)
    return np.linalg.norm(P - (a + t[:, None] * ab), axis=1), t

def smoothstep(x): x = np.clip(x, 0, 1); return x * x * (3 - 2 * x)

def classify(P, c):
    """Grupo duro de cada vertice (nombre de hueso 'principal' o grupo) segun posicion y color."""
    X, Y, Z = P[:, 0], P[:, 1], P[:, 2]
    r, g, b = c[:, 0], c[:, 1], c[:, 2]
    pink = (r > 0.45) & (g < 0.45) & (b > 0.22) & (r > g * 1.6)
    purple = (b > r * 0.85) & (b > g * 1.2) & (b > 0.25)
    olive = (g > b * 1.25) & (g > 0.2) & (r > 0.15) & (g > r * 0.75)
    G = np.array(['tronco'] * len(P), dtype=object)
    def setg(mask, name): G[mask] = name
    # cabeza (caja roja/naranja + dientes)
    head = (np.abs(X) < 0.34) & (Z > -0.16) & (Y > -0.42) & (Y < 0.50) & ~olive
    setg(head, 'cabeza')
    setg(head & (Y < -0.02) & (Z > 0.20) & (np.abs(X) < 0.26), 'mandibula')
    # petalos
    setg((X < -0.18) & (Y > 0.08) & (Z < 0.38), 'petalo_izq')
    setg((X > 0.18) & (Y > 0.08) & (Z < 0.38), 'petalo_der')
    setg((np.abs(X) < 0.34) & (Y > 0.30) & (Z < 0.10) & ~head, 'petalo_sup')
    setg((np.abs(X) < 0.34) & (Y > 0.30) & (Z < 0.10) & head & (Z < 0.0) & (Y > 0.36), 'petalo_sup')
    setg(pink & (X > -0.68) & (X < -0.2) & (Z > 0.05) & (Z < 0.38) & (Y > -0.25) & (Y < 0.12), 'petalo_fizq')
    setg(pink & (X > 0.2) & (X < 0.70) & (Z > 0.0) & (Z < 0.40) & (Y > -0.30) & (Y < 0.12), 'petalo_fder')
    # hojas y flores
    setg(olive & (X > 0.38) & (Y > 0.10), 'hoja_1')
    setg(olive & (X > 0.38) & (Y <= 0.10) & (Y > -0.55), 'hoja_2')
    purple2 = (b > r * 0.8) & (b > g * 1.3)
    setg((Y > 0.55) & (X > 0.12) & (X < 0.75) & ~purple2, 'flor_1')
    setg((Y > 0.62) & (X > 0.38) & (X < 0.75) & ~purple2, 'flor_2')
    setg((Y > 0.80) & (X > -0.2) & (X < 0.30) & (Z < 0.0) & ~purple2, 'flor_1')
    # brazo
    arm = (X < -0.28) & (Y > -0.62) & ((Z > 0.40) | ((Y < -0.08) & (Z > 0.08)) | ((Y < 0.10) & (Z > 0.30) & ~pink))
    arm |= (X < -0.62) & (Y > -0.62) & (Z > 0.22)
    arm &= ~((G == 'petalo_fizq'))
    setg(arm, 'brazo')
    # lengua
    for_t = np.array([-0.03, 0.03, 0.14]); 
    tpts = [BONES[f'lengua_{i}'][1] for i in range(1, 6)] + [BONES['lengua_5'][2]]
    dmin = np.full(len(P), 9.0)
    for a, b2 in zip(tpts[:-1], tpts[1:]):
        d, _ = seg_dist(P, np.array(a), np.array(b2)); dmin = np.minimum(dmin, d)
    mag = (r > 0.5) & (g < 0.28) & (b > 0.35)
    setg(mag & (dmin < 0.065) & (Z > 0.10) & (Y < 0.06), 'lengua_1')
    # raices
    rad = np.hypot(X, Z - 0.05)
    setg((Y < -0.80) & (rad > 0.22), 'raices')
    return G

def compute_weights(P, c, adj=None, smooth_iters=3):
    n = len(P)
    G = classify(P, c)
    r_, g_, b_ = c[:, 0], c[:, 1], c[:, 2]
    pink = (r_ > 0.45) & (g_ < 0.45) & (b_ > 0.22) & (r_ > g_ * 1.6)
    W = np.zeros((n, len(NAMES)))
    Y = P[:, 1]
    for gname in np.unique(G):
        m = G == gname
        Pm = P[m]
        if gname == 'tronco':
            t = smoothstep((Pm[:, 1] + 0.72) / 0.5)          # tronco -> cuello
            w = np.zeros((m.sum(), len(NAMES)))
            w[:, IDX['tronco']] = 1 - t; w[:, IDX['cuello']] = t
            low = Pm[:, 1] < -0.80
            w[low, IDX['tronco']] = 0.6; w[low, IDX['raiz']] = 0.4
            W[m] = w
        elif gname == 'raices':
            ang = np.arctan2(Pm[:, 2] - 0.05, Pm[:, 0])
            names = np.where(np.abs(Pm[:, 0]) > np.abs(Pm[:, 2] - 0.05), np.where(Pm[:, 0] < 0, 'raices_o', 'raices_e'),
                             np.where(Pm[:, 2] - 0.05 < 0, 'raices_n', 'raices_s'))
            w = np.zeros((m.sum(), len(NAMES)))
            rad = np.hypot(Pm[:, 0], Pm[:, 2] - 0.05)
            k = smoothstep((rad - 0.22) / 0.4)
            for i, nm in enumerate(names): w[i, IDX[nm]] = k[i]
            w[:, IDX['raiz']] = 1 - k
            W[m] = w
        elif gname in ('brazo',):
            cand = ['cuello', 'brazo', 'antebrazo', 'mano', 'dedo_1', 'dedo_2', 'pulgar']
            D = np.zeros((m.sum(), len(cand)))
            sh = np.array(BONES['brazo'][1])
            for k, nm in enumerate(cand):
                a = np.array(BONES[nm][1]); b2 = np.array(BONES[nm][2])
                if nm == 'cuello': a, b2 = sh + np.array([0.28, 0.05, -0.05]), sh + np.array([0.35, 0.10, -0.05])
                d, _ = seg_dist(Pm, a, b2); D[:, k] = d / max(BONES[nm][3], 0.06)
            # los dedos solo en la zona de la mano (z>0.45, y>-0.12, x>-0.98)
            hand = (Pm[:, 2] > 0.46) & (Pm[:, 1] > -0.10)
            pk = pink[m]
            D[~(hand & pk), 4:] += 6.0
            D[~hand, 3] += 1.5                                   # palma solo en la mano
            E = np.exp(-(D ** 2) * 1.2)
            # antes del hombro fuerza cuello
            w = E / (E.sum(1, keepdims=True) + 1e-9)
            for k, nm in enumerate(cand): W[np.where(m)[0], IDX[nm]] = w[:, k]
        elif gname.startswith('lengua'):
            cand = [f'lengua_{i}' for i in range(1, 6)]
            D = np.zeros((m.sum(), 5))
            for k, nm in enumerate(cand):
                d, _ = seg_dist(Pm, np.array(BONES[nm][1]), np.array(BONES[nm][2])); D[:, k] = d
            E = np.exp(-(D / 0.05) ** 2)
            w = E / (E.sum(1, keepdims=True) + 1e-9)
            for k, nm in enumerate(cand): W[np.where(m)[0], IDX[nm]] = w[:, k]
        else:
            W[np.where(m)[0], IDX[gname]] = 1.0
    # suavizado por conectividad, pero SOLO entre huesos vecinos en el esqueleto (padre/hijo/hermanos); el brazo no se mezcla con nada mas
    ARM = {'brazo', 'antebrazo', 'mano', 'dedo_1', 'dedo_2', 'pulgar'}
    nb = len(NAMES)
    allowed = np.zeros((nb, nb), bool)
    for i, nm in enumerate(NAMES):
        allowed[i, i] = True
        if PARENT[i] >= 0: allowed[i, PARENT[i]] = True; allowed[PARENT[i], i] = True
        if nm not in ARM and PARENT[i] >= 0:
            for j, nj in enumerate(NAMES):
                if PARENT[j] == PARENT[i] and nj not in ARM and not nj.startswith('raices') and not nj.startswith('lengua'): allowed[i, j] = True
    home = np.argmax(W, axis=1)
    mask = allowed[home]
    W = W * mask
    if adj is not None:
        for _ in range(smooth_iters):
            Wn = adj @ W
            W = (0.5 * W + 0.5 * Wn) * mask
        # top-4
    idx = np.argsort(-W, axis=1)[:, :4]
    w4 = np.take_along_axis(W, idx, 1)
    w4 /= (w4.sum(1, keepdims=True) + 1e-12)
    return G, idx.astype(np.uint8), w4.astype(np.float32)

def build_adj(n, T):
    a, b, c = T[:, 0], T[:, 1], T[:, 2]
    r = np.r_[a, b, c, b, c, a]; cc = np.r_[b, c, a, a, b, c]
    A = sp.coo_matrix((np.ones(len(r)), (r, cc)), shape=(n, n)).tocsr()
    A.data[:] = 1
    deg = np.asarray(A.sum(1)).ravel(); deg[deg == 0] = 1
    return sp.diags(1 / deg) @ A
