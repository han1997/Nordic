"""复用既有只读产物核验；原始 DEX/签名报告只写入构建目录。"""
from pathlib import Path
from collections import Counter
import hashlib
import argparse
import importlib.util
import json
import os
import re
import subprocess
import xml.etree.ElementTree as ET

TASK_RESEARCH = Path(__file__).resolve().parent
ROOT = next(parent for parent in TASK_RESEARCH.parents
            if (parent / "settings.gradle.kts").is_file() and (parent / ".trellis").is_dir())
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--phase", default="r2-artifacts")
args = parser.parse_args()
assert re.fullmatch(r"[a-z0-9_-]+", args.phase)
EVIDENCE = ROOT / "app/build/reports/ui-polish" / args.phase
assert not EVIDENCE.exists(), "Use a new artifact phase; never overwrite sealed reports"
EVIDENCE.mkdir(parents=True)
spec = importlib.util.spec_from_file_location("ui_runner", TASK_RESEARCH / "run-ui-checks.py")
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)
source_hash = runner.source_sha256()
os.chdir(ROOT)
SDK = Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk"
TOOLS = SDK / "build-tools/35.0.0"
ANALYZER = SDK / "cmdline-tools/latest/bin/apkanalyzer.bat"
BUILD_CONFIG = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
VERSION_NAME = re.search(r'versionName = "([^"]+)"', BUILD_CONFIG).group(1)
VERSION_CODE = int(re.search(r'versionCode = (\d+)', BUILD_CONFIG).group(1))


def run(*args):
    result = subprocess.run([str(a) for a in args], capture_output=True, text=True,
                            encoding="utf-8", errors="replace", check=True)
    return result.stdout


def save(name, value):
    (EVIDENCE / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


suites = [ET.parse(p).getroot() for p in Path("app/build/test-results/testDebugUnitTest").glob("TEST-*.xml")]
for required in ["EncryptedConfigStoreReactivityTest", "EncryptedPreferencesInstanceTest", "EncryptedConfigStoreTest", "SettingsCenterTest", "NordicDesignContractTest", "SettingsRowLayoutTest", "QueueItemGeometryTest", "MusicLibraryFeedbackTest", "MusicActionsSheetTest"]:
    assert any(s.attrib["name"].endswith(required) for s in suites), required
tests = {"suites": len(suites), **{k: sum(int(s.attrib.get(k, 0)) for s in suites)
         for k in ["tests", "failures", "errors", "skipped"]}}
assert tests["tests"] > 0 and tests["failures"] == tests["errors"] == tests["skipped"] == 0
lint = Counter(x.attrib["severity"] for x in ET.parse("app/build/reports/lint-results-debug.xml").getroot().findall("issue"))
assert not lint["Error"] and not lint["Fatal"]
artifacts = []
signatures = []
certificates = []
for variant in ["debug", "release"]:
    folder = Path("app/build/outputs/apk") / variant
    meta = json.loads((folder / "output-metadata.json").read_text(encoding="utf-8"))
    item = meta["elements"][0]
    apk = folder / item["outputFile"]
    assert item["versionName"] == VERSION_NAME and item["versionCode"] == VERSION_CODE
    assert meta["applicationId"] == "fun.han1997.nordic"
    if variant == "release":
        assert item["outputFile"] == f"nordic-{VERSION_NAME}.apk"
        release_apk = apk
    signature = run(TOOLS / "apksigner.bat", "verify", "--verbose", "--print-certs", apk)
    signatures.append("=== " + variant + " ===\n" + signature)
    certificates.append(re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})", signature).group(1))
    assert "Verified using v2 scheme (APK Signature Scheme v2): true" in signature
    badging = run(TOOLS / "aapt.exe", "dump", "badging", apk)
    assert f"name='fun.han1997.nordic' versionCode='{VERSION_CODE}' versionName='{VERSION_NAME}'" in badging
    assert "launchable-activity: name='com.nordic.mediahub.MainActivity'" in badging
    manifest = run(TOOLS / "aapt.exe", "dump", "xmltree", apk, "AndroidManifest.xml")
    assert ("com.nordic.mediahub.VideoPlayerPreviewActivity" in manifest) == (variant == "debug")
    assert ("com.nordic.mediahub.UiCatalogActivity" in manifest) == (variant == "debug")
    resources = run(TOOLS / "aapt.exe", "dump", "resources", apk)
    assert ("ui_sample_cover_a" in resources) == (variant == "debug")
    assert ("ui_sample_cover_b" in resources) == (variant == "debug")
    assert ("ui_sample_cover_c" in resources) == (variant == "debug")
    (EVIDENCE / f"{variant}-manifest.txt").write_text(manifest, encoding="utf-8")
    if variant == "release":
        assert not re.search(r"android:debuggable[^\r\n]*0xffffffff", manifest)
    artifacts.append({"variant": variant, "file": apk.as_posix(), "applicationId": meta["applicationId"],
        "versionName": item["versionName"], "versionCode": item["versionCode"], "bytes": apk.stat().st_size,
        "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "certificateSha256": certificates[-1]})
assert certificates[0] == certificates[1]
previous = ROOT / ".trellis/tasks/archive/2026-09/09-12-media-module-reactivity/research/apk-signatures.txt"
previous_certificates = set(re.findall(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})", previous.read_text(encoding="utf-8")))
assert previous_certificates == {certificates[0]}, "Signing identity differs from 0.1.8"
old_apk = Path("app/build/distributions/Nordic-0.1.3-release.apk")
if old_apk.exists():
    old_signature = run(TOOLS / "apksigner.bat", "verify", "--print-certs", old_apk)
    assert certificates[0] in old_signature
(EVIDENCE / "apk-signatures.txt").write_text("\n".join(signatures), encoding="utf-8")
save("build-results.json", {"sourceSha256": source_hash, "verifierSha256": runner.sha256(Path(__file__)), "tests": tests, "lint": dict(lint), "artifacts": artifacts})

mapping = Path("app/build/outputs/mapping/release/mapping.txt").read_text(encoding="utf-8")
assert not re.search(r"^com\.nordic\.mediahub\.(?:UiCatalogActivity|ui\.(?:UiSample[^ ]*|UiCatalog[^ ]*|MusicCatalog[^ ]*)) ->", mapping, re.M), "Debug catalog leaked into release DEX"
classes = ["com.nordic.mediahub.api." + n for n in ["NavidromeApi", "AudiobookShelfApi", "EmbyApi"]]
classes += ["kotlin.coroutines.Continuation", "retrofit2.Response", "retrofit2.Call"]
resolved = {}
for name in classes:
    match = re.search(r"^" + re.escape(name) + r" -> (.+):$", mapping, re.M)
    target = match.group(1) if match else name
    resolved[name] = target
    print("Checking release DEX:", name, flush=True)
    text = run(ANALYZER, "dex", "code", "--class", target, release_apk)
    (EVIDENCE / (name.rsplit(".", 1)[-1] + ".dex.txt")).write_text(text, encoding="utf-8")
save("dex-class-mapping.json", resolved)
cont = "L" + resolved["kotlin.coroutines.Continuation"].replace(".", "/")
response = "L" + resolved["retrofit2.Response"].replace(".", "/")
needle = cont + "<-" + response + "<"
results = {}
for api in ["NavidromeApi", "AudiobookShelfApi", "EmbyApi"]:
    source = Path("app/src/main/java/com/nordic/mediahub/api/" + api + ".kt").read_text(encoding="utf-8")
    expected = set(re.findall(r"\bsuspend\s+fun\s+(\w+)\s*\(", source))
    text = (EVIDENCE / (api + ".dex.txt")).read_text(encoding="utf-8")
    verified = []
    for method in re.findall(r"^\.method\b.*?^\.end method", text, re.M | re.S):
        header = method.splitlines()[0]
        if cont + ";" not in header:
            continue
        name = re.search(r"\s(\w+)\(", header).group(1)
        sigs = re.findall(r"\.annotation system Ldalvik/annotation/Signature;(.*?)\.end annotation", method, re.S)
        assert len(sigs) == 1, (api, name)
        sig = "".join(re.findall(r'"([^"\n]*)"', sigs[0]))
        assert needle in sig and sig.endswith(">;>;)Ljava/lang/Object;"), (api, name, sig)
        verified.append(name)
    assert set(verified) == expected, (api, expected - set(verified))
    results[api] = {"count": len(verified), "methods": verified}
for name in ["Continuation", "Response", "Call"]:
    text = (EVIDENCE / (name + ".dex.txt")).read_text(encoding="utf-8")
    block = re.search(r"\.annotation system Ldalvik/annotation/Signature;(.*?)\.end annotation", text, re.S).group(1)
    assert "".join(re.findall(r'"([^"\n]*)"', block)).startswith("<T:")
    results[name] = {"genericTypeParameterPreserved": True}
save("dex-contract.json", results)
print(json.dumps({"tests": tests, "lint": dict(lint), "release": artifacts[-1],
                  "suspendMethodsVerified": sum(results[n]["count"] for n in ["NavidromeApi", "AudiobookShelfApi", "EmbyApi"])}, indent=2))

assert runner.source_sha256() == source_hash, "Sources changed during artifact verification"
for item in artifacts:
    assert runner.sha256(ROOT / item["file"]) == item["sha256"], "APK changed during verification"
