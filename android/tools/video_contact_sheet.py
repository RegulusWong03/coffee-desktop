#!/usr/bin/env python3
"""Extract timestamped video frames and compose a contact sheet (ffmpeg + Pillow)."""
import argparse
import math
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("video", type=Path)
    ap.add_argument("output", type=Path)
    ap.add_argument("--interval", type=float, default=0.5, help="seconds between samples")
    ap.add_argument("--roi", help="crop x,y,width,height in source-video pixels")
    ap.add_argument("--columns", type=int, default=4)
    args = ap.parse_args()
    if args.interval <= 0 or args.columns <= 0:
        ap.error("--interval and --columns must be positive")
    probe = subprocess.check_output([
        "ffprobe", "-v", "error", "-show_entries", "format=duration",
        "-of", "default=noprint_wrappers=1:nokey=1", str(args.video.resolve())
    ], text=True)
    duration = float(probe.strip())
    roi = None
    if args.roi:
        try:
            roi = tuple(int(v) for v in args.roi.split(","))
            if len(roi) != 4 or min(roi[2:]) <= 0 or min(roi[:2]) < 0:
                raise ValueError()
        except ValueError:
            ap.error("--roi must be x,y,width,height with nonnegative origin and positive size")
    times = [i * args.interval for i in range(math.ceil(duration / args.interval))]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    cell_w, cell_h, label_h = (roi[2], roi[3], 30) if roi else (360, 576, 30)
    cell_w, cell_h = min(cell_w, 720), min(cell_h, 1000)
    font = ImageFont.load_default()
    with tempfile.TemporaryDirectory(prefix="coffee-video-frames-") as td:
        frames = []
        for i, t in enumerate(times):
            target = Path(td) / f"{i:05}.png"
            cmd = ["ffmpeg", "-v", "error", "-ss", f"{t:.3f}", "-i", str(args.video.resolve()), "-frames:v", "1"]
            if roi:
                cmd += ["-vf", f"crop={roi[2]}:{roi[3]}:{roi[0]}:{roi[1]},scale={cell_w}:{cell_h}:force_original_aspect_ratio=decrease"]
            else:
                cmd += ["-vf", f"scale={cell_w}:{cell_h}:force_original_aspect_ratio=decrease"]
            cmd += ["-y", str(target)]
            result = subprocess.run(cmd, check=False, capture_output=True)
            if result.returncode or not target.exists():
                if t >= duration - 0.05:
                    continue  # Some encoders cannot decode a frame at the container's final timestamp.
                raise RuntimeError(f"ffmpeg failed at t={t:.3f}s: {result.stderr.decode(errors='replace')}")
            im = Image.open(target).convert("RGB")
            tile = Image.new("RGB", (cell_w, cell_h + label_h), "white")
            tile.paste(im, ((cell_w-im.width)//2, (cell_h-im.height)//2))
            ImageDraw.Draw(tile).text((8, cell_h + 8), f"t={t:.3f}s", fill="black", font=font)
            frames.append(tile)
        rows = math.ceil(len(frames) / args.columns)
        sheet = Image.new("RGB", (args.columns*cell_w, rows*(cell_h+label_h)), "#d8d8d8")
        for i, frame in enumerate(frames):
            sheet.paste(frame, ((i % args.columns)*cell_w, (i // args.columns)*(cell_h+label_h)))
        sheet.save(args.output)
    print(f"duration={duration:.3f}s samples={len(times)} interval={args.interval:g}s output={args.output.resolve()}")


if __name__ == "__main__":
    main()
