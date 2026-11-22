#!/usr/bin/env python3
"""
QUMARA - pipeline del modelo organico (OBJ de un modelador profesional) -> recursos del mod.
Uso:  python3 tools/build_model.py [ruta/carnivorous_plant.obj] [celdas=90] [textura=2048]
Entrada: carnivorous_plant.obj + albedo.jpg + emission.jpg (misma carpeta). Requiere numpy, scipy, pillow.
Salida (src/main/resources/assets/qumara/):
  model/qumara.mesh   malla decimada (~47k vertices / 32k cuads) con 4 pesos de hueso por vertice
  model/qumara.qani   esqueleto + todas las animaciones (20 fps) + trayectoria de la mano
  textures/entity/qumara.png, qumara_fury.png
"""
import sys, os, numpy as np
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
from PIL import Image
Image.MAX_IMAGE_PIXELS = None
from mesh_tools import *
from skin import compute_weights, build_adj
from qumara_rig import *
import anims
from animlib import write_qani

obj = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, 'source', 'carnivorous_plant.obj')
cells = int(sys.argv[2]) if len(sys.argv) > 2 else 90
tex = int(sys.argv[3]) if len(sys.argv) > 3 else 2048
src = os.path.dirname(obj)
OUT = os.path.join(ROOT, 'src/main/resources/assets/qumara')
os.makedirs(OUT + '/model', exist_ok=True); os.makedirs(OUT + '/textures/entity', exist_ok=True)

print('1/5 leyendo OBJ...'); raw = parse_obj(obj)
print('2/5 decimando...'); m = decimate(raw, cells); P, UV, N, T = m['P'], m['UV'], m['N'], m['T']
print('   vertices', len(P), 'triangulos', len(T))
alb = Image.open(os.path.join(src, 'albedo.jpg')).convert('RGB')
emi = Image.open(os.path.join(src, 'emission.jpg')).convert('RGB') if os.path.exists(os.path.join(src, 'emission.jpg')) else None
a2 = np.asarray(alb.resize((2048, 2048))) / 255.0
col = a2[((1 - (UV[:, 1] % 1)) * 2047).astype(int), ((UV[:, 0] % 1) * 2047).astype(int)]
print('3/5 pesos de hueso...'); G, bi, bw = compute_weights(P, col, build_adj(len(P), T))
print('4/5 cuads y exportacion...'); quads = pair_quads(P, T)
write_qmsh(OUT + '/model/qumara.mesh', (P - ORIGIN) * S, UV, N, bi, bw, quads)
base = np.asarray(alb.resize((tex, tex), Image.LANCZOS)).astype(float) / 255.0
if emi is not None: base = np.clip(base + 0.6 * np.asarray(emi.resize((tex, tex), Image.LANCZOS)).astype(float) / 255.0, 0, 1)
Image.fromarray((base * 255).astype(np.uint8)).save(OUT + '/textures/entity/qumara.png', optimize=True)
fury = np.clip(base * np.array([1.35, 0.50, 0.45]) + np.array([0.06, 0.0, 0.0]), 0, 1)       # furia: tinte rojo
Image.fromarray((fury * 255).astype(np.uint8)).save(OUT + '/textures/entity/qumara_fury.png', optimize=True)
print('5/5 animaciones (%d)...' % len(anims.ANIMS)); write_qani(OUT + '/model/qumara.qani', anims.ANIMS)
np.savez(os.path.join(HERE, 'out', 'skin_debug.npz'), P=P, UV=UV, T=T, bi=bi, bw=bw) if os.path.isdir(os.path.join(HERE, 'out')) else None
print('listo ->', OUT)
