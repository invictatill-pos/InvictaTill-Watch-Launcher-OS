"""Collect release evidence from completed builds and verify the exact APKs."""
import hashlib
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

root = Path(__file__).resolve().parents[1]
version = "2.7.0"
sdk = Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk"
java = Path(os.environ.get("JAVA_HOME", "C:/Program Files/Android/Android Studio/jbr")) / "bin/java.exe"
build_tools = sdk / "build-tools/34.0.0"

def run(arguments):
    completed = subprocess.run([str(arg) for arg in arguments], check=True, text=True, capture_output=True)
    return completed.stdout

def signature(path):
    result = run([java, "-jar", build_tools / "lib/apksigner.jar", "verify", "--verbose", "--print-certs", path])
    digest = re.search(r"certificate SHA-256 digest: ([a-f0-9]{64})", result)
    assert digest, "APK signer certificate was not reported"
    return {
        "verified": True,
        "certificate_sha256": digest[1],
        "v1": "Verified using v1 scheme (JAR signing): true" in result,
        "v2": "Verified using v2 scheme (APK Signature Scheme v2): true" in result,
    }

summary = {"version": version, "automated_validation_only": True, "modules": {}}
for module, variant, apk_name, previous, package, code in [
    ("PhoneApp", "release", "app-release.apk", "HealthSync-Phone-2.6.0.apk", "com.healthsync.phone", 20),
    ("WatchApp", "debug", "app-debug.apk", "HealthSync-Watch-2.6.0-Android8.apk", "com.healthsync.watch", 26),
]:
    build = root / module / "app/build"
    test_files = list((build / "test-results/testDebugUnitTest").glob("TEST-*.xml"))
    assert test_files, f"No unit test results for {module}"
    counts = Counter()
    for path in test_files:
        suite = ET.parse(path).getroot()
        for key in ("tests", "failures", "errors", "skipped"):
            counts[key] += int(suite.get(key, "0"))
    assert counts["failures"] == counts["errors"] == 0
    lint = ET.parse(build / "reports/lint-results-debug.xml").getroot()
    issues = Counter(issue.get("severity") for issue in lint.findall("issue"))
    assert issues["Error"] == issues["Fatal"] == 0
    warnings = Counter(issue.get("id") for issue in lint.findall("issue") if issue.get("severity") == "Warning")
    metadata = json.loads((build / f"outputs/apk/{variant}/output-metadata.json").read_text())
    assert metadata["applicationId"] == package
    assert metadata["elements"][0]["versionName"] == version and metadata["elements"][0]["versionCode"] == code
    apk = build / f"outputs/apk/{variant}/{apk_name}"
    signing = signature(apk)
    assert signing["certificate_sha256"] == signature(root / "dist" / previous)["certificate_sha256"]
    assert signing["v2"] and (module != "WatchApp" or signing["v1"])
    badging = run([build_tools / "aapt.exe", "dump", "badging", apk])
    package_line = badging.splitlines()[0]
    assert f"name='{package}'" in package_line and f"versionCode='{code}'" in package_line and f"versionName='{version}'" in package_line
    log = root / ("build-phone-final-2.7.0.log" if module == "PhoneApp" else "build-watch-final-2.7.0.log")
    assert "BUILD SUCCESSFUL" in log.read_text(encoding="utf-16" if log.read_bytes().startswith(b"\xff\xfe") else "utf-8", errors="replace")
    summary["modules"][module] = {
        "gradle_build_passed": True,
        "unit_tests": dict(counts), "lint": dict(issues), "lint_warning_ids": dict(warnings),
        "package": package, "version_code": code,
        "minimum_api": int(re.search(r"sdkVersion:'(\d+)'", badging)[1]),
        "target_api": int(re.search(r"targetSdkVersion:'(\d+)'", badging)[1]),
        "apk_bytes": apk.stat().st_size,
        "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "signing": signing,
    }

summary["database_migrations"] = json.loads(run([sys.executable, root / "tools/Test-Health-Migrations.py"]))
devices = run([sdk / "platform-tools/adb.exe", "devices"])
summary["attached_android_devices"] = sum(1 for line in devices.splitlines()[1:] if "\tdevice" in line)
output = root / f"validation/{version}/summary.json"
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print(json.dumps(summary, indent=2))
