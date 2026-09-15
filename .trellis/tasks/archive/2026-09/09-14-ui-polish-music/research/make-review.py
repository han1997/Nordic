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
OUT = REPORTS / "r2-review/final"
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
    research = Path(__file__).resolve().parent
    plan = json.loads((research / "verification-matrix.json").read_text(encoding="utf-8"))
    OUT.mkdir(parents=True, exist_ok=True)
    records = []
    after_phases = [p["after"] for p in plan["pairs"]] + list(plan["extraScreenshots"])
    for phase in after_phases:
        metadata = REPORTS / phase / "evidence.json"
        e = json.loads(metadata.read_text(encoding="utf-8"))
        assert e["passed"] and e["kind"] == "screenshots"
        for offset in range(0, len(e["images"]), 8):
            items = []
            for image in e["images"][offset:offset+8]:
                path = metadata.parent / image["file"]
                assert hashlib.sha256(path.read_bytes()).hexdigest() == image["sha256"]
                label = f'{image["screen"]} / {image["state"]}\n{"深色" if image["dark"] else "浅色"} · {image["fontScale"]}×'
                items.append((path, label))
            records.append(sheet(items, OUT / f"{phase}-{offset//8+1:02d}.png", phase, columns=4, width=240))
    for pair in plan["pairs"]:
        before = json.loads((REPORTS / pair["before"] / "evidence.json").read_text(encoding="utf-8"))
        after = json.loads((REPORTS / pair["after"] / "evidence.json").read_text(encoding="utf-8"))
        assert (before["widthDp"],before["heightDp"]) == (after["widthDp"],after["heightDp"])
        indexed={i["file"]:i for i in before["images"]}
        for offset in range(0,len(after["images"]),4):
            items=[]
            for image in after["images"][offset:offset+4]:
                original=indexed[image["file"]]
                assert all(original[k]==image[k] for k in ("screen","state","dark","fontScale","systemFontScale"))
                tag = f'{image["screen"]} · {image["state"]}'
                items += [(REPORTS/pair["before"]/image["file"], tag+"\n修改前"),
                          (REPORTS/pair["after"]/image["file"], tag+"\n修改后")]
            name=f'compare-{pair["after"]}-{offset//4+1:02d}.png'
            records.append(sheet(items, OUT/name, f'{before["widthDp"]}dp · 同系统字号/主题/数据（样板导航修正另记）',columns=4,width=240))
    for phase,(kind,_,count) in plan["interaction"].items():
        if not count: continue
        e=json.loads((REPORTS/phase/"evidence.json").read_text(encoding="utf-8"))
        records.append(sheet([(REPORTS/phase/i["file"],i["file"].replace("music-", "")) for i in e["interactionImages"]],
            OUT/f"{phase}.png",phase,columns=3,width=288))
    (OUT/"review-manifest.json").write_text(json.dumps(records,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    import html
    sections=["<!doctype html><meta charset='utf-8'><title>Nordic 音乐域精修审阅</title><style>body{font:16px system-ui;margin:32px;background:#f5f4f8;color:#1a1a20}img{max-width:100%;height:auto}section{margin:32px 0}a{color:#4b327f}</style><h1>音乐域第二轮 · 修改前后与设备交互</h1><p>这是封存原图的缩放排列，不是重新绘制的效果图。均衡器仍未接入；真实服务与个人设备未验收。本轮用户视觉确认待定。</p>",
        "<p>"+html.escape(plan["scaffoldCorrection"])+"</p>"]
    for record in records:
        file=Path(record["file"]).name
        sections.append(f"<section><h2>{html.escape(file)}</h2><a href='{file}'><img loading='lazy' src='{file}'></a></section>")
    (OUT/"index.html").write_text("\n".join(sections),encoding="utf-8")
    print(f"Review sheets: {len(records)}; {OUT / 'index.html'}")

if __name__ == "__main__":
    main()
