#!/usr/bin/env python3
"""Genera texturas del mod a partir de tools/source/*.png"""
import os, colorsys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
TEX = os.path.join(ROOT, "src/main/resources/assets/qumara/textures")
os.makedirs(os.path.join(TEX, "entity"), exist_ok=True)
os.makedirs(os.path.join(TEX, "gui"), exist_ok=True)

# ---- Entidad -----------------------------------------------------------------------------------------------
base = Image.open(os.path.join(HERE, "source", "kumara.png")).convert("RGBA")
# (desactivado) base.save(os.path.join(TEX, "entity", "qumara.png"))  <- ahora lo genera tools/build_model.py

# Variante de furia: verdes -> ocres/rojizos, morados -> carmesi, mas saturacion y brillo
fury = base.copy(); px = fury.load()
for y in range(fury.height):
    for x in range(fury.width):
        r, g, b, a = px[x, y]
        if a == 0: continue
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        hd = h * 360
        if 70 <= hd <= 170: hd = 30 + (hd - 70) * 0.15          # verdes -> naranja/ocre
        elif 250 <= hd <= 310: hd = 345 + (hd - 250) * 0.05      # morados -> carmesi
        s = min(1.0, s * 1.25 + 0.05); v = min(1.0, v * 1.08 + 0.02)
        nr, ng, nb = colorsys.hsv_to_rgb((hd % 360) / 360, s, v)
        px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
# (desactivado) fury.save(os.path.join(TEX, "entity", "qumara_fury.png"))  <- ahora lo genera tools/build_model.py

# ---- BossBar: se separa en capa 'overlay' (marco, enredaderas, icono) y capa 'fill' (relleno rosa) -------------------
src = Image.open(os.path.join(HERE, "source", "bossbar_source.png")).convert("RGBA")
bb = src.crop(src.getbbox())
TARGET_W = 724
bb = bb.resize((TARGET_W, round(bb.height * TARGET_W / bb.width)), Image.LANCZOS)
W, H = bb.size
px = bb.load()
overlay = bb.copy(); fill = Image.new("RGBA", (W, H), (0, 0, 0, 0))
op, fp = overlay.load(), fill.load()
bar_x0 = int(W * 0.25)                       # el icono queda a la izquierda; la barra empieza ~25 %
cand = []
rows = {}
for y in range(H):
    for x in range(bar_x0, W):
        r, g, b, a = px[x, y]
        if a < 40: continue
        h, s_, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        if 0.82 <= h <= 0.96 and s_ > 0.30 and v > 0.55:
            cand.append((x, y, (r, g, b))); rows[y] = rows.get(y, 0) + 1
mx = max(rows.values())
bar_rows = [y for y, c in rows.items() if c > 0.35 * mx]
y0, y1 = min(bar_rows) - 1, max(bar_rows) + 1          # solo las filas de la barra (los capullos quedan en el overlay)
for x, y, (r, g, b) in cand:
    if y0 <= y <= y1:
        fp[x, y] = (r, g, b, 255); op[x, y] = (0, 0, 0, 0)
# rellena huecos de 1-2 px para que la barra sea continua
from PIL import ImageFilter
alpha = fill.split()[3].filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.MinFilter(3))
fill.putalpha(alpha)
# donde el relleno cubre, el overlay queda transparente (las enredaderas siguen encima porque no son rosa)
overlay.save(os.path.join(TEX, "gui", "bossbar_overlay.png"))
fill.save(os.path.join(TEX, "gui", "bossbar_fill.png"))
xs = [x for x in range(bar_x0, W) if fill.getpixel((x, H // 2))[3] > 0] or [bar_x0, W - 1]
info = {"w": W, "h": H, "fill_x0": min(xs), "fill_x1": max(xs)}
open(os.path.join(HERE, "out", "bossbar_info.txt"), "w").write(repr(info))
print("bossbar", info)

# ---- Icono de huevo de aparicion (16x16) ---------------------------------------------------------------------------
os.makedirs(os.path.join(TEX, "item"), exist_ok=True)
icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); ip = icon.load()
for y in range(16):
    for x in range(16):
        dx, dy = (x - 7.5) / 6.5, (y - 8.5) / 7.5
        if dx * dx + dy * dy <= 1:
            ip[x, y] = (214, 70, 160, 255) if (x + y) % 5 else (98, 160, 52, 255)
icon.save(os.path.join(TEX, "item", "qumara_spawn_egg.png"))
print("ok")
