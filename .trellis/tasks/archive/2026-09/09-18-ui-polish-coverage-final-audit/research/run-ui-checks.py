"""仅在专用模拟器执行；构建另行运行，证据封存后不可覆盖。"""
from datetime import datetime, timezone
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import struct
import subprocess
import time

ROOT = next(p for p in Path(__file__).resolve().parents if (p / "settings.gradle.kts").is_file())
ADB = Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk/platform-tools/adb.exe"
SERIAL = "emulator-5580"
AVD = "nordic-ui-api34"
PACKAGE = "fun.han1997.nordic"


def run(*args, timeout=90):
    return subprocess.run([str(ADB), "-s", SERIAL, *map(str, args)], cwd=ROOT,
        capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout,
        check=True).stdout


def apk(variant):
    folder = ROOT / "app/build/outputs/apk" / variant
    metadata = json.loads((folder / "output-metadata.json").read_text(encoding="utf-8"))
    assert metadata["applicationId"] == (PACKAGE + ".test" if "androidTest" in variant else PACKAGE)
    return folder / metadata["elements"][0]["outputFile"]


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source_sha256():
    files = [p for p in (ROOT / "app/src").rglob("*") if p.is_file()]
    files += [ROOT / p for p in ("app/build.gradle.kts", "app/proguard-rules.pro", "build.gradle.kts", "settings.gradle.kts", "gradle.properties")]
    digest = hashlib.sha256()
    for path in sorted(files):
        digest.update(path.relative_to(ROOT).as_posix().encode("utf-8") + b"\0")
        digest.update(path.read_bytes())
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phase", required=True)
    parser.add_argument("--kind", choices=["screenshots", "interaction", "music", "live"], default="screenshots")
    parser.add_argument("--screens", default="songs,album,player,lyrics,queue,speed,modules,server,settings_rows")
    parser.add_argument("--states", default="normal")
    parser.add_argument("--fonts", default="1,2")
    parser.add_argument("--width", type=int, default=360)
    parser.add_argument("--height", type=int, default=800)
    args = parser.parse_args()
    assert re.fullmatch(r"[a-z0-9_-]+", args.phase)
    assert re.fullmatch(r"[a-z_,]+", args.screens) and re.fullmatch(r"[a-z_,]+", args.states)
    fonts = list(map(float, args.fonts.split(",")))
    assert fonts and all(font in (1, 1.5, 2) for font in fonts) and len(set(fonts)) == len(fonts)
    assert 320 <= args.width <= 720 and 320 <= args.height <= 960
    assert run("emu", "avd", "name").splitlines()[0].strip() == AVD, "Refusing another AVD"
    assert run("shell", "getprop", "ro.kernel.qemu").strip() == "1", "Refusing physical devices"
    output = ROOT / "app/build/reports/ui-polish" / args.phase
    assert not (output / "evidence.json").exists(), "Sealed batch exists; choose a new phase name"
    output.mkdir(parents=True, exist_ok=True)
    app, test = apk("debug"), apk("androidTest/debug")
    source_hash = source_sha256()
    evidence = {"schemaVersion": 3, "phase": args.phase, "kind": args.kind, "device": SERIAL, "avd": AVD,
        "capturedAt": datetime.now(timezone.utc).isoformat(), "apiLevel": run("shell", "getprop", "ro.build.version.sdk").strip(),
        "widthDp": args.width, "heightDp": args.height, "sourceSha256": source_hash,
        "appSha256": sha256(app), "testApkSha256": sha256(test), "runnerSha256": sha256(Path(__file__)),
        "arguments": vars(args), "passed": False}
    size = re.search(r"Override size: (\d+x\d+)", run("shell", "wm", "size"))
    density = re.search(r"Override density: (\d+)", run("shell", "wm", "density"))
    settings = {key: run("shell", "settings", "get", "system", key).strip()
        for key in ("font_scale", "screen_off_timeout")}
    start = time.monotonic()
    try:
        for package in (app, test):
            response = run("install", "-r", package, timeout=120)
            assert "Success" in response, response
        run("shell", "wm", "size", f"{args.width * 3}x{args.height * 3}")
        run("shell", "wm", "density", "480")
        run("shell", "settings", "put", "system", "font_scale", "1")
        run("shell", "settings", "put", "system", "screen_off_timeout", "1800000")
        run("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        run("shell", "wm", "dismiss-keyguard")
        klass = {"screenshots": "UiCatalogScreenshotTest", "interaction": "UiCatalogInteractionTest", "live": "MainSettingsUiTest", "music": "MusicCatalogInteractionTest"}[args.kind]
        result = run("shell", "am", "instrument", "-w", "-r", "-e", "class", f"com.nordic.mediahub.{klass}",
            "-e", "phase", args.phase, "-e", "screens", args.screens, "-e", "states", args.states,
            "-e", "fonts", args.fonts, f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner", timeout=900)
        (output / "instrumentation.txt").write_text(result, encoding="utf-8")
        match = re.search(r"OK \((\d+) tests?\)", result)
        print(result[-6000:] if not match else "\n".join(result.splitlines()[-8:]), flush=True)
        assert match, "Instrumentation did not report a passing test run"
        evidence["testsRun"] = int(match.group(1))
        evidence["testNames"] = sorted(set(re.findall(r"INSTRUMENTATION_STATUS: test=(.+)", result)))
        if args.kind == "screenshots":
            remote = f"/sdcard/Android/data/{PACKAGE}/files/ui-catalog/{args.phase}"
            manifest = json.loads(run("shell", "cat", remote + "/manifest.json"))
            expected = {(s, t, dark, font) for s in args.screens.split(",") for t in args.states.split(",")
                for dark in (False, True) for font in fonts}
            actual = {(i["screen"], i["state"], i["dark"], i["fontScale"]) for i in manifest}
            assert actual == expected and len(manifest) == len(expected), "Incomplete screenshot matrix"
            for image in manifest:
                name = image["file"]
                assert Path(name).name == name and name.endswith(".png")
                assert abs(image["systemFontScale"] - image["fontScale"]) < 0.001, "Dialog font mismatch"
                assert image["statusBarInkVerified"], "Status icon contrast was not checked"
                run("pull", remote + "/" + name, output / name)
                image["sha256"] = sha256(output / name)
                image["widthPx"], image["heightPx"] = struct.unpack(">II", (output / name).read_bytes()[16:24])
                assert (image["widthPx"], image["heightPx"]) == (args.width * 3, args.height * 3)
            evidence["images"] = manifest
        if args.kind in ("interaction", "music"):
            evidence["interactionImages"] = []
            remote = f"/sdcard/Android/data/{PACKAGE}/files/ui-interactions/{args.phase}"
            names = ("player-controls-large.png", "lyrics-controls-large.png") if args.kind == "interaction" else (
                "music-home-shelf-large.png", "music-search-song-large.png", "music-playlist-actions-large.png",
                "music-playlist-ime-large.png", "music-equalizer-band-large.png")
            for name in names:
                run("pull", remote + "/" + name, output / name)
                evidence["interactionImages"].append({"file": name, "fontScale": 2, "sha256": sha256(output / name)})
        assert source_sha256() == source_hash and sha256(app) == evidence["appSha256"] and sha256(test) == evidence["testApkSha256"], "Sources or APKs changed during capture"
        evidence["passed"] = True
    except subprocess.TimeoutExpired:
        run("shell", "am", "force-stop", PACKAGE)
        raise
    finally:
        # 恢复调用前的模拟器显示设置；失败也不把大字号/短屏状态泄漏给下一轮。
        run("shell", "wm", "size", size.group(1) if size else "reset")
        run("shell", "wm", "density", density.group(1) if density else "reset")
        for key, value in settings.items():
            if value == "null": run("shell", "settings", "delete", "system", key)
            else: run("shell", "settings", "put", "system", key, value)
        evidence["durationSeconds"] = round(time.monotonic() - start, 1)
        (output / "evidence.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"UI_CHECK_PASSED: {args.phase} -> {output}", flush=True)


if __name__ == "__main__":
    main()
