"""校验明确白名单的当前证据和严格基线；不把失败/过期批次计作最终结果。"""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import struct

RESEARCH = Path(__file__).resolve().parent
ROOT = next(p for p in RESEARCH.parents if (p / "settings.gradle.kts").is_file())
REPORTS = ROOT / "app/build/reports/ui-polish"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    plan = json.loads((RESEARCH / "verification-matrix.json").read_text(encoding="utf-8"))
    spec = importlib.util.spec_from_file_location("runner", RESEARCH / "run-ui-checks.py")
    runner = importlib.util.module_from_spec(spec); spec.loader.exec_module(runner)
    current = {"sourceSha256": runner.source_sha256(), "appSha256": sha(runner.apk("debug")),
               "testApkSha256": sha(runner.apk("androidTest/debug")), "runnerSha256": sha(RESEARCH / "run-ui-checks.py")}
    def validate(phase, kind, tests, images, interaction_images=0, baseline=False):
        path = REPORTS / phase / "evidence.json"
        e = json.loads(path.read_text(encoding="utf-8"))
        assert e["schemaVersion"] == 3 and e["passed"] and e["kind"] == kind, phase
        assert e["testsRun"] == tests and len(e["testNames"]) == tests, phase
        assert e["device"] == "emulator-5580" and e["avd"] == "nordic-ui-api34" and e["apiLevel"] == "34", phase
        if baseline:
            assert e["sourceSha256"] == plan["baselineSourceSha256"], phase
        else:
            assert all(e[k] == v for k, v in current.items()), phase
        assert len(e.get("images", [])) == images and len(e.get("interactionImages", [])) == interaction_images, phase
        keys = set()
        for image in e.get("images", []) + e.get("interactionImages", []):
            file = path.parent / image["file"]
            assert sha(file) == image["sha256"], file
            assert struct.unpack(">II", file.read_bytes()[16:24]) == (e["widthDp"] * 3, e["heightDp"] * 3), file
            if kind == "screenshots":
                assert image["statusBarInkVerified"] and abs(image["fontScale"] - image["systemFontScale"]) < .001, file
                key = tuple(image[k] for k in ("screen", "state", "dark", "fontScale"))
                assert key not in keys; keys.add(key)
        if kind == "screenshots":
            a = e["arguments"]
            expected = {(screen,state,dark,float(font)) for screen in a["screens"].split(",")
                for state in a["states"].split(",") for dark in (False,True) for font in a["fonts"].split(",")}
            assert keys == expected, phase
        return e, {"phase":phase,"kind":kind,"testsRun":tests,"imageCount":images,"interactionImageCount":interaction_images,
            "manifest":path.relative_to(ROOT).as_posix(),"manifestSha256":sha(path),"arguments":e["arguments"],"testNames":e["testNames"],
            "capturedAt":e["capturedAt"],"durationSeconds":e["durationSeconds"]}
    baselines, final = [], []
    for pair in plan["pairs"]:
        before, b = validate(pair["before"], "screenshots", 1, pair["images"], baseline=True)
        after, a = validate(pair["after"], "screenshots", 1, pair["images"])
        assert (before["widthDp"],before["heightDp"]) == (after["widthDp"],after["heightDp"])
        for key in ("screens","states","fonts","width","height"):
            assert before["arguments"][key] == after["arguments"][key], (pair,key)
        baselines.append(b); final.append(a)
    for phase, count in plan["extraScreenshots"].items():
        final.append(validate(phase,"screenshots",1,count)[1])
    for phase, (kind,count,images) in plan["interaction"].items():
        final.append(validate(phase,kind,count,0,images)[1])
    build_path = REPORTS / plan["artifacts"] / "build-results.json"
    build = json.loads(build_path.read_text(encoding="utf-8"))
    assert build["sourceSha256"] == current["sourceSha256"]
    assert build["tests"]["tests"] == 723 and all(build["tests"][x] == 0 for x in ("failures","errors","skipped"))
    assert not any(build["lint"].get(x,0) for x in ("Error","Fatal"))
    for artifact in build["artifacts"]:
        assert sha(ROOT / artifact["file"]) == artifact["sha256"]
        assert artifact["versionName"] == "0.1.10" and artifact["versionCode"] == 10
    dex = json.loads((REPORTS / plan["artifacts"] / "dex-contract.json").read_text(encoding="utf-8"))
    methods = sum(dex[x]["count"] for x in ("NavidromeApi","AudiobookShelfApi","EmbyApi")); assert methods == 36
    summary = {"createdAt":datetime.now(timezone.utc).isoformat(),"codeBaseline":"c1ee241",**current,
        "baselineSourceSha256":plan["baselineSourceSha256"],"baselineScreenshotCount":sum(x["imageCount"] for x in baselines),
        "screenshotCount":sum(x["imageCount"] for x in final),"interactionImageCount":sum(x["interactionImageCount"] for x in final),
        "build":build,"retrofitSuspendMethodsVerified":methods,"baselineBatches":baselines,"batches":final,
        "scaffoldCorrection":plan["scaffoldCorrection"],"visualAcceptance":"待用户确认；真实服务/个人设备/其余媒体域未验收"}
    (RESEARCH / "evidence-summary.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps({"batches":len(final),"baselineScreenshots":summary["baselineScreenshotCount"],
        "screenshots":summary["screenshotCount"],"interactionImages":summary["interactionImageCount"],**current},indent=2))

if __name__ == "__main__":
    main()
