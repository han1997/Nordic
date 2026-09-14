"""核对最终白名单批次的指纹和原图，生成可提交的小型证据索引。"""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import struct

RESEARCH = Path(__file__).resolve().parent
ROOT = next(p for p in RESEARCH.parents if (p / "settings.gradle.kts").is_file())
REPORTS = ROOT / "app/build/reports/ui-polish"
EXPECTED = {
    "final-interaction": ("interaction", 17, 0),
    "final-interaction-short": ("interaction", 17, 0),
    "delivery-live": ("live", 1, 0),
    "delivery-samples": ("screenshots", 1, 44),
    "delivery-music-states": ("screenshots", 1, 64),
    "delivery-long": ("screenshots", 1, 20),
    "delivery-disabled": ("screenshots", 1, 8),
    "delivery-server-states": ("screenshots", 1, 8),
    "delivery-queue-empty": ("screenshots", 1, 4),
    "delivery-short": ("screenshots", 1, 18),
    "delivery-wide": ("screenshots", 1, 22),
    "delivery-mid": ("screenshots", 1, 10),
}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    spec = importlib.util.spec_from_file_location("runner", RESEARCH / "run-ui-checks.py")
    runner = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(runner)
    current = {"sourceSha256": runner.source_sha256(), "appSha256": sha(runner.apk("debug")),
               "testApkSha256": sha(runner.apk("androidTest/debug")), "runnerSha256": sha(RESEARCH / "run-ui-checks.py")}
    batches = []
    for phase, (kind, tests, image_count) in EXPECTED.items():
        path = REPORTS / phase / "evidence.json"
        evidence = json.loads(path.read_text(encoding="utf-8"))
        assert evidence["schemaVersion"] == 3 and evidence["passed"], phase
        assert evidence["kind"] == kind and evidence["testsRun"] == tests, phase
        assert evidence["device"] == "emulator-5580" and evidence["avd"] == "nordic-ui-api34", phase
        assert all(evidence[key] == value for key, value in current.items()), phase
        images = evidence.get("images", [])
        assert len(images) == image_count, phase
        for image in images + evidence.get("interactionImages", []):
            file = path.parent / image["file"]
            assert sha(file) == image["sha256"], file
            if kind == "screenshots":
                assert abs(image["fontScale"] - image["systemFontScale"]) < 0.001 and image["statusBarInkVerified"], file
                assert struct.unpack(">II", file.read_bytes()[16:24]) == (evidence["widthDp"] * 3, evidence["heightDp"] * 3), file
        batches.append({"phase": phase, "kind": kind, "testsRun": tests, "imageCount": image_count,
                        "capturedAt": evidence["capturedAt"], "durationSeconds": evidence["durationSeconds"],
                        "arguments": evidence["arguments"], "testNames": evidence["testNames"],
                        "manifest": path.relative_to(ROOT).as_posix(), "manifestSha256": sha(path)})
    build_path = REPORTS / "artifacts/build-results.json"
    build = json.loads(build_path.read_text(encoding="utf-8"))
    assert build["tests"]["tests"] == 712 and all(build["tests"][x] == 0 for x in ("failures", "errors", "skipped"))
    assert not any(build["lint"].get(x, 0) for x in ("Error", "Fatal"))
    for artifact in build["artifacts"]:
        assert sha(ROOT / artifact["file"]) == artifact["sha256"]
    dex = json.loads((REPORTS / "artifacts/dex-contract.json").read_text(encoding="utf-8"))
    methods = sum(dex[x]["count"] for x in ("NavidromeApi", "AudiobookShelfApi", "EmbyApi"))
    assert methods == 36
    task = json.loads((RESEARCH.parent / "task.json").read_text(encoding="utf-8"))
    review = task.get("meta", {}).get("review_handoff", {})
    visual_acceptance = (
        f'{review.get("acceptedAt", "")} 用户确认首轮样板方向；保留 evidence-index.md 验证限制；未完成全应用逐页迁移'
        if review.get("visualAcceptance") == "accepted"
        else "待用户确认；未完成全应用逐页迁移"
    )
    summary = {"createdAt": datetime.now(timezone.utc).isoformat(), "codeBaseline": "d4b4c0d", **current,
               "build": build, "retrofitSuspendMethodsVerified": methods,
               "screenshotCount": sum(x["imageCount"] for x in batches), "batches": batches,
               "visualAcceptance": visual_acceptance,
               "historicalBaseline": "baseline 仅作历史视觉参考；无系统字号/源码指纹，不作大字体 Dialog 严格同条件对照"}
    (RESEARCH / "evidence-summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"batches": len(batches), "screenshots": summary["screenshotCount"], "unitTests": 712,
                      "normalInteractionTests": 17, "shortInteractionTests": 17, "liveTests": 1, **current}, indent=2))


if __name__ == "__main__":
    main()
