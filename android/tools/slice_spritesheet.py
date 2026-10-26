"""Crop the approved source sheet without resampling or repainting."""
from __future__ import annotations

import csv
import hashlib
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "assets" / "spritesheet.png"
OUT = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res" / "drawable-nodpi"
WIDTH, HEIGHT, COLUMNS, ROWS = 192, 208, 8, 11

def main() -> None:
    sheet = Image.open(SOURCE).convert("RGBA")
    if sheet.size != (WIDTH * COLUMNS, HEIGHT * ROWS):
        raise SystemExit(f"Unexpected source dimensions: {sheet.size}")
    OUT.mkdir(parents=True, exist_ok=True)
    entries = []
    for row in range(ROWS):
        for col in range(COLUMNS):
            frame = sheet.crop((col * WIDTH, row * HEIGHT, (col + 1) * WIDTH, (row + 1) * HEIGHT))
            if frame.getchannel("A").getbbox() is None:
                continue
            name = f"r{row:02d}_c{col:02d}.png"
            frame.save(OUT / name, optimize=True)
            entries.append((row, col, col * WIDTH, row * HEIGHT, name, hashlib.sha256((OUT / name).read_bytes()).hexdigest()))
    with (Path(__file__).resolve().parents[1] / "frame-map.csv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(("source_row", "source_col", "x", "y", "output", "sha256"))
        writer.writerows(entries)
    print(f"source={sheet.width}x{sheet.height} rows={ROWS} columns={COLUMNS} valid_frames={len(entries)}")

if __name__ == "__main__":
    main()
