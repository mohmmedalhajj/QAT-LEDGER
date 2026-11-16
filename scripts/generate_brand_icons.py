#!/usr/bin/env python3
"""Generates QAT LEDGER (ميزان القات) brand emblem, adaptive icon assets, and Android mipmaps."""
import math
import os
import sys

sys.path.insert(0, "/tmp/pydeps")
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def draw_emblem(size: int, with_background: bool = True, monochrome: bool = False) -> Image.Image:
    scale = 4
    w = size * scale
    cx = w / 2.0
    u = w / 108.0  # matches Android adaptive 108dp viewport

    if monochrome or not with_background:
        img = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    else:
        # Smooth continuous radial emerald background (#195C45 center -> #0E382A edge)
        img = Image.new("RGBA", (w, w), (14, 56, 42, 255))
        bg = ImageDraw.Draw(img)
        max_r = w * 0.78
        steps = 160
        for step in range(steps, 0, -1):
            r = max_r * (step / steps)
            t = 1.0 - (step / steps)
            t = t * t * (3.0 - 2.0 * t)
            col = (
                int(14 + 13 * t),
                int(56 + 40 * t),
                int(42 + 30 * t),
                255,
            )
            bg.ellipse([cx - r, cx - r, cx + r, cx + r], fill=col)

    d = ImageDraw.Draw(img)

    if monochrome:
        ivory = (255, 255, 255, 255)
        brass = (255, 255, 255, 225)
        brass_dark = (255, 255, 255, 195)
        leaf_left = (255, 255, 255, 255)
        leaf_right = (255, 255, 255, 205)
        vein = (0, 0, 0, 0)
    else:
        ivory = (246, 244, 239, 255)       # #F6F4EF warm parchment
        brass = (212, 162, 67, 255)        # #D4A243 Yemeni brass gold
        brass_dark = (176, 128, 43, 255)   # #B0802B
        leaf_left = (168, 234, 196, 255)   # #A8EAC4 mint leaf
        leaf_right = (43, 182, 115, 255)   # #2BB673 emerald leaf
        vein = (18, 72, 54, 255)

    # All coordinates strictly inside safe zone [20..88] of 108dp
    # 1. Crown botanical leaf (drawn before center pivot jewel so stem tucks cleanly behind jewel)
    left_pts = []
    right_pts = []
    top_y = 21.0 * u
    bot_y = 46.8 * u
    steps = 80
    for i in range(steps + 1):
        t = i / steps
        y = top_y + (bot_y - top_y) * t
        # Tapered pointed leaf profile
        width = 9.8 * u * ((math.sin(math.pi * (t ** 0.92))) ** 1.08)
        left_pts.append((cx - width, y))
        right_pts.append((cx + width, y))

    d.polygon([(cx, top_y)] + left_pts + [(cx, bot_y)], fill=leaf_left)
    d.polygon([(cx, top_y)] + list(reversed(right_pts)) + [(cx, bot_y)], fill=leaf_right)

    if not monochrome:
        d.line([(cx, top_y + 2.5 * u), (cx, bot_y)], fill=vein, width=max(1, int(1.25 * u)))
        for frac, span in [(0.34, 3.8), (0.52, 5.4), (0.70, 4.4)]:
            vy = top_y + (bot_y - top_y) * frac
            d.line([(cx, vy + 1.5 * u), (cx - span * u, vy - 1.7 * u)], fill=vein, width=max(1, int(1.0 * u)))
            d.line([(cx, vy + 1.5 * u), (cx + span * u, vy - 1.7 * u)], fill=vein, width=max(1, int(1.0 * u)))

    # 2. Pedestal base
    d.rounded_rectangle([37 * u, 79.2 * u, 71 * u, 83.2 * u], radius=1.8 * u, fill=ivory)
    d.rounded_rectangle([43.5 * u, 75.2 * u, 64.5 * u, 79.2 * u], radius=1.4 * u, fill=brass)

    # 3. Central vertical pillar & horizontal balance beam
    d.rounded_rectangle([52.2 * u, 46.5 * u, 55.8 * u, 75.6 * u], radius=1.2 * u, fill=ivory)
    d.rounded_rectangle([27.5 * u, 46.3 * u, 80.5 * u, 49.5 * u], radius=1.6 * u, fill=ivory)

    # Center brass pivot ring
    d.ellipse([51.1 * u, 45.0 * u, 56.9 * u, 50.8 * u], fill=brass)

    # 4. Left & right suspension cords + weighing pans
    for pan_cx in (31.5, 76.5):
        top_x = pan_cx * u
        top_y_c = 49.2 * u
        left_x = (pan_cx - 7.8) * u
        right_x = (pan_cx + 7.8) * u
        pan_y = 65.2 * u

        d.line([(top_x, top_y_c), (left_x + 1.3 * u, pan_y)], fill=ivory, width=max(1, int(1.45 * u)))
        d.line([(top_x, top_y_c), (right_x - 1.3 * u, pan_y)], fill=ivory, width=max(1, int(1.45 * u)))
        d.ellipse([top_x - 1.45 * u, top_y_c - 1.45 * u, top_x + 1.45 * u, top_y_c + 1.45 * u], fill=brass)

        d.rounded_rectangle([left_x, pan_y - 0.9 * u, right_x, pan_y + 1.6 * u], radius=0.8 * u, fill=ivory)
        bowl = [
            (left_x + 0.8 * u, pan_y + 1.5 * u),
            (right_x - 0.8 * u, pan_y + 1.5 * u),
            ((pan_cx + 4.8) * u, (65.2 + 4.8) * u),
            ((pan_cx - 4.8) * u, (65.2 + 4.8) * u),
        ]
        d.polygon(bowl, fill=brass)
        half_bowl = [
            (pan_cx * u, pan_y + 1.5 * u),
            (right_x - 0.8 * u, pan_y + 1.5 * u),
            ((pan_cx + 4.8) * u, (65.2 + 4.8) * u),
            (pan_cx * u, (65.2 + 4.8) * u),
        ]
        d.polygon(half_bowl, fill=brass_dark)

    return img.resize((size, size), Image.Resampling.LANCZOS)


def round_mask_image(img: Image.Image) -> Image.Image:
    w, h = img.size
    mask = Image.new("L", (w * 4, h * 4), 0)
    md = ImageDraw.Draw(mask)
    md.ellipse([0, 0, w * 4 - 1, h * 4 - 1], fill=255)
    mask_small = mask.resize((w, h), Image.Resampling.LANCZOS)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask_small)
    return out


def main():
    assets_img_dir = os.path.join(ROOT, "assets", "images")
    os.makedirs(assets_img_dir, exist_ok=True)

    store_icon = draw_emblem(1024, with_background=True, monochrome=False)
    store_icon.save(os.path.join(assets_img_dir, "store_icon_1024.png"))

    app_logo = round_mask_image(draw_emblem(512, with_background=True, monochrome=False))
    app_logo.save(os.path.join(assets_img_dir, "app_logo.png"))

    adaptive_fg = draw_emblem(432, with_background=False, monochrome=False)
    adaptive_fg.save(os.path.join(assets_img_dir, "adaptive_foreground.png"))

    adaptive_mono = draw_emblem(432, with_background=False, monochrome=True)
    adaptive_mono.save(os.path.join(assets_img_dir, "adaptive_monochrome.png"))

    res_dir = os.path.join(ROOT, "android", "app", "src", "main", "res")
    os.makedirs(os.path.join(res_dir, "drawable"), exist_ok=True)
    adaptive_fg.save(os.path.join(res_dir, "drawable", "ic_launcher_foreground.png"))
    adaptive_mono.save(os.path.join(res_dir, "drawable", "ic_launcher_monochrome.png"))

    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, px in densities.items():
        mdir = os.path.join(res_dir, folder)
        os.makedirs(mdir, exist_ok=True)
        sq = store_icon.resize((px, px), Image.Resampling.LANCZOS)
        sq.save(os.path.join(mdir, "ic_launcher.png"))
        rnd = round_mask_image(sq)
        rnd.save(os.path.join(mdir, "ic_launcher_round.png"))

    print("Polished brand icons and Android mipmap assets successfully.")


if __name__ == "__main__":
    main()
