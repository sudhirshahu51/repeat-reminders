"""Generates the Play Store icon (512x512) and feature graphic (1024x500) from the launcher icon artwork.

Run: py make_graphics.py   (needs: pip install pillow matplotlib svgpath2mpl)
"""
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from make_launcher_icons import COLOURS, render_png

OUT = Path(__file__).parent
TOP, BOTTOM = (79, 99, 214), (40, 53, 147)


def gradient(w, h):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / (h - 1)
        d.line([(0, y), (w, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM)))
    return img


def app_icon(px):
    """The default launcher icon, full square, px x px."""
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "icon.png"
        render_png(path, COLOURS["default"], px)
        return Image.open(path).convert("RGBA").copy()


def font(size, bold=True):
    for name in (["segoeuib.ttf", "arialbd.ttf"] if bold else ["segoeui.ttf", "arial.ttf"]):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


# Play applies its own rounded mask, so the store icon is the full square.
app_icon(512).convert("RGB").save(OUT / "icon-512.png")

fg = gradient(1024, 500).convert("RGBA")
tile = app_icon(330)
mask = Image.new("L", tile.size, 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, tile.width - 1, tile.height - 1], radius=72, fill=255)
fg.paste(tile, (655, 85), mask)
d = ImageDraw.Draw(fg)
d.text((70, 165), "Repeat Reminders", font=font(66), fill="white")
d.text((72, 260), "Repeat every few minutes,", font=font(38, False), fill=(230, 233, 255))
d.text((72, 308), "on the days you choose.", font=font(38, False), fill=(230, 233, 255))
fg.convert("RGB").save(OUT / "feature-graphic-1024x500.png")
print("ok")
