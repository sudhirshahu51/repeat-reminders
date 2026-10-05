"""Generates the Play Store icon (512x512) and feature graphic (1024x500). Run: uv run --with pillow make_graphics.py"""
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).parent
TOP, BOTTOM = (79, 99, 214), (40, 53, 147)


def gradient(w, h):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / (h - 1)
        d.line([(0, y), (w, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM)))
    return img


def draw_bell(d, cx, cy, r, s=1.0):
    w = max(2, round(r * 0.13))
    # loop arrow around the bell: an open ring with an arrowhead at the clockwise end
    ar, aw = r * 1.3, round(w * 0.8)
    start, stop = -50, 250
    d.arc([cx - ar, cy - ar, cx + ar, cy + ar], start=start, end=stop, fill=(255, 202, 40), width=aw)
    a = math.radians(stop)
    px, py = cx + (ar - aw / 2) * math.cos(a), cy + (ar - aw / 2) * math.sin(a)
    tx, ty = -math.sin(a), math.cos(a)  # direction of travel
    nx, ny = math.cos(a), math.sin(a)  # outward normal
    h = r * 0.34
    d.polygon([(px + tx * h, py + ty * h), (px + nx * h * 0.6, py + ny * h * 0.6), (px - nx * h * 0.6, py - ny * h * 0.6)], fill=(255, 202, 40))
    # reminder bell inside the loop: knob, dome, body flaring to a rim, clapper
    kr = r * 0.12
    d.ellipse([cx - kr, cy - r * 0.86 - kr, cx + kr, cy - r * 0.86 + kr], fill="white")
    dome = r * 0.55
    dy = cy - r * 0.25
    d.pieslice([cx - dome, dy - dome, cx + dome, dy + dome], 180, 360, fill="white")
    d.polygon([(cx - dome, dy), (cx + dome, dy), (cx + r * 0.62, cy + r * 0.38), (cx - r * 0.62, cy + r * 0.38)], fill="white")
    d.rounded_rectangle([cx - r * 0.82, cy + r * 0.36, cx + r * 0.82, cy + r * 0.54], radius=r * 0.08, fill="white")
    cr = r * 0.17
    d.pieslice([cx - cr, cy + r * 0.56 - cr, cx + cr, cy + r * 0.56 + cr], 0, 180, fill=(255, 202, 40))


def font(size, bold=True):
    for name in (["segoeuib.ttf", "arialbd.ttf"] if bold else ["segoeui.ttf", "arial.ttf"]):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


icon = gradient(512, 512)
draw_bell(ImageDraw.Draw(icon), 256, 276, 150)
icon.save(OUT / "icon-512.png")

fg = gradient(1024, 500)
d = ImageDraw.Draw(fg)
draw_bell(d, 820, 260, 120)
d.text((70, 165), "Repeat Reminders", font=font(66), fill="white")
d.text((72, 260), "Repeat every few minutes,", font=font(38, False), fill=(230, 233, 255))
d.text((72, 308), "on the days you choose.", font=font(38, False), fill=(230, 233, 255))
fg.save(OUT / "feature-graphic-1024x500.png")
print("ok")
