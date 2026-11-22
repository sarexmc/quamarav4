"""
Definicion del esqueleto de QUMARA (nuevo modelo organico con UN solo brazo) + funciones FK / IK / skinning.
Coordenadas de la malla original en unidades 'mesh' (Y arriba, el frente de la planta es +Z, el brazo esta en -X).
Salida al juego en 'px de modelo': P_px = (P_mesh - ORIGIN) * S   (16 px = 1 bloque cuando RENDER = 1).
"""
import numpy as np

S = 155.0                                  # px por unidad de malla  (planta = 300 px de alto)
ORIGIN = np.array([0.0, -0.97, 0.0])

def to_px(p): return (np.asarray(p, float) - ORIGIN) * S

# nombre: (padre, pivote en coordenadas de malla, punta del segmento (para pesos), radio de influencia)
BONES = {
 'raiz':        (None,        (0.00,-0.97, 0.05), (0.00,-0.80, 0.05), 0.10),
 'raices_o':    ('raiz',      (-0.12,-0.93,0.05), (-0.85,-0.93,0.1),  0.15),
 'raices_e':    ('raiz',      ( 0.12,-0.93,0.05), ( 0.90,-0.93,0.1),  0.15),
 'raices_n':    ('raiz',      (0.00,-0.93,-0.10), (0.0,-0.93,-0.70),  0.15),
 'raices_s':    ('raiz',      (0.00,-0.93, 0.15), (0.0,-0.93, 0.70),  0.15),
 'tronco':      ('raiz',      (0.00,-0.85, 0.05), (0.00,-0.45, 0.05), 0.25),
 'cuello':      ('tronco',    (0.00,-0.45, 0.05), (0.00,-0.15, 0.10), 0.25),
 'cabeza':      ('cuello',    (0.00,-0.05, 0.10), (0.00, 0.15, 0.30), 0.30),
 'mandibula':   ('cabeza',    (0.00,-0.03, 0.22), (0.00,-0.37, 0.43), 0.15),
 'lengua_1':    ('cabeza',    (-0.03, 0.03, 0.14), (-0.06,-0.03, 0.25), 0.06),
 'lengua_2':    ('lengua_1',  (-0.06,-0.03, 0.25), (-0.08,-0.09, 0.38), 0.06),
 'lengua_3':    ('lengua_2',  (-0.08,-0.09, 0.38), (-0.10,-0.15, 0.52), 0.06),
 'lengua_4':    ('lengua_3',  (-0.10,-0.15, 0.52), (-0.12,-0.21, 0.63), 0.06),
 'lengua_5':    ('lengua_4',  (-0.12,-0.21, 0.63), (-0.13,-0.34, 0.67), 0.06),
 'petalo_sup':  ('cuello',    (0.00, 0.30,-0.12), (0.00, 0.85,-0.15), 0.35),
 'petalo_izq':  ('cuello',    (-0.25, 0.20,-0.05), (-0.60, 0.35,-0.10), 0.30),
 'petalo_der':  ('cuello',    ( 0.25, 0.20,-0.05), ( 0.70, 0.35,-0.10), 0.30),
 'petalo_fizq': ('cuello',    (-0.25,-0.05, 0.15), (-0.55,-0.10, 0.25), 0.20),
 'petalo_fder': ('cuello',    ( 0.25,-0.05, 0.15), ( 0.60,-0.10, 0.25), 0.20),
 'hoja_1':      ('cuello',    ( 0.30, 0.20,-0.10), ( 0.95, 0.45,-0.10), 0.20),
 'hoja_2':      ('cuello',    ( 0.35,-0.10, 0.00), ( 1.00,-0.30, 0.05), 0.20),
 'flor_1':      ('cuello',    ( 0.10, 0.55,-0.25), ( 0.40, 0.75,-0.30), 0.10),
 'flor_2':      ('flor_1',    ( 0.40, 0.75,-0.30), ( 0.55, 0.88,-0.30), 0.10),
 # --- el unico brazo (lado -X) ---
 'brazo':       ('cuello',    (-0.33,-0.28, 0.18), (-0.80,-0.40, 0.48), 0.20),
 'antebrazo':   ('brazo',     (-0.80,-0.40, 0.48), (-0.88, 0.12, 0.58), 0.18),
 'mano':        ('antebrazo', (-0.88, 0.12, 0.58), (-0.85, 0.38, 0.62), 0.16),
 'dedo_1':      ('mano',      (-0.80, 0.20, 0.62), (-0.44, 0.20, 0.68), 0.07),
 'dedo_2':      ('mano',      (-0.66, 0.12, 0.62), (-0.45,-0.03, 0.62), 0.07),
 'pulgar':      ('mano',      (-0.88, 0.45, 0.60), (-0.72, 0.47, 0.60), 0.07),
}
NAMES = list(BONES.keys())
IDX = {n: i for i, n in enumerate(NAMES)}
PARENT = [IDX[BONES[n][0]] if BONES[n][0] else -1 for n in NAMES]
PIVOT_PX = np.array([to_px(BONES[n][1]) for n in NAMES])
TIP_PX = np.array([to_px(BONES[n][2]) for n in NAMES])

# ------------------------------------------------------------------------------ rotaciones
def rot_xyz(rx, ry, rz):
    """Matriz 3x3 = Rz * Ry * Rx (grados). Convencion compartida con el renderer Java."""
    a, b, c = np.radians([rx, ry, rz])
    ca, sa, cb, sb, cc, sc = np.cos(a), np.sin(a), np.cos(b), np.sin(b), np.cos(c), np.sin(c)
    Rx = np.array([[1, 0, 0], [0, ca, -sa], [0, sa, ca]])
    Ry = np.array([[cb, 0, sb], [0, 1, 0], [-sb, 0, cb]])
    Rz = np.array([[cc, -sc, 0], [sc, cc, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx

def pose_matrices(rot=None, pos=None, scl=None):
    """rot/pos/scl: dict nombre -> vec3 (rot en grados, orden Rz*Ry*Rx). Devuelve (nb,4,4): transformacion de cada hueso
    de coordenadas de REPOSO a coordenadas posadas (espacio modelo). W[i] = W[padre] @ T(pivote+pos) R S T(-pivote)."""
    nb = len(NAMES)
    W = np.zeros((nb, 4, 4))
    for i, n in enumerate(NAMES):
        r = (rot or {}).get(n, (0, 0, 0)); p = (pos or {}).get(n, (0, 0, 0)); s = (scl or {}).get(n, (1, 1, 1))
        R = r if isinstance(r, np.ndarray) and r.shape == (3, 3) else rot_xyz(*r)
        R = R @ np.diag(s)
        pv = PIVOT_PX[i]
        M = np.eye(4); M[:3, :3] = R
        M[:3, 3] = pv + np.asarray(p) - R @ pv
        W[i] = M if PARENT[i] < 0 else W[PARENT[i]] @ M
    return W

def two_bone_ik(shoulder, target, l1, l2, pole):
    """IK analitico 2 huesos. Devuelve el codo (posicion). 'pole' = direccion hacia la que se dobla el codo."""
    shoulder = np.asarray(shoulder, float); target = np.asarray(target, float)
    d = target - shoulder; dist = np.linalg.norm(d)
    dist = np.clip(dist, abs(l1 - l2) + 1e-3, l1 + l2 - 1e-3)
    dn = d / (np.linalg.norm(d) + 1e-9)
    a = (l1 * l1 - l2 * l2 + dist * dist) / (2 * dist)
    h = np.sqrt(max(l1 * l1 - a * a, 0))
    pole = np.asarray(pole, float); pp = pole - dn * np.dot(pole, dn)
    pp = pp / (np.linalg.norm(pp) + 1e-9)
    return shoulder + dn * a + pp * h
