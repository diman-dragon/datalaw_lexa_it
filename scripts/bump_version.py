#!/usr/bin/env python3
"""Поднимает версию на 1 в последнем знаке (0.1.0 → 0.1.1) и versionCode на 1: version.properties."""
import re
from pathlib import Path

p = Path(__file__).resolve().parent.parent / "version.properties"
text = p.read_text(encoding="utf-8")
name = re.search(r"^versionName=(\d+)\.(\d+)\.(\d+)$", text, re.M)
code = re.search(r"^versionCode=(\d+)$", text, re.M)
if not name or not code:
    raise SystemExit("version.properties: не нашёл versionName=X.Y.Z / versionCode=N")
new_name = f"{name[1]}.{name[2]}.{int(name[3]) + 1}"
new_code = int(code[1]) + 1
text = text.replace(name[0], f"versionName={new_name}").replace(code[0], f"versionCode={new_code}")
p.write_text(text, encoding="utf-8")
print(f"{name[0].split('=')[1]} → {new_name} (versionCode {new_code})")
