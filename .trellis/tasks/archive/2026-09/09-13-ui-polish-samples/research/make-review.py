"""由封存的原始截图生成审阅拼图；只缩放排列，不修饰或重绘界面。"""
from pathlib import Path
import argparse
import hashlib
import json
import math
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = next(p for p in Path(__file__).resolve().parents if (p / "settings.gradle.kts").is_file())
REPORTS = ROOT / "app/build/reports/ui-polish"
OUT = REPORTS / "review/final"
FONT = Path(os.environ.get("WINDIR", "C:/Windows")) / "Fonts/msyh.ttc"


def sheet(items, destination, title, columns=3, width=288):
    font = ImageFont.truetype(str(FONT), 17)
    heading = ImageFont.truetype(str(FONT), 24)
    padding, top, caption = 16, 55, 42
    heights = []
    for path, _ in items:
        with Image.open(path) as image:
            heights.append(round(image.height * width / image.width))
    row_height = max(heights) + caption + padding
    canvas = Image.new("RGB", (padding + columns * (width + padding), top + math.ceil(len(items) / columns) * row_height), "#e6e5eb")
    draw = ImageDraw.Draw(canvas)
    draw.text((padding, 12), title, font=heading, fill="#252530")
    for i, (path, label) in enumerate(items):
        x, y = padding + (i % columns) * (width + padding), top + (i // columns) * row_height
        draw.text((x, y), label, font=font, fill="#252530", spacing=2)
        with Image.open(path) as original:
            original = original.convert("RGB")
            image = original.resize((width, heights[i]), Image.Resampling.LANCZOS)
            canvas.paste(image, (x, y + caption))
    canvas.save(destination)
    return {"file": destination.relative_to(REPORTS).as_posix(),
            "sha256": hashlib.sha256(destination.read_bytes()).hexdigest(),
            "sources": [{"file": p.relative_to(REPORTS).as_posix(), "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p, _ in items]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phase", required=True)
    parser.add_argument("--compare-baseline", action="store_true")
    args = parser.parse_args()
    metadata = REPORTS / args.phase / "evidence.json"
    evidence = json.loads(metadata.read_text(encoding="utf-8"))
    assert evidence["passed"] and evidence["kind"] == "screenshots"
    OUT.mkdir(parents=True, exist_ok=True)
    records = []
    images = evidence["images"]
    for offset in range(0, len(images), 6):
        items = []
        for item in images[offset:offset + 6]:
            path = metadata.parent / item["file"]
            assert hashlib.sha256(path.read_bytes()).hexdigest() == item["sha256"]
            label = f'{item["screen"]} / {item["state"]}\n{"深色" if item["dark"] else "浅色"} · {item["fontScale"]}× 系统字号'
            items.append((path, label))
        records.append(sheet(items, OUT / f"{args.phase}-{offset // 6 + 1:02d}.png", args.phase))
    if args.compare_baseline:
        for theme in ("light", "dark"):
            items = []
            for screen in ("songs", "album", "player", "server"):
                name = f"{screen}-normal-{theme}-font1_0.png"
                items.extend([(REPORTS / "baseline" / name, f"{screen} · 修改前（历史基线）"),
                              (metadata.parent / name, f"{screen} · 当前样板")])
            records.append(sheet(items, OUT / f"comparison-{theme}.png", "首轮样板 · 360dp / 1× 内容字号（历史基线仅作视觉参考）", columns=4))
        for theme, font in (("light", "1_0"), ("dark", "1_0"), ("light", "2_0")):
            screens = ("songs", "album", "player", "queue", "modules", "server")
            items = [(metadata.parent / f"{screen}-normal-{theme}-font{font}.png", screen) for screen in screens]
            name = f"overview-{theme}-{font}.png"
            records.append(sheet(items, OUT / name, f"Nordic 首轮样板 · {theme} / {font.replace('_', '.')}× 系统字号", columns=3, width=360))
        interactions = []
        for phase in ("final-interaction", "final-interaction-short"):
            for screen in ("player", "lyrics"):
                interactions.append((REPORTS / phase / f"{screen}-controls-large.png", f"{phase}\n{screen} · 完整控制区"))
        records.append(sheet(interactions, OUT / "player-controls-large.png", "真实滚动和点击后的控制区 · 2× 系统字号", columns=4))
    (OUT / f"{args.phase}-review.json").write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for record in records:
        print(record["file"])


if __name__ == "__main__":
    main()
