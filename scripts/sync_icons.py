#!/usr/bin/env python3
"""Кладёт иконку приложения в ресурсы Android из ./assets (замена android_icons.py веб-версии).

Исходники картинок делает scripts/make-icons.py (Pillow + numpy). Нужен только Pillow:
    python3 scripts/make-icons.py && python3 scripts/sync_icons.py
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"


def save(src: str, dst: str, size: int) -> None:
    path = ROOT / "assets" / src
    if not path.exists():
        raise SystemExit(f"Нет {path}. Сначала: python3 scripts/make-icons.py")
    out = RES / dst
    out.parent.mkdir(parents=True, exist_ok=True)
    Image.open(path).convert("RGBA").resize((size, size), Image.LANCZOS).save(out, optimize=True)
    print("✔", out.relative_to(ROOT))


# адаптивная иконка: 108dp → 432px на xxxhdpi
save("icon-foreground.png", "drawable-nodpi/ic_launcher_foreground.png", 432)
save("icon-background.png", "drawable-nodpi/ic_launcher_background.png", 432)
save("icon-monochrome.png", "drawable-nodpi/ic_launcher_monochrome.png", 432)
# запасная иконка для Android 7 (minSdk 24)
save("icon-only.png", "mipmap-xxxhdpi/ic_launcher.png", 192)
