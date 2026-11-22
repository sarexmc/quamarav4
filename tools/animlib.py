"""Libreria de animacion de QUMARA: cuaterniones, IK del brazo, muestreo a 20 fps y exportador binario QANI."""
import numpy as np, struct, math
from qumara_rig import *

FPS = 20

# ------------------------------------------------------------------ rotaciones
def mat_to_quat(R):
    t = np.trace(R)
    if t > 0:
        s = math.sqrt(t + 1) * 2; w = s / 4; x = (R[2, 1] - R[1, 2]) / s; y = (R[0, 2] - R[2, 0]) / s; z = (R[1, 0] - R[0, 1]) / s
    elif R[0, 0] > R[1, 1] and R[0, 0] > R[2, 2]:
        s = math.sqrt(1 + R[0, 0] - R[1, 1] - R[2, 2]) * 2; w = (R[2, 1] - R[1, 2]) / s; x = s / 4; y = (R[0, 1] + R[1, 0]) / s; z = (R[0, 2] + R[2, 0]) / s
    elif R[1, 1] > R[2, 2]:
        s = math.sqrt(1 + R[1, 1] - R[0, 0] - R[2, 2]) * 2; w = (R[0, 2] - R[2, 0]) / s; x = (R[0, 1] + R[1, 0]) / s; y = s / 4; z = (R[1, 2] + R[2, 1]) / s
    else:
        s = math.sqrt(1 + R[2, 2] - R[0, 0] - R[1, 1]) * 2; w = (R[1, 0] - R[0, 1]) / s; x = (R[0, 2] + R[2, 0]) / s; y = (R[1, 2] + R[2, 1]) / s; z = s / 4
    q = np.array([x, y, z, w]); return q / np.linalg.norm(q)

def quat_to_mat(q):
    x, y, z, w = q / np.linalg.norm(q)
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])

def slerp(a, b, t):
    d = np.dot(a, b)
    if d < 0: b = -b; d = -d
    if d > 0.9995:
        r = a + t * (b - a); return r / np.linalg.norm(r)
    th = math.acos(d); return (math.sin((1 - t) * th) * a + math.sin(t * th) * b) / math.sin(th)

def rot_between(a, b):
    a = a / np.linalg.norm(a); b = b / np.linalg.norm(b)
    v = np.cross(a, b); c = float(np.dot(a, b))
    if c < -0.99999:
        ax = np.cross(a, [1, 0, 0]); 
        if np.linalg.norm(ax) < 1e-3: ax = np.cross(a, [0, 1, 0])
        ax /= np.linalg.norm(ax); return 2 * np.outer(ax, ax) - np.eye(3)
    k = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + k + k @ k / (1 + c)

def axis_angle(axis, deg):
    axis = np.asarray(axis, float); axis /= np.linalg.norm(axis); a = math.radians(deg)
    k = np.array([[0, -axis[2], axis[1]], [axis[2], 0, -axis[0]], [-axis[1], axis[0], 0]])
    return np.eye(3) + math.sin(a) * k + (1 - math.cos(a)) * k @ k

def ease(t, kind='smooth'):
    t = min(max(t, 0.0), 1.0)
    if kind == 'smooth': return t * t * (3 - 2 * t)
    if kind == 'in': return t * t * t
    if kind == 'out': return 1 - (1 - t) ** 3
    if kind == 'snap': return 1 - (1 - t) ** 5          # golpe: llega rapido y se asienta
    return t

# ------------------------------------------------------------------ brazo (IK)
SH = PIVOT_PX[IDX['brazo']]; EL = PIVOT_PX[IDX['antebrazo']]; WR = PIVOT_PX[IDX['mano']]
PALM_REST = to_px((-0.80, 0.30, 0.62))                 # centro de la palma/garras en reposo
L1 = np.linalg.norm(EL - SH); L2 = np.linalg.norm(WR - EL)
ARM_BONES = ('brazo', 'antebrazo', 'mano')

def solve_arm(rot_other, target_palm, pole, roll=0.0, hand_curl=(0, 0, 0)):
    """Devuelve dict de matrices 3x3 (rotaciones locales) para brazo/antebrazo/mano de forma que la PALMA llegue a target_palm (px, espacio
    modelo) con la pose del cuerpo definida por rot_other (dict euler). El antebrazo/mano apuntan hacia el objetivo."""
    W = pose_matrices(rot_other)
    Wc = W[IDX['cuello']]
    Rp = Wc[:3, :3]
    S_w = Wc[:3, :3] @ SH + Wc[:3, 3]
    wrist = np.array(target_palm, float) - (PALM_REST - WR)       # primera estimacion (mano sin rotar)
    pole = np.asarray(pole, float)
    for _ in range(4):
        E = two_bone_ik(S_w, wrist, L1, L2, pole)
        u1 = E - S_w; f1 = wrist - E
        wr_dir = wrist - E
        # mano orientada con el antebrazo: la palma sale en la direccion del antebrazo
        R1 = rot_between(EL - SH, u1)
        R2 = rot_between(WR - EL, f1) 
        if abs(roll) > 1e-6: R2 = axis_angle(f1, roll) @ R2
        R3 = R2 @ rot_xyz(*hand_curl)
        # la palma real respecto a la muñeca
        palm = wrist + R3 @ (PALM_REST - WR)
        wrist = wrist + (np.array(target_palm) - palm)
    E = two_bone_ik(S_w, wrist, L1, L2, pole)
    u1 = E - S_w; f1 = wrist - E
    R1 = rot_between(EL - SH, u1)
    R2 = rot_between(WR - EL, f1)
    if abs(roll) > 1e-6: R2 = axis_angle(f1, roll) @ R2
    R3 = R2 @ rot_xyz(*hand_curl)
    return {'brazo': Rp.T @ R1, 'antebrazo': R1.T @ R2, 'mano': R2.T @ R3}

# ------------------------------------------------------------------ pose -> frames
class Pose:
    """Pose en un instante: rot (euler grados) y pos por hueso; arm = dict(target, pole, roll, curl, w) o None."""
    def __init__(self): self.rot = {}; self.pos = {}; self.arm = None

def lerp(a, b, t): return a + (b - a) * t

def interp_pose(p0, p1, t):
    out = Pose()
    for n in set(p0.rot) | set(p1.rot):
        a = np.array(p0.rot.get(n, (0, 0, 0)), float); b = np.array(p1.rot.get(n, (0, 0, 0)), float); out.rot[n] = lerp(a, b, t)
    for n in set(p0.pos) | set(p1.pos):
        a = np.array(p0.pos.get(n, (0, 0, 0)), float); b = np.array(p1.pos.get(n, (0, 0, 0)), float); out.pos[n] = lerp(a, b, t)
    a0, a1 = p0.arm, p1.arm
    if a0 is None and a1 is None: out.arm = None
    else:
        def A(a, ref):
            if a is not None: return a
            r = dict(ref); r['w'] = 0.0; return r
        a0 = A(a0, a1); a1 = A(a1, a0)
        out.arm = dict(target=lerp(np.array(a0['target'], float), np.array(a1['target'], float), t),
                       pole=lerp(np.array(a0['pole'], float), np.array(a1['pole'], float), t),
                       roll=lerp(a0['roll'], a1['roll'], t), curl=lerp(np.array(a0['curl'], float), np.array(a1['curl'], float), t),
                       w=lerp(a0['w'], a1['w'], t))
    return out

def arm(target, pole=(0, -1, 0.3), roll=0.0, curl=(0, 0, 0), w=1.0):
    return dict(target=np.array(target, float), pole=np.array(pole, float), roll=roll, curl=np.array(curl, float), w=w)

def pose_to_frame(p):
    """-> (quat dict nombre->xyzw, pos dict, palm px). Resuelve el brazo con IK."""
    rot = {n: rot_xyz(*v) for n, v in p.rot.items()}
    rot_e = dict(p.rot)
    quats = {}
    if p.arm is not None and p.arm['w'] > 1e-4:
        sol = solve_arm(rot_e, p.arm['target'], p.arm['pole'], p.arm['roll'], tuple(p.arm['curl']))
        for n in ARM_BONES:
            q_ik = mat_to_quat(sol[n]); q_fk = mat_to_quat(rot.get(n, np.eye(3)))
            quats[n] = slerp(q_fk, q_ik, min(1.0, p.arm['w']))
    for n, R in rot.items():
        if n not in quats: quats[n] = mat_to_quat(R)
    return quats, dict(p.pos)

def hand_world(quats, pos):
    rot = {n: quat_to_mat(q) for n, q in quats.items()}
    W = pose_matrices(rot, pos)
    m = W[IDX['mano']]
    return m[:3, :3] @ PALM_REST + m[:3, 3]

class Anim:
    def __init__(self, name, length, loop='once'):
        self.name = name; self.length = float(length); self.loop = loop   # once | loop | hold
        self.keys = []            # (t, Pose, ease)
        self.fn = None
    def key(self, t, ease_in='smooth', rot=None, pos=None, arm_=None):
        p = Pose(); p.rot = {k: np.array(v, float) for k, v in (rot or {}).items()}; p.pos = {k: np.array(v, float) for k, v in (pos or {}).items()}; p.arm = arm_
        self.keys.append((float(t), p, ease_in)); return self
    def pose_at(self, t):
        if self.fn is not None: return self.fn(t)
        ks = sorted(self.keys, key=lambda k: k[0])
        if t <= ks[0][0]: return ks[0][1]
        for (t0, p0, _), (t1, p1, e) in zip(ks[:-1], ks[1:]):
            if t <= t1:
                return interp_pose(p0, p1, ease((t - t0) / max(1e-6, t1 - t0), e))
        return ks[-1][1]
    def frames(self):
        n = int(round(self.length * FPS)) + 1
        out = []
        for i in range(n):
            q, p = pose_to_frame(self.pose_at(i / FPS)); out.append((q, p, hand_world(q, p)))
        return out

# ------------------------------------------------------------------ exportador binario QANI v1
def write_qani(path, anims):
    def ws(f, s): b = s.encode('utf-8'); f.write(struct.pack('>H', len(b))); f.write(b)
    with open(path, 'wb') as f:
        f.write(b'QANI'); f.write(struct.pack('>i', 1))
        f.write(struct.pack('>i', len(NAMES)))
        for i, n in enumerate(NAMES):
            ws(f, n); f.write(struct.pack('>h', PARENT[i])); f.write(struct.pack('>3f', *PIVOT_PX[i]))
        f.write(struct.pack('>i', len(anims)))
        for a in anims:
            fr = a.frames()
            ws(f, a.name); f.write(struct.pack('>f', a.length)); f.write(struct.pack('>b', {'once': 0, 'loop': 1, 'hold': 2}[a.loop]))
            f.write(struct.pack('>i', len(fr)))
            # que huesos se mueven
            rb = []; pb = []
            for n in NAMES:
                if any(n in q for q, _, _ in fr):
                    qs = [q.get(n, np.array([0, 0, 0, 1.0])) for q, _, _ in fr]
                    if any(np.linalg.norm(x - np.array([0, 0, 0, 1.0])) > 1e-4 and np.linalg.norm(x + np.array([0, 0, 0, 1.0])) > 1e-4 for x in qs): rb.append(n)
                if any(n in p for _, p, _ in fr):
                    if any(np.linalg.norm(np.array(p.get(n, (0, 0, 0)))) > 1e-4 for _, p, _ in fr): pb.append(n)
            f.write(struct.pack('>h', len(rb)))
            for n in rb:
                f.write(struct.pack('>h', IDX[n]))
                prev = None
                for q, _, _ in fr:
                    x = q.get(n, np.array([0, 0, 0, 1.0]))
                    if prev is not None and np.dot(prev, x) < 0: x = -x
                    prev = x; f.write(struct.pack('>4f', *x))
            f.write(struct.pack('>h', len(pb)))
            for n in pb:
                f.write(struct.pack('>h', IDX[n]))
                for _, p, _ in fr: f.write(struct.pack('>3f', *np.array(p.get(n, (0, 0, 0)), float)))
            # trayectoria de la mano (para sincronizar agarres con el servidor)
            f.write(struct.pack('>b', 1))
            for _, _, h in fr: f.write(struct.pack('>3f', *h))
