"""Generates the launcher icon for every colour choice, plus a preview image.

The icon is a white spiral notebook with three coloured to-do rows and tabs, a yellow reminder bell over the
bottom-right corner, on a coloured gradient. The colour choice (Settings > App icon) sets the gradient.

Writes app/src/main/res/drawable/ic_launcher_{fg,bg}_<colour>.xml, ic_launcher_monochrome.xml, ic_stat_alarm.xml
and mipmap-anydpi-v26/ic_launcher*.xml.
Run: py make_launcher_icons.py [preview.png]   (rendering needs: pip install pillow matplotlib svgpath2mpl)
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"

# Background gradient (top-left, bottom-right) per choice. "default" is orange.
COLOURS = {
    "default": ("#FFB74D", "#F4511E"),
    "indigo": ("#7986CB", "#303F9F"),
    "teal": ("#4DB6AC", "#00796B"),
    "pink": ("#F06292", "#C2185B"),
    "dark": ("#616161", "#1E1E1E"),
}
TABS = ["#26C6DA", "#FF9800", "#FFE082", "#8BC34A"]
ROWS = ["#1E88E5", "#E53935", "#FB8C00"]  # to-do bullets, top to bottom
RING = "#B0BEC5"
LINE = "#BDBDBD"

# Geometry in the 108 x 108 adaptive-icon space; the artwork stays inside the 66dp safe zone (radius 33 around 54,54).
BX, BY, BW, BH, BR = 33, 29, 42, 50, 5  # notebook page

# Reminder bell badge over the bottom-right corner: middle of the rim's bottom edge, unit size, colours.
BELL_X, BELL_Y, BELL_U = 71, 76, 1.05
BELL_FILL, BELL_EDGE = "#FFCA28", "#C77800"

# Material "notifications" bell, for the monochrome (themed icon) layer and the status-bar icon.
BELL_GLYPH = ("M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4"
              "c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z")


def rrect(x, y, w, h, r):
    return (f"M{x + r},{y} h{w - 2 * r} a{r},{r} 0 0 1 {r},{r} v{h - 2 * r} a{r},{r} 0 0 1 {-r},{r} "
            f"h{-(w - 2 * r)} a{r},{r} 0 0 1 {-r},{-r} v{-(h - 2 * r)} a{r},{r} 0 0 1 {r},{-r} z")


def circle(cx, cy, r):
    return f"M{cx},{cy - r} a{r},{r} 0 1 1 0,{2 * r} a{r},{r} 0 1 1 0,{-2 * r} z"


def bell_path(x, y, u):
    """Bell with a flared rim, knob and clapper; (x, y) is the middle of the rim's bottom edge."""
    def p(dx, dy):
        return f"{x + dx * u:.2f},{y + dy * u:.2f}"
    body = (f"M{p(-9, 0)} L{p(-9, -1.6)} C{p(-9, -3)} {p(-6.5, -3)} {p(-6.5, -5)} L{p(-6.5, -8)} "
            f"C{p(-6.5, -12.5)} {p(-3.8, -15)} {p(0, -15)} C{p(3.8, -15)} {p(6.5, -12.5)} {p(6.5, -8)} "
            f"L{p(6.5, -5)} C{p(6.5, -3)} {p(9, -3)} {p(9, -1.6)} L{p(9, 0)} Z")
    r = 1.7 * u
    knob = f"M{p(0, -15.2 - 1.7)} a{r:.2f},{r:.2f} 0 1 1 0,{2 * r:.2f} a{r:.2f},{r:.2f} 0 1 1 0,{-2 * r:.2f} Z"
    c = 2.7 * u
    clapper = f"M{p(-2.7, 0)} a{c:.2f},{c:.2f} 0 0 0 {2 * c:.2f},0 Z"
    return f"{body} {knob} {clapper}"


def background(colours):
    """(kind, d, style) for the full-bleed gradient behind the notebook."""
    return [("grad", "M0,0 h108 v108 h-108 z", (colours[0], colours[1], 0, 0, 108, 108))]


def foreground():
    """List of (kind, d, style). kind: fill | grad | stroke | badge. Same for every colour choice."""
    out = []
    # tabs peeking out on the right
    for i, c in enumerate(TABS):
        out.append(("fill", rrect(BX + BW - 4, BY + 6 + i * 8.6, 8, 7.6, 1.8), c))
    # page, slightly shaded towards the bottom
    out.append(("grad", rrect(BX, BY, BW, BH, BR), ("#FFFFFF", "#E6E6E6", BX, BY, BX, BY + BH)))
    # spiral rings through the left edge
    for i in range(8):
        out.append(("fill", rrect(BX - 4.5, BY + 4.5 + i * 5.9, 9, 2.6, 1.3), RING))
    # three to-do rows: target bullet and a line
    for i, c in enumerate(ROWS):
        cy = BY + 11 + i * 13
        cx = BX + 10.5
        out.append(("stroke", circle(cx, cy, 4.2), (1.9, c)))
        out.append(("fill", circle(cx, cy, 2.0), c))
        out.append(("stroke", f"M{cx + 8},{cy} H{BX + BW - 6}", (2.2, LINE)))
    out.append(("badge", bell_path(BELL_X, BELL_Y, BELL_U), (BELL_FILL, BELL_EDGE)))
    return out


def vector_xml(shapes, comment):
    parts = []
    for kind, d, style in shapes:
        if kind == "fill":
            parts.append(f'    <path android:fillColor="{style}" android:pathData="{d}" />')
        elif kind == "grad":
            c0, c1, x0, y0, x1, y1 = style
            parts.append(
                f'    <path android:pathData="{d}">\n'
                f'        <aapt:attr name="android:fillColor">\n'
                f'            <gradient android:type="linear" android:startX="{x0}" android:startY="{y0}" '
                f'android:endX="{x1}" android:endY="{y1}" android:startColor="{c0}" android:endColor="{c1}" />\n'
                f'        </aapt:attr>\n'
                f'    </path>'
            )
        elif kind == "badge":
            fill, edge = style
            parts.append(f'    <path android:fillColor="#FFFFFF" android:strokeColor="#FFFFFF" android:strokeWidth="3.6" '
                         f'android:strokeLineJoin="round" android:pathData="{d}" />')
            parts.append(f'    <path android:fillColor="{fill}" android:strokeColor="{edge}" android:strokeWidth="1.2" '
                         f'android:strokeLineJoin="round" android:pathData="{d}" />')
        else:
            width, colour = style
            parts.append(f'    <path android:strokeColor="{colour}" android:strokeWidth="{width}" '
                         f'android:strokeLineCap="round" android:pathData="{d}" />')
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- Generated by store/make_launcher_icons.py: {comment} -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
            + "\n".join(parts) + "\n</vector>\n")


def glyph_xml(size, scale, offset, comment):
    group = f'android:translateX="{offset}" android:translateY="{offset}" android:scaleX="{scale}" android:scaleY="{scale}"'
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- Generated by store/make_launcher_icons.py: {comment} -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size}dp" android:height="{size}dp" android:viewportWidth="{size}" android:viewportHeight="{size}">\n'
            f'    <group {group}>\n'
            f'        <path android:fillColor="#FFFFFFFF" android:pathData="{BELL_GLYPH}" />\n'
            '    </group>\n'
            '</vector>\n')


def write_resources():
    draw = RES / "drawable"
    mip = RES / "mipmap-anydpi-v26"
    (draw / "ic_launcher_fg.xml").write_text(vector_xml(foreground(), "notebook with to-do rows and a reminder bell."), encoding="utf-8")
    for name, colours in COLOURS.items():
        (draw / f"ic_launcher_bg_{name}.xml").write_text(vector_xml(background(colours), f"{name} icon background."), encoding="utf-8")
        icon = "ic_launcher" if name == "default" else f"ic_launcher_{name}"
        (mip / f"{icon}.xml").write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            f'    <background android:drawable="@drawable/ic_launcher_bg_{name}" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_fg" />\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
            '</adaptive-icon>\n', encoding="utf-8")
    (draw / "ic_launcher_monochrome.xml").write_text(
        glyph_xml(108, 2, 30, "themed-icon layer (Android 13+), tinted by the system."), encoding="utf-8")
    (draw / "ic_stat_alarm.xml").write_text(glyph_xml(24, 1, 0, "status-bar icon."), encoding="utf-8")


def render_png(path, colours, px, view=(14, 14, 94, 94), with_background=True):
    """Renders the icon (real gradients) to a px x px PNG covering [view] of the 108 space."""
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import numpy as np
    from matplotlib.colors import to_rgb
    from matplotlib.patches import PathPatch
    from svgpath2mpl import parse_path

    x0, y0, x1, y1 = view
    fig = plt.figure(figsize=(1, 1), dpi=px)
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_xlim(x0, x1); ax.set_ylim(y1, y0); ax.axis("off")
    pts_per_unit = 72 / (x1 - x0)
    shapes = (background(colours) if with_background else []) + foreground()
    n = 400
    gx, gy = np.meshgrid(np.linspace(x0, x1, n), np.linspace(y0, y1, n))
    for kind, d, style in shapes:
        p = parse_path(d)
        if kind == "fill":
            ax.add_patch(PathPatch(p, facecolor=style, lw=0))
        elif kind == "grad":
            c0, c1, sx, sy, ex, ey = style
            patch = PathPatch(p, facecolor="none", lw=0)
            ax.add_patch(patch)
            dx, dy = ex - sx, ey - sy
            t = np.clip(((gx - sx) * dx + (gy - sy) * dy) / (dx * dx + dy * dy), 0, 1)[..., None]
            a, b = np.array(to_rgb(c0)), np.array(to_rgb(c1))
            im = ax.imshow(a + (b - a) * t, extent=[x0, x1, y1, y0], interpolation="bilinear")
            im.set_clip_path(patch)
        elif kind == "badge":
            ax.add_patch(PathPatch(p, facecolor="white", edgecolor="white", lw=3.6 * pts_per_unit, joinstyle="round"))
            ax.add_patch(PathPatch(p, facecolor=style[0], edgecolor=style[1], lw=1.2 * pts_per_unit, joinstyle="round"))
        else:
            width, colour = style
            ax.add_patch(PathPatch(p, facecolor="none", edgecolor=colour, lw=width * pts_per_unit, capstyle="round"))
    ax.set_xlim(x0, x1); ax.set_ylim(y1, y0)
    fig.savefig(path, dpi=px, transparent=not with_background)
    plt.close(fig)


def preview(path):
    """All colour choices side by side, cut to the circle a Pixel launcher shows (72dp of the 108dp icon)."""
    import tempfile
    from PIL import Image, ImageDraw

    size = 220
    strip = Image.new("RGB", (size * len(COLOURS) + 20 * (len(COLOURS) + 1), size + 40), (207, 207, 207))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    with tempfile.TemporaryDirectory() as tmp:
        for i, colours in enumerate(COLOURS.values()):
            f = Path(tmp) / f"{i}.png"
            render_png(f, colours, size, view=(18, 18, 90, 90))
            strip.paste(Image.open(f).convert("RGB"), (20 + i * (size + 20), 20), mask)
    strip.save(path)


if __name__ == "__main__":
    import sys
    write_resources()
    if len(sys.argv) > 1:
        preview(sys.argv[1])
    print("ok")
