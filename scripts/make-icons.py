#!/usr/bin/env python3
"""Иконка и заставка Lexa из логотипа DataLaw DesignLab «D» (resources/logo-source.png).

Знак приложения: фирменная «D» + раскрытая книга (словарь) под ней — в той же манере,
что и у других приложений DataLaw (Forma: «D» + гантель).

Выход:
  assets/{icon-only,icon-foreground,icon-monochrome,icon-background,splash,splash-dark}.png  (для scripts/android_icons.py)
  public/logo-mask.png        белая «D» на прозрачном (маска знака в интерфейсе)
  store/play-icon-512.png     иконка для Google Play

Запуск:  python3 scripts/make-icons.py      (нужны Pillow и numpy)
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "resources" / "logo-source.png"
BG = (10, 10, 11)
CREAM_TOP = (246, 236, 220)
CREAM_BOT = (201, 162, 114)
SPLASH_LIGHT = (236, 220, 196)  # --bg светлой темы
SS = 4  # суперсэмплинг книги


def smooth(x):
    x = np.clip(x, 0, 1)
    return x * x * (3 - 2 * x)


def load_mask() -> Image.Image:
    """Яркость → прозрачность: тёмная «D» на светлой бумаге становится непрозрачной формой."""
    a = np.asarray(Image.open(SRC).convert("L"), dtype=np.float32)
    alpha = 1.0 - smooth((a - 120.0) / 70.0)
    m = Image.fromarray((alpha * 255).astype(np.uint8), "L")
    return m.crop(m.point(lambda v: 255 if v > 128 else 0).getbbox())


GLYPH = load_mask()


def fit(img: Image.Image, w: float, h: float) -> Image.Image:
    s = min(w / img.width, h / img.height)
    return img.resize((max(1, round(img.width * s)), max(1, round(img.height * s))), Image.LANCZOS)


def gradient(size: tuple[int, int]) -> Image.Image:
    w, h = size
    t = np.linspace(0, 1, h, dtype=np.float32)[:, None, None]
    top = np.array(CREAM_TOP, dtype=np.float32)[None, None, :]
    bot = np.array(CREAM_BOT, dtype=np.float32)[None, None, :]
    return Image.fromarray(((top * (1 - t) + bot * t) * np.ones((h, w, 1), np.float32)).astype(np.uint8), "RGB")


def painted(mask: Image.Image, mode: str) -> Image.Image:
    fill = Image.new("RGB", mask.size, (255, 255, 255)) if mode == "white" else gradient(mask.size)
    fill = fill.convert("RGBA")
    fill.putalpha(mask)
    return fill


def bezier(p0, p1, p2, n=28):
    return [
        ((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0], (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1])
        for t in (i / n for i in range(n + 1))
    ]


def book_mask(canvas: int, cx: float, cy: float, w: float, h: float) -> Image.Image:
    """Раскрытая книга: две страницы с изгибом, между ними щель-корешок. Центр (cx, cy), размер w×h."""
    big = canvas * SS
    m = Image.new("L", (big, big), 0)
    d = ImageDraw.Draw(m)

    def pt(x, y):  # x∈[-1,1], y∈[0,1] → пиксели суперсэмпла
        return ((cx + x * w / 2) * SS, (cy - h / 2 + y * h) * SS)

    for sgn in (-1, 1):
        top = bezier(pt(sgn * 0.05, 0.16), pt(sgn * 0.45, -0.16), pt(sgn * 1.0, 0.02))
        bot = bezier(pt(sgn * 1.0, 0.80), pt(sgn * 0.45, 0.52), pt(sgn * 0.05, 0.92))
        d.polygon(top + bot, fill=255)
    return m.resize((canvas, canvas), Image.LANCZOS)


def mark(canvas: int, frac: float, mode: str = "color") -> Image.Image:
    """Знак: «D» сверху и книга под ней, всё вместе занимает frac холста по высоте."""
    box = canvas * frac
    y0 = (canvas - box) / 2
    layer = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    g = fit(GLYPH, box * 0.70, box * 0.70)
    gl = painted(g, mode)
    layer.alpha_composite(gl, (round((canvas - g.width) / 2), round(y0 + 0.35 * box - g.height / 2)))
    bm = book_mask(canvas, canvas / 2, y0 + 0.865 * box, box * 0.74, box * 0.27)
    fill = painted(Image.new("L", (canvas, canvas), 255), mode)
    fill.putalpha(bm)
    layer.alpha_composite(fill)
    return layer


def rounded(img: Image.Image, r: float = 0.22) -> Image.Image:
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, img.width - 1, img.height - 1), radius=int(img.width * r), fill=255)
    out = img.copy()
    out.putalpha(m)
    return out


def main() -> None:
    out = ROOT / "assets"
    out.mkdir(exist_ok=True)
    S = 1024

    icon = Image.new("RGBA", (S, S), BG + (255,))
    icon.alpha_composite(mark(S, 0.62))
    icon.save(out / "icon-only.png")

    # adaptive: холст 108dp, безопасная зона ≈ 61% → знак ~54%
    mark(S, 0.54).save(out / "icon-foreground.png")
    mark(S, 0.54, "white").save(out / "icon-monochrome.png")
    Image.new("RGBA", (S, S), BG + (255,)).save(out / "icon-background.png")

    for name, bg in (("splash.png", SPLASH_LIGHT), ("splash-dark.png", (0, 0, 0))):
        sp = Image.new("RGBA", (2732, 2732), bg + (255,))
        sp.alpha_composite(mark(2732, 0.30, "color" if bg == (0, 0, 0) else "color"))
        sp.save(out / name)

    # в интерфейсе знак — маска, цвет задаёт тема
    (ROOT / "public").mkdir(exist_ok=True)
    g = fit(GLYPH, 512, 512)
    mask = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    white = Image.new("RGBA", g.size, (255, 255, 255, 255))
    white.putalpha(g)
    mask.alpha_composite(white, ((512 - g.width) // 2, (512 - g.height) // 2))
    mask.save(ROOT / "public" / "logo-mask.png", optimize=True)

    (ROOT / "store").mkdir(exist_ok=True)
    play = Image.new("RGB", (S, S), BG)
    play.paste(mark(S, 0.60), (0, 0), mark(S, 0.60))
    play.resize((512, 512), Image.LANCZOS).save(ROOT / "store" / "play-icon-512.png", optimize=True)

    # превью для людей
    sheet = Image.new("RGB", (1000, 300), (40, 40, 44))
    sq = rounded(icon.resize((192, 192), Image.LANCZOS))
    sheet.paste(sq, (30, 30), sq)
    rd = icon.resize((192, 192), Image.LANCZOS)
    rm = Image.new("L", (192, 192), 0)
    ImageDraw.Draw(rm).ellipse((0, 0, 191, 191), fill=255)
    sheet.paste(rd, (260, 30), rm)
    sheet.paste(Image.open(ROOT / "store" / "play-icon-512.png").resize((240, 240)), (490, 30))
    mono = Image.new("RGB", (192, 192), (30, 30, 34))
    m = mark(192, 0.54, "white")
    mono.paste(m, (0, 0), m)
    sheet.paste(mono, (770, 30))
    sheet.save(ROOT / "resources" / "preview.png")
    print("assets →", out)


if __name__ == "__main__":
    main()
