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


def draw_clock(d, cx, cy, r, s=1.0):
    w = max(2, round(r * 0.13))
    # loop arrow around the clock: an open ring with an arrowhead at the clockwise end
    ar, aw = r * 1.3, round(w * 0.8)
    start, stop = -50, 250
    d.arc([cx - ar, cy - ar, cx + ar, cy + ar], start=start, end=stop, fill=(255, 202, 40), width=aw)
    a = math.radians(stop)
    px, py = cx + (ar - aw / 2) * math.cos(a), cy + (ar - aw / 2) * math.sin(a)
    tx, ty = -math.sin(a), math.cos(a)  # direction of travel
    nx, ny = math.cos(a), math.sin(a)  # outward normal
    h = r * 0.34
    d.polygon([(px + tx * h, py + ty * h), (px + nx * h * 0.6, py + ny * h * 0.6), (px - nx * h * 0.6, py - ny * h * 0.6)], fill=(255, 202, 40))
    # bells, partly hidden behind the face
    for sx in (-1, 1):
        bx, by, br = cx + sx * r * 0.66, cy - r * 0.74, r * 0.36
        d.ellipse([bx - br, by - br, bx + br, by + br], fill="white")
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill="white")
    d.ellipse([cx - r + w, cy - r + w, cx + r - w, cy + r - w], fill=BOTTOM)
    for i in range(12):
        ang = math.radians(i * 30)
        o, inn = r - w * 1.6, r - w * (2.6 if i % 3 == 0 else 2.1)
        d.line([(cx + inn * math.sin(ang), cy - inn * math.cos(ang)), (cx + o * math.sin(ang), cy - o * math.cos(ang))], fill="white", width=max(2, w // 3))
    d.line([(cx, cy), (cx, cy - r * 0.55)], fill="white", width=w)
    d.line([(cx, cy), (cx + r * 0.42, cy + r * 0.2)], fill="white", width=w)
    d.ellipse([cx - w, cy - w, cx + w, cy + w], fill=(255, 202, 40))


def font(size, bold=True):
    for name in (["segoeuib.ttf", "arialbd.ttf"] if bold else ["segoeui.ttf", "arial.ttf"]):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


icon = gradient(512, 512)
draw_clock(ImageDraw.Draw(icon), 256, 276, 150)
icon.save(OUT / "icon-512.png")

fg = gradient(1024, 500)
d = ImageDraw.Draw(fg)
draw_clock(d, 820, 260, 120)
d.text((70, 165), "Repeat Reminders", font=font(66), fill="white")
d.text((72, 260), "Repeat every few minutes,", font=font(38, False), fill=(230, 233, 255))
d.text((72, 308), "on the days you choose.", font=font(38, False), fill=(230, 233, 255))
fg.save(OUT / "feature-graphic-1024x500.png")
print("ok")
