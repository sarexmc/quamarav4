"""Catalogo de animaciones de QUMARA (generadas por codigo, IK para el brazo). 20 fps. Se exporta a .qani por build_model.py.
Unidades: segundos, grados, px de modelo (1 bloque = 16/RENDER px; RENDER = 1.3 -> 12.3 px/bloque). +Z = frente, -X = lado del brazo (derecha del jefe)."""
import numpy as np, math
from animlib import *

RENDER = 1.3
PXB = 16.0 / RENDER                    # px por bloque
def blk(b): return b * PXB
def Ry(deg): return rot_xyz(0, deg, 0)
def ring(psi, r, h, side='r'):
    """Punto a distancia r del eje del jefe, angulo psi medido desde su lado (derecha para 'r') hacia el frente (igual que SweepAttack)."""
    a = math.radians(psi)
    return np.array([(-1 if side == 'r' else 1) * r * math.cos(a), h, r * math.sin(a)])
def ground(fwd_blocks, side_blocks=0.0, h=14.0, side='r'):
    s = -1 if side == 'r' else 1
    return np.array([s * blk(side_blocks), h, blk(fwd_blocks)])

PETALS = ('petalo_sup', 'petalo_izq', 'petalo_der', 'petalo_fizq', 'petalo_fder')
def body(lean=0, head=0, jaw=0, flare=0, turn=0, headturn=0, tilt=0, flor=0, leaf=0, tongue=0, extra=None, raiz_y=0.0):
    """lean: + inclina el tronco hacia delante; head: + baja la cabeza; jaw: -55 cierra, +20 abre del todo; flare: + abre petalos."""
    r = {'tronco': (lean * 0.35, turn * 0.3, tilt * 0.3), 'cuello': (lean * 0.65, turn * 0.7, tilt * 0.7), 'cabeza': (head, headturn, 0), 'mandibula': (jaw, 0, 0),
         'petalo_sup': (-24 * flare, 0, 0), 'petalo_izq': (0, 0, 28 * flare), 'petalo_der': (0, 0, -28 * flare),
         'petalo_fizq': (0, 0, 22 * flare), 'petalo_fder': (0, 0, -22 * flare),
         'flor_1': (flor, 0, flor), 'flor_2': (flor, 0, -flor), 'hoja_1': (0, 0, -12 * leaf), 'hoja_2': (0, 0, -9 * leaf)}
    for i in range(1, 6): r[f'lengua_{i}'] = (-tongue, 0, 0)
    if extra: r.update(extra)
    return r
def claws(c):   # 0 abiertas, 1 cerradas
    return {'dedo_1': (0, 0, -55 * c), 'dedo_2': (0, 0, -45 * c), 'pulgar': (0, 0, -50 * c)}
def merge(*ds):
    out = {}
    for d in ds: out.update(d)
    return out
UPOLE = lambda side='r': (-0.4 if side == 'r' else 0.4, 1.0, -0.1)   # el codo se dobla hacia arriba/afuera

ANIMS = []
def A(name, length, loop='once'):
    a = Anim(name, length, loop); ANIMS.append(a); return a
def k(a, t, e='smooth', rot=None, pos=None, arm_=None): a.key(t, e, rot, pos, arm_); return a
def hand(tgt, side='r', roll=0, curl=(0, 0, 0), w=1.0, pole=None): return arm(tgt, pole or UPOLE(side), roll, curl, w)

# ======================================================================== IDLE (bucle)
def idle_fn(period, amp, lean0, jaw0, flare0, tongue_amp, shake=0.0, rage=False):
    def f(t):
        p = Pose(); w = 2 * math.pi / period; s = math.sin(w * t); c = math.cos(w * t)
        r = body(lean=lean0 + 1.6 * amp * s, head=1.5 * amp * math.sin(w * t + 0.9), jaw=jaw0 + 2.5 * amp * s, flare=flare0 + 0.12 * amp * s,
                 flor=3 * amp * math.sin(w * t + 1.3), leaf=0.6 * amp * math.sin(w * t + 2.0))
        r['petalo_izq'] = (2 * amp * c, 0, 28 * (flare0 + 0.12 * amp * s)); r['petalo_der'] = (2 * amp * c, 0, -28 * (flare0 + 0.12 * amp * math.sin(w * t + 1)))
        r['antebrazo'] = (3 * amp * s, 0, 0); r['brazo'] = (0, 0, 2 * amp * c); r['mano'] = (2 * amp * c, 0, 0)
        for i in range(1, 6): r[f'lengua_{i}'] = (-4 * tongue_amp * math.sin(w * t + i * 0.7), 3 * tongue_amp * math.sin(w * t + i * 0.5), 0)
        if shake: 
            for n in ('cuello', 'cabeza', 'brazo'): r[n] = tuple(np.array(r[n]) + shake * np.array([math.sin(w * t * 9 + 1), math.sin(w * t * 11), math.sin(w * t * 7)]))
        p.rot = {n: np.array(v, float) for n, v in r.items()}; return p
    return f
for nm, per, amp, lean0, jaw0, fl, ta, sh in (('idle', 4.0, 1.0, 0, 0, 0, 1.0, 0), ('idle_fury', 2.0, 1.7, 5, 6, 0.35, 1.8, 0.0), ('idle_rage', 1.0, 2.2, 9, 10, 0.6, 2.6, 0.9)):
    a = A(nm, 4.0, 'loop'); a.fn = idle_fn(per, amp, lean0, jaw0, fl, ta, sh)
for nm, per, amp in (('lengua_idle', 4.0, 1.0), ('lengua_idle_fury', 2.0, 1.8), ('lengua_idle_rage', 1.0, 2.6)):
    a = A(nm, 4.0, 'loop')
    def mk(per=per, amp=amp):
        def f(t):
            p = Pose(); w = 2 * math.pi / per
            for i in range(1, 6): p.rot[f'lengua_{i}'] = np.array([-5 * amp * math.sin(w * t + i * 0.7), 4 * amp * math.sin(w * t + i * 0.5), 0])
            return p
        return f
    a.fn = mk()

# ======================================================================== LENGUA
def tongue_pose(curl=0, ext=0, yaw=0, wave=0, t=0):
    r = {}; pos = {}
    for i in range(1, 6):
        r[f'lengua_{i}'] = (-curl - 6 * ext + wave * math.sin(t * 14 + i), yaw if i == 1 else 0, 0)
        pos[f'lengua_{i}'] = (0, 0, 48 * ext * i / 5 * (1 if i > 1 else 0.4)) if ext else (0, 0, 0)
    return r, pos
def tong(a, t, curl=0, ext=0, yaw=0, e='smooth'):
    r, p = tongue_pose(curl, ext, yaw); return k(a, t, e, r, p)
a = A('lengua_atacar', 3.2); tong(a, 0); tong(a, 0.8, curl=40); tong(a, 1.0, curl=55, e='in'); tong(a, 1.2, curl=-10, ext=1.0, e='snap'); tong(a, 1.3, ext=1.0, curl=-10)
tong(a, 1.9, ext=0.8); tong(a, 2.4, curl=10, ext=0.1); tong(a, 3.2)
a = A('lengua_rugir', 3.0)
for i, t in enumerate(np.arange(0, 3.01, 0.25)):
    r, p = tongue_pose(curl=20 * math.sin(i), ext=0.35 * (i % 2)); k(a, t, 'smooth', r, p)
a = A('lengua_morir', 5.0, 'hold'); tong(a, 0); tong(a, 2.0, curl=-30); tong(a, 4.0, curl=-60, ext=0.4); tong(a, 5.0, curl=-60, ext=0.4)
a = A('lengua_abanico', 5.0); tong(a, 0)
for hit, yaw in ((1.5, -22), (2.7, 22), (3.9, 0)):
    tong(a, hit - 0.75, curl=35, yaw=yaw * 0.6); tong(a, hit - 0.1, curl=45, yaw=yaw * 0.8, e='in'); tong(a, hit + 0.1, curl=-10, ext=1.0, yaw=yaw, e='snap'); tong(a, hit + 0.5, ext=0.9, yaw=yaw); tong(a, hit + 0.9, curl=10, ext=0.1, yaw=0)
tong(a, 5.0)
a = A('lengua_devorar', 4.0); tong(a, 0); tong(a, 0.6, curl=30, ext=-0.5); tong(a, 3.0, curl=35, ext=-0.6); tong(a, 3.1, curl=-20, ext=0.9, e='snap'); tong(a, 3.6, curl=10, ext=0.2); tong(a, 4.0)
a = A('lengua_veneno', 3.0); tong(a, 0); tong(a, 0.5, curl=35); tong(a, 1.0, curl=55); tong(a, 1.9, curl=55); tong(a, 2.4, curl=15); tong(a, 3.0)

# ======================================================================== CUERPO / BRAZO
rest_arm = None
# --- SLAM (un golpe con el unico brazo)
def slam(name, side, fwd=12.48, sd=1.85, imp=1.45, total=2.6):
    a = A(name, total); tgt = ground(fwd, sd, 14, side); up = np.array([(-1 if side == 'r' else 1) * 40, 330, -50])
    k(a, 0)
    k(a, 0.9, 'smooth', body(lean=-12, head=-8, jaw=10, flare=0.6, extra=claws(0)), arm_=hand(up, side, curl=(-20, 0, 0)))
    k(a, imp - 0.12, 'smooth', body(lean=-16, head=-10, jaw=14, flare=0.7, extra=claws(0)), arm_=hand(up + np.array([0, 20, -20]), side))
    k(a, imp, 'snap', body(lean=16, head=14, jaw=-40, flare=-0.2, extra=claws(0.4)), arm_=hand(tgt, side, curl=(50, 0, 0)))
    k(a, imp + 0.3, 'out', body(lean=13, head=10, jaw=-30, flare=-0.1, extra=claws(0.4)), arm_=hand(tgt, side, curl=(50, 0, 0)))
    k(a, total, 'smooth', {})
slam('slam_r', 'r'); slam('slam_l', 'l')
# --- SWEEP
def sweep(name, side):
    a = A(name, 3.4); r0, r1, h = blk(11.0), blk(11.0), 45
    k(a, 0)
    k(a, 0.9, 'smooth', body(lean=-6, turn=(20 if side == 'r' else -20), jaw=8, flare=0.3, extra=claws(0)), arm_=hand(ring(-25, blk(7), h + 40, side), side))
    for i in range(0, 11):
        t = 1.0 + i * 0.1; psi = -15 + (150 + 15) * ((i / 10) ** 1.0)
        k(a, t, 'linear', body(lean=5, turn=(20 - 40 * i / 10) * (1 if side == 'r' else -1), jaw=-10, flare=0.2, extra=claws(0)), arm_=hand(ring(psi, blk(11.5), h, side), side, curl=(30, 0, 0)))
    k(a, 2.5, 'out', body(lean=2, extra=claws(0)), arm_=hand(ring(150, blk(7), 90, side), side, w=0.6))
    k(a, 3.4, 'smooth', {})
sweep('sweep_r', 'r'); sweep('sweep_l', 'l')
# --- GRAB (arrastre por la lengua)
def grab(name, side):
    a = A(name, 2.4); tgt = ground(9.1, 1.85, 18, side); hi = np.array([(-1 if side == 'r' else 1) * 30, 210, blk(7)])
    k(a, 0); k(a, 0.5, 'smooth', body(lean=6, jaw=6, flare=0.3, extra=claws(0)), arm_=hand(hi, side, curl=(20, 0, 0)))
    k(a, 1.0, 'snap', body(lean=15, head=10, jaw=-35, extra=claws(1)), arm_=hand(tgt, side, curl=(45, 0, 0)))
    k(a, 1.35, 'smooth', body(lean=8, extra=claws(1)), arm_=hand(hi + np.array([0, 20, 0]), side))
    k(a, 1.8, 'snap', body(lean=18, jaw=-20, extra=claws(0.2)), arm_=hand(np.array([0, 160, blk(14)]), side))
    k(a, 2.4, 'smooth', {})
grab('grab_r', 'r'); grab('grab_l', 'l')
# --- RAICES, JARDIN, ENREDADERA
a = A('roots_cast', 2.4); k(a, 0)
k(a, 0.9, 'smooth', body(lean=-14, head=-10, jaw=12, flare=0.8, flor=6, extra=claws(0)), arm_=hand(np.array([-60, 300, -20]), 'r'))
k(a, 1.3, 'snap', body(lean=14, head=12, jaw=-35, flare=-0.1, extra=claws(0.5)), arm_=hand(ground(5.5, 4.0, 12), 'r', curl=(45, 0, 0)))
k(a, 1.8, 'smooth', body(lean=10, jaw=-20, flare=0.1), arm_=hand(ground(5.5, 4.0, 12), 'r', curl=(45, 0, 0))); k(a, 2.4, 'smooth', {})
a = A('garden_cast', 3.0); k(a, 0)
k(a, 1.0, 'smooth', body(lean=-10, head=-18, jaw=18, flare=1.0, flor=8, leaf=1.5, extra=claws(0)), arm_=hand(np.array([-45, 360, 30]), 'r'))
k(a, 1.7, 'smooth', body(lean=-12, head=-20, jaw=20, flare=1.2, flor=-8, leaf=-1.5, extra=claws(0)), arm_=hand(np.array([-45, 380, 30]), 'r'))
k(a, 1.8, 'snap', body(lean=8, head=6, jaw=-10, flare=1.4, flor=10, leaf=2), arm_=hand(ground(6, 3, 30), 'r')); k(a, 2.4, 'out', body(flare=0.8), arm_=hand(ground(6, 3, 30), 'r', w=0.5)); k(a, 3.0, 'smooth', {})
a = A('vine_cast', 2.6); k(a, 0)
k(a, 1.0, 'smooth', body(lean=-16, head=-22, jaw=16, flare=0.9, flor=10, extra=claws(0)), arm_=hand(np.array([-40, 260, -30]), 'r'))
k(a, 1.5, 'snap', body(lean=12, head=8, jaw=-20, flare=0.4), arm_=hand(np.array([-10, 230, blk(10)]), 'r', curl=(-20, 0, 0)))
k(a, 2.0, 'out', body(lean=6, flare=0.3), arm_=hand(np.array([-10, 230, blk(10)]), 'r', w=0.4)); k(a, 2.6, 'smooth', {})
# --- HURT (aditiva), STUN
a = A('hurt', 0.6); k(a, 0); k(a, 0.12, 'out', body(lean=-7, head=-9, jaw=8, flare=0.5, extra={'brazo': (0, 0, -6)})); k(a, 0.6, 'smooth', {})
a = A('stun', 3.0, 'loop')
def stun_fn(t):
    p = Pose(); w = 2 * math.pi / 3.0; s = math.sin(w * t)
    r = body(lean=20 + 2 * s, head=26 + 3 * s, jaw=-12 + 3 * s, flare=-0.6, flor=4 * s, leaf=-1)
    r['brazo'] = (10, 0, 8); r['antebrazo'] = (12, 0, 0)
    for i in range(1, 6): r[f'lengua_{i}'] = (10 + 3 * math.sin(w * t + i), 0, 0)
    p.rot = {n: np.array(v, float) for n, v in r.items()}; return p
a.fn = stun_fn
# --- FURY / RAGE / DEATH / SPAWN / TAUNT
def roar_keys(a, t0, imp, fwd=12.48, sd=0.0):
    k(a, t0, 'smooth', {}); k(a, imp - 0.5, 'smooth', body(lean=-18, head=-24, jaw=22, flare=1.3, flor=9, leaf=2, tongue=20, extra=claws(0)), arm_=hand(np.array([-45, 340, 0]), 'r'))
    k(a, imp, 'snap', body(lean=14, head=12, jaw=-30, flare=0.2), arm_=hand(ground(fwd / 1.0 * 0.9, sd + 1.8, 14), 'r', curl=(45, 0, 0)))
a = A('fury', 3.0); roar_keys(a, 0, 1.55); k(a, 2.2, 'out', body(lean=6, flare=0.5), arm_=hand(ground(11, 1.8, 14), 'r', w=0.6)); k(a, 3.0, 'smooth', {})
a = A('rage', 4.0); k(a, 0)
k(a, 1.0, 'smooth', body(lean=-20, head=-26, jaw=24, flare=1.4, flor=10, leaf=2, extra=claws(0)), arm_=hand(np.array([-45, 350, 0]), 'r'))
for t, sd in ((1.2, 0), (2.0, 2), (2.8, 0)):
    k(a, t, 'snap', body(lean=18, head=14, jaw=-35, flare=0.1, extra=claws(0.5)), arm_=hand(ground(11.5, 1.8 + sd, 14), 'r', curl=(45, 0, 0)))
    k(a, t + 0.35, 'smooth', body(lean=-12, head=-14, jaw=14, flare=1.0, extra=claws(0)), arm_=hand(np.array([-45, 330, 0]), 'r'))
k(a, 4.0, 'smooth', {})
a = A('death', 5.0, 'hold'); k(a, 0)
k(a, 0.8, 'out', body(lean=-12, head=-20, jaw=20, flare=1.2, extra=claws(0)), arm_=hand(np.array([-45, 320, 0]), 'r'))
k(a, 2.5, 'in', body(lean=28, head=35, jaw=-35, flare=-1.0, flor=6, leaf=-2, extra=merge(claws(0.3))), pos={'raiz': (0, -40, 0)}, arm_=hand(ground(7, 3, 8), 'r', w=0.7))
k(a, 5.0, 'out', body(lean=45, head=55, jaw=-45, flare=-1.4, flor=0, leaf=-3, extra=merge(claws(0.3))), pos={'raiz': (0, -130, 0)}, arm_=hand(ground(8, 3, 4), 'r', w=0.7))
a = A('spawn', 10.0, 'hold'); k(a, 0, 'smooth', body(flare=-1.3, lean=10, head=20, jaw=-50, extra=claws(0.6)), pos={'raiz': (0, -330, 0)})
k(a, 3.5, 'smooth', body(flare=-1.3, lean=10, head=20, jaw=-50, extra=claws(0.6)), pos={'raiz': (0, -330, 0)})
k(a, 6.0, 'smooth', body(flare=-1.0, lean=8, head=16, jaw=-45, extra=claws(0.6)), pos={'raiz': (0, -120, 0)})
k(a, 7.4, 'smooth', body(flare=-0.4, lean=-6, head=-8, jaw=-20, extra=claws(0.2)), pos={'raiz': (0, -8, 0)}, arm_=hand(np.array([-45, 320, 20]), 'r'))
k(a, 7.9, 'in', body(lean=-12, head=-14, jaw=10, flare=0.4, extra=claws(0)), arm_=hand(np.array([-45, 340, 0]), 'r'))
k(a, 8.0, 'snap', body(lean=15, head=14, jaw=-30, flare=0.1, extra=claws(0.4)), arm_=hand(ground(10.9, 1.8, 14), 'r', curl=(45, 0, 0)))
k(a, 8.6, 'smooth', body(lean=-18, head=-24, jaw=22, flare=1.3, flor=9, leaf=2, tongue=20, extra=claws(0)), arm_=hand(np.array([-45, 330, 0]), 'r'))
k(a, 9.5, 'smooth', body(lean=0, flare=0.2, jaw=8), arm_=hand(np.array([-45, 250, 20]), 'r', w=0.3)); k(a, 10.0, 'smooth', {})
a = A('taunt', 4.0); k(a, 0)
k(a, 1.2, 'smooth', body(lean=-20, head=-26, jaw=22, flare=1.3, flor=9, leaf=2, tongue=15, extra=claws(0)), arm_=hand(np.array([-50, 345, 10]), 'r'))
k(a, 2.2, 'snap', body(lean=16, head=16, jaw=-35, flare=1.5, flor=-10, leaf=-2), arm_=hand(ground(10, 2, 14), 'r', curl=(45, 0, 0)))
k(a, 3.0, 'out', body(lean=4, flare=0.6, jaw=14), arm_=hand(ground(10, 2, 14), 'r', w=0.4)); k(a, 4.0, 'smooth', {})
a = A('slam_both', 3.0); k(a, 0)
k(a, 1.2, 'smooth', body(lean=-20, head=-24, jaw=22, flare=1.1, flor=8, extra=claws(0)), arm_=hand(np.array([-30, 360, -10]), 'r'))
k(a, 1.6, 'smooth', body(lean=-24, head=-28, jaw=24, flare=1.2, extra=claws(0)), arm_=hand(np.array([-30, 375, -20]), 'r'))
k(a, 1.7, 'snap', body(lean=24, head=30, jaw=-45, flare=-0.6, extra=claws(0.5)), arm_=hand(ground(13.0, 0.5, 14), 'r', curl=(50, 0, 0)))
k(a, 2.3, 'out', body(lean=18, head=22, jaw=-35, flare=-0.3), arm_=hand(ground(13.0, 0.5, 14), 'r', curl=(50, 0, 0))); k(a, 3.0, 'smooth', {})
a = A('root_wave', 3.0); k(a, 0)
k(a, 0.8, 'smooth', body(lean=-10, head=-12, jaw=12, flare=0.9, extra=claws(0)), arm_=hand(np.array([-50, 250, 20]), 'r'))
k(a, 1.2, 'snap', body(lean=12, head=10, jaw=-25, flare=0.2, extra=claws(0.5)), arm_=hand(ground(7.5, 2.5, 12), 'r', curl=(45, 0, 0)))
k(a, 2.0, 'out', body(lean=10, jaw=-15), arm_=hand(ground(7.5, 2.5, 12), 'r', curl=(45, 0, 0))); k(a, 3.0, 'smooth', {})
a = A('cyclone', 4.6); k(a, 0)
k(a, 0.9, 'smooth', body(lean=-4, jaw=10, flare=0.5, extra=claws(0)), arm_=hand(ring(-28, blk(9), 60, 'r'), 'r'))
N = 44
for i in range(N + 1):
    t = 1.0 + 2.2 * i / N; psi = -28 + 748 * (i / N)
    tg = Ry(psi + 28) @ np.array([-blk(10.5), 50, 0])
    k(a, t, 'linear', body(lean=6, turn=psi * 0.0, jaw=-10, flare=0.5 + 0.3 * math.sin(i), extra=merge(claws(0), {'tronco': (2, psi + 28, 0)})), arm_=hand(tg + np.array([0, 0, 0]), 'r', pole=(0, 1, 0)))
k(a, 3.9, 'out', body(lean=2), arm_=hand(ring(10, blk(8), 80, 'r'), 'r', w=0.4)); k(a, 4.6, 'smooth', {})
a = A('devour', 4.0); k(a, 0)
k(a, 1.0, 'smooth', body(lean=-16, head=-20, jaw=24, flare=1.3, tongue=10, extra=claws(0.8)), arm_=hand(ground(5, 5, 14), 'r', w=0.8))
k(a, 3.0, 'smooth', body(lean=-22, head=-26, jaw=26, flare=1.5, extra=claws(0.8)), arm_=hand(ground(5, 5, 14), 'r', w=0.8))
k(a, 3.1, 'snap', body(lean=26, head=22, jaw=-60, flare=-0.4, extra=claws(0.8)), arm_=hand(ground(5, 5, 14), 'r', w=0.8)); k(a, 3.6, 'out', body(lean=10, jaw=-30), arm_=hand(ground(5, 5, 14), 'r', w=0.4)); k(a, 4.0, 'smooth', {})
a = A('thorn_rain', 3.6); k(a, 0)
for t in (0.9, 1.4, 1.9, 2.4):
    k(a, t, 'smooth', body(lean=-16, head=-22, jaw=20, flare=1.2, flor=9, extra=claws(0)), arm_=hand(np.array([-45, 360, 0]), 'r'))
    k(a, t + 0.25, 'smooth', body(lean=-8, head=-12, jaw=10, flare=0.8, flor=-9, extra=claws(0)), arm_=hand(np.array([-45, 330, 20]), 'r'))
k(a, 3.6, 'smooth', {})

# ======================================================================== NUEVOS ATAQUES
GRAB_P = ground(9.0, 0.0, 16)                  # punto de captura (centro de la zona roja) - debe coincidir con GripAttack
for nm, slams in (('grip_throw', 0), ('grip_slam', 2)):
    a = A(nm, 3.9); hi = np.array([-20, 235, blk(6.5)])
    k(a, 0)
    k(a, 0.8, 'smooth', body(lean=-10, head=-10, jaw=10, flare=0.7, extra=claws(0)), arm_=hand(np.array([-55, 300, -30]), 'r', curl=(-20, 0, 0)))
    k(a, 1.15, 'snap', body(lean=18, head=14, jaw=-30, flare=0.1, extra=claws(0.2)), arm_=hand(GRAB_P, 'r', curl=(45, 0, 0)))
    k(a, 1.35, 'smooth', body(lean=14, head=10, jaw=-30, extra=claws(1)), arm_=hand(GRAB_P + np.array([0, 4, 0]), 'r', curl=(45, 0, 0)))
    k(a, 1.8, 'smooth', body(lean=-6, head=-4, jaw=-20, extra=claws(1)), arm_=hand(hi, 'r'))
    if slams == 0:
        for j, t in enumerate((2.0, 2.2, 2.4)):                     # sacudidas (dano periodico)
            k(a, t, 'smooth', body(lean=-6 + 3 * (j % 2), extra=claws(1)), arm_=hand(hi + np.array([0, 14 * (1 if j % 2 else -1), 0]), 'r'))
        k(a, 2.55, 'smooth', body(lean=-16, head=-18, jaw=18, flare=0.8, extra=claws(1)), arm_=hand(np.array([-45, 290, -30]), 'r'))
        k(a, 2.8, 'snap', body(lean=22, head=22, jaw=-35, flare=0.4, extra=claws(0)), arm_=hand(np.array([0, 210, blk(15)]), 'r', curl=(-20, 0, 0)))
        k(a, 3.2, 'out', body(lean=8, flare=0.3, extra=claws(0)), arm_=hand(np.array([0, 190, blk(15)]), 'r', w=0.5))
    else:
        for j, t in enumerate((2.05, 2.85)):
            k(a, t - 0.35, 'smooth', body(lean=-14, head=-16, jaw=16, flare=0.8, extra=claws(1)), arm_=hand(np.array([-30, 330, 30]), 'r'))
            k(a, t, 'snap', body(lean=24, head=26, jaw=-50, flare=-0.5, extra=claws(1)), arm_=hand(ground(10.5, 0.0, 14), 'r', curl=(50, 0, 0)))
        k(a, 3.15, 'smooth', body(lean=-12, jaw=12, flare=0.6, extra=claws(1)), arm_=hand(np.array([-45, 290, 20]), 'r'))
        k(a, 3.35, 'snap', body(lean=16, jaw=-25, extra=claws(0)), arm_=hand(np.array([-120, 150, blk(9)]), 'r', curl=(-20, 0, 0)))
    k(a, 3.9, 'smooth', {})
a = A('poison_spit', 3.5); k(a, 0)
k(a, 0.9, 'smooth', body(lean=-14, head=-22, jaw=22, flare=1.0, flor=6, extra=claws(0)), arm_=hand(np.array([-50, 150, 80]), 'r', w=0.8))
for t in (1.0, 1.3, 1.6, 1.9, 2.2):
    k(a, t, 'snap', body(lean=8, head=18, jaw=26, flare=0.3, extra=claws(0)), arm_=hand(np.array([-50, 150, 80]), 'r', w=0.8))
    k(a, t + 0.15, 'smooth', body(lean=-6, head=-10, jaw=18, flare=0.8, extra=claws(0)), arm_=hand(np.array([-50, 150, 80]), 'r', w=0.8))
k(a, 3.5, 'smooth', {})
a = A('poison_cloud', 3.2); k(a, 0)
k(a, 0.9, 'smooth', body(lean=12, head=16, jaw=-30, flare=-0.6, extra=claws(0.7)), arm_=hand(ground(3.5, 6, 10), 'r', w=0.9))
k(a, 1.2, 'snap', body(lean=-12, head=-22, jaw=26, flare=1.6, flor=10, leaf=2, extra=claws(0)), arm_=hand(ground(3.5, 6, 10), 'r', w=0.9))
k(a, 2.2, 'smooth', body(lean=-6, head=-12, jaw=18, flare=1.3, flor=-10, leaf=-2), arm_=hand(ground(3.5, 6, 10), 'r', w=0.5)); k(a, 3.2, 'smooth', {})
a = A('poison_breath', 4.0); k(a, 0)
k(a, 0.7, 'smooth', body(lean=10, head=22, jaw=24, flare=0.8, turn=38, extra=claws(0.4)), arm_=hand(ground(6, 5, 12), 'r', w=0.7))
for i in range(0, 12):
    t = 0.8 + 2.2 * i / 11; th = 40 - 80 * i / 11
    k(a, t, 'linear', body(lean=12, head=22 + 2 * math.sin(i * 2), jaw=26, flare=0.9, turn=th, extra=claws(0.4)), arm_=hand(ground(6, 5, 12), 'r', w=0.7))
k(a, 3.4, 'smooth', body(lean=4), arm_=hand(ground(6, 5, 12), 'r', w=0.3)); k(a, 4.0, 'smooth', {})
a = A('venom_rain', 3.6); k(a, 0)
for t in (0.8, 1.3, 1.8, 2.3):
    k(a, t - 0.3, 'smooth', body(lean=-22, head=-34, jaw=26, flare=1.3, flor=9, extra=claws(0)), arm_=hand(np.array([-50, 350, 10]), 'r'))
    k(a, t, 'snap', body(lean=6, head=-10, jaw=22, flare=0.7, flor=-9, extra=claws(0)), arm_=hand(np.array([-50, 320, 10]), 'r'))
k(a, 3.6, 'smooth', {})
