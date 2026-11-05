#!/usr/bin/env python3
"""Local BRAVIA checks; no GitHub upload, SDK provisioning, or device commands."""

import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
HOST_CHECKS = (
    "test_no_tracked_native_binaries.sh",
    "test_npu_conversation_policy_artifact_verification.sh",
    "test_standard_release_automated_conversation_suite.sh",
    "test_conversation_api_ab_harness.sh",
)
EXCLUDED = (
    "test_npu_conversation_policy_device_validation.sh",
    "test_npu_conversation_policy_device_summary.sh",
)
JVM_ARGS = "-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8"
ROBOLECTRIC_JAR = "android-all-instrumented-14-robolectric-10818077-i6.jar"


def capture(*command):
    return subprocess.check_output(command, cwd=ROOT, text=True).strip()


def schema_guard():
    for version in (13, 14):
        path = ROOT / f"app/schemas/io.github.ninbyo02.lami.db.ChatDatabase/{version}.json"
        if not path.is_file():
            raise RuntimeError(f"Missing Room schema: {path.relative_to(ROOT)}")
    if capture("git", "status", "--porcelain", "--untracked-files=all", "--", "app/schemas"):
        raise RuntimeError("Room schema history changed; inspect app/schemas before continuing")


def commands(profile, run_dir, gradle_home, gradle_executable=None):
    steps = []
    if profile in ("guards", "tv", "debug", "all"):
        steps += [(name.removesuffix(".sh"), ["bash", f"scripts/tests/{name}"])
                  for name in HOST_CHECKS]
    if profile in ("tv", "all"):
        steps.append(("bravia-jvm", [sys.executable, "scripts/run_bravia_jvm_tests.py",
                                   "--gradle-cache", str(gradle_home / "caches/modules-2/files-2.1"),
                                   "--output", str(run_dir / "jvm")]))
    gradle = [str(gradle_executable or "<pinned-cached-gradle>/bin/gradle"), "--no-daemon", "--offline", "--max-workers=2",
              f"-Dorg.gradle.jvmargs={JVM_ARGS}",
              "-Pkotlin.compiler.execution.strategy=in-process", "-Pandroid.builder.sdkDownload=false",
              "--init-script", str(ROOT / "scripts/bravia-local-ci.init.gradle"),
              f"-Dlami.localCiRobolectricDir={run_dir / 'robolectric'}", "--stacktrace"]
    if profile in ("debug", "all"):
        steps += [
            ("debug-unit-tests", gradle + [":app:testStandardDebugUnitTest"]),
            ("debug-lint", gradle + ["-Plami.allowMissingQairt244Jni=true", ":app:lintStandardDebug"]),
            ("debug-apk", gradle + ["-Plami.allowMissingQairt244Jni=true", ":app:assembleStandardDebug"]),
        ]
    if profile in ("release", "all"):
        release = gradle + ["-Plami.standardNpuRuntimeEnabled=false"]
        steps += [
            ("release-unit-tests", release + [":app:testStandardReleaseUnitTest"]),
            ("release-lint", release + [":app:lintStandardRelease"]),
            ("release-packages", release + [":app:assembleStandardRelease", ":app:bundleStandardRelease"]),
        ]
    return steps


def java_preflight():
    java = Path(os.environ["JAVA_HOME"]) / "bin/java" if os.environ.get("JAVA_HOME") else Path(shutil.which("java") or "missing-java")
    if not java.is_file():
        raise RuntimeError("JDK 21 is required; set JAVA_HOME")
    version = subprocess.check_output([str(java), "-version"], stderr=subprocess.STDOUT, text=True)
    if not re.search(r'version "21(?:[.\"])', version):
        raise RuntimeError("JDK 21 is required; select it with JAVA_HOME")
    return version.splitlines()[0]


def cached_gradle(gradle_home):
    wrapper = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text()
    distribution = re.search(r"distributionUrl=.*?/(gradle-[^/]+)-(?:bin|all)\.zip", wrapper)
    if not distribution:
        raise RuntimeError("Cannot identify the pinned Gradle distribution")
    name = distribution[1]
    cached = sorted((gradle_home / "wrapper/dists").glob(f"{name}-*/*/{name}/bin/gradle"))
    if not cached:
        raise RuntimeError(f"{name} wrapper is not cached in GRADLE_USER_HOME; refusing a wrapper download")
    return cached[0].resolve()


def android_preflight(gradle_home):
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk:
        raise RuntimeError("Set ANDROID_SDK_ROOT to the existing licensed Android SDK; no SDK is installed automatically")
    sdk = Path(sdk).resolve()
    for item in ("platforms/android-36/android.jar", "build-tools/36.0.0/zipalign"):
        if not (sdk / item).is_file():
            raise RuntimeError(f"Required SDK input is missing: {item}")
    return sdk, cached_gradle(gradle_home)


def native_preflight(sdk):
    # The app otherwise falls back to docker run, which can pull an image.
    suffix = "toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android34-clang++"
    for candidate in sorted((sdk / "ndk").glob("*"), reverse=True):
        compiler = candidate / suffix
        if compiler.is_file() and os.access(compiler, os.X_OK):
            return candidate.resolve()
    raise RuntimeError("Existing Android NDK clang++ for API 34 is required; refusing the Docker fallback")


def prepare_robolectric(cache, run_dir):
    # Gradle --offline does not control Robolectric's own Maven resolver.
    if 'org.robolectric:robolectric:4.12.2' not in (ROOT / "app/build.gradle.kts").read_text():
        raise RuntimeError("Robolectric declaration changed; review the pinned offline SDK jar first")
    jar = cache / "14-robolectric-10818077-i6" / ROBOLECTRIC_JAR
    if not jar.is_file():
        raise RuntimeError(f"Missing existing Robolectric SDK 34 jar: {jar}; no download attempted")
    target = run_dir / "robolectric"
    target.mkdir()
    (target / ROBOLECTRIC_JAR).symlink_to(jar.resolve())
    return {"path": str(jar.resolve()), "sha256": digest(jar), "bytes": jar.stat().st_size}


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def inventory_release(run, run_dir, sdk):
    apks = sorted((ROOT / "app/build/outputs/apk/standard/release").glob("*.apk"))
    bundles = sorted((ROOT / "app/build/outputs/bundle/standardRelease").glob("*.aab"))
    if not apks or not bundles:
        raise RuntimeError("Release build must produce both APK and AAB")
    for index, package in enumerate(apks + bundles):
        run(f"release-packaging-{index}", ["bash", "scripts/tests/test_standard_release_npu_packaging.sh", str(package), "disabled"])
    for index, apk in enumerate(apks):
        run(f"release-zipalign-{index}", [str(sdk / "build-tools/36.0.0/zipalign"), "-c", "-P", "16", "-v", "4", str(apk)])
    libraries = {}
    with zipfile.ZipFile(apks[0]) as archive:
        for entry in archive.infolist():
            parts = entry.filename.split("/")
            if len(parts) == 3 and parts[0] == "lib" and parts[2].endswith(".so"):
                stats = libraries.setdefault(parts[1], {"files": 0, "uncompressed_bytes": 0})
                stats["files"] += 1
                stats["uncompressed_bytes"] += entry.file_size
    (run_dir / "native-library-inventory.json").write_text(json.dumps(libraries, indent=2) + "\n")


def save_reports(run_dir, started, profile):
    # Keep only outputs updated by this invocation; do not copy stale binary artifacts.
    patterns = []
    if profile in ("debug", "all"):
        patterns += ["app/build/reports/tests/testStandardDebugUnitTest/**/*",
                     "app/build/test-results/testStandardDebugUnitTest/**/*",
                     "app/build/reports/lint-results-standardDebug.*",
                     "app/build/outputs/apk/standard/debug/*.apk"]
    if profile in ("release", "all"):
        patterns += ["app/build/reports/tests/testStandardReleaseUnitTest/**/*",
                     "app/build/test-results/testStandardReleaseUnitTest/**/*",
                     "app/build/reports/lint-results-standardRelease.*",
                     "app/build/outputs/apk/standard/release/*.apk",
                     "app/build/outputs/bundle/standardRelease/*.aab"]
    inventory = []
    for path in sorted({p for pattern in patterns for p in ROOT.glob(pattern)}):
        if not path.is_file() or path.stat().st_mtime_ns < started:
            continue
        relative = path.relative_to(ROOT)
        inventory.append({"path": str(relative), "bytes": path.stat().st_size, "sha256": digest(path)})
        if path.suffix not in (".apk", ".aab"):
            target = run_dir / "reports" / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, target)
    (run_dir / "outputs.json").write_text(json.dumps(inventory, indent=2) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", choices=("guards", "tv", "debug", "release", "all"), default="tv")
    parser.add_argument("--gradle-home", type=Path, default=Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle"))))
    parser.add_argument("--output-root", type=Path, default=ROOT / "artifacts/bravia-local-ci")
    parser.add_argument("--robolectric-cache", type=Path,
                        default=Path.home() / ".m2/repository/org/robolectric/android-all-instrumented",
                        help="Existing Robolectric instrumented SDK cache; read only, no downloads")
    parser.add_argument("--plan", action="store_true", help="Show commands without running or creating files")
    args = parser.parse_args()
    gradle_home = args.gradle_home.expanduser().resolve()
    output_root = args.output_root.expanduser().resolve()
    run_dir = output_root / (time.strftime("%Y%m%dT%H%M%SZ", time.gmtime()) + f"-{os.getpid()}")
    steps = commands(args.profile, run_dir, gradle_home)
    if args.plan:
        if args.profile in ("debug", "release", "all"):
            try:
                steps = commands(args.profile, run_dir, gradle_home, cached_gradle(gradle_home))
            except RuntimeError:
                pass  # Keep the plan readable on hosts that have not provisioned Gradle.
        print(json.dumps({"profile": args.profile, "commands": steps,
                          "excluded_device_contracts": EXCLUDED,
                          "release_post_checks": "APK/AAB NPU exclusion, 16K zipalign, SHA-256 inventory",
                          "gradle_user_home": str(gradle_home)}, indent=2))
        return 0
    output_root.mkdir(parents=True, exist_ok=True)
    # A shared checkout lock applies even when callers choose different output roots.
    lock_dir = ROOT / "artifacts/bravia-local-ci"
    lock_dir.mkdir(parents=True, exist_ok=True)
    with (lock_dir / ".lock").open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            print("Another BRAVIA local CI run owns this checkout; stopped", file=sys.stderr)
            return 2
        run_dir.mkdir()
        started = time.time_ns()
        summary = {"profile": args.profile, "status": "running", "head": capture("git", "rev-parse", "HEAD"),
                   "branch": capture("git", "branch", "--show-current"), "steps": [],
                   "excluded_device_contracts": list(EXCLUDED), "github_changed": False,
                   "binary_storage": "app/build/outputs; binaries are not duplicated into run directories"}
        inputs = [ROOT / path for path in (".github/workflows/ci.yml", "gradle/libs.versions.toml",
                  "app/build.gradle.kts", "scripts/run_bravia_local_ci.py", "scripts/run_bravia_jvm_tests.py",
                  "scripts/bravia-local-ci.init.gradle")]
        inputs += sorted((ROOT / "app/src").glob("*/java/io/github/ninbyo02/lami/tools/bravia/*.kt"))
        (run_dir / "working-tree.txt").write_text(capture("git", "status", "--porcelain", "--untracked-files=all") + "\n")
        env = os.environ.copy()
        env.update({"GRADLE_USER_HOME": str(gradle_home), "PYTHONDONTWRITEBYTECODE": "1"})

        def run(name, command):
            print(f"[{name}] {shlex.join(command)}", flush=True)
            log = run_dir / f"{name}.log"
            before = time.monotonic()
            with log.open("w") as stream:
                result = subprocess.run(command, cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)
            summary["steps"].append({"name": name, "command": command, "exit_code": result.returncode,
                                     "seconds": round(time.monotonic() - before, 3), "log": log.name})
            if result.returncode:
                raise RuntimeError(f"{name} failed (exit {result.returncode}); see {log}")

        try:
            summary["inputs"] = [{"path": str(path.relative_to(ROOT)), "sha256": digest(path)} for path in inputs]
            schema_guard()
            if args.profile != "guards":
                summary["java"] = java_preflight()
            sdk = None
            if args.profile in ("debug", "release", "all"):
                sdk, gradle_executable = android_preflight(gradle_home)
                steps = commands(args.profile, run_dir, gradle_home, gradle_executable)
                env.update({"ANDROID_HOME": str(sdk), "ANDROID_SDK_ROOT": str(sdk)})
                if args.profile in ("debug", "all"):
                    ndk = native_preflight(sdk)
                    env.update({"ANDROID_NDK_HOME": str(ndk), "ANDROID_NDK_ROOT": str(ndk)})
                    summary["ndk"] = str(ndk)
                summary["robolectric"] = prepare_robolectric(args.robolectric_cache.expanduser().resolve(), run_dir)
                analyzer = sdk / "cmdline-tools/latest/bin/apkanalyzer"
                if args.profile in ("release", "all"):
                    if not analyzer.is_file():
                        raise RuntimeError("Release packaging verification needs existing cmdline-tools/latest/bin/apkanalyzer")
                    env["APK_ANALYZER"] = str(analyzer)
            for name, command in steps:
                run(name, command)
            if args.profile in ("release", "all"):
                inventory_release(run, run_dir, sdk)
            schema_guard()
            if any(digest(ROOT / item["path"]) != item["sha256"] for item in summary["inputs"]):
                raise RuntimeError("CI inputs changed during validation; result is not accepted")
            summary["status"] = "passed"
        except (OSError, subprocess.CalledProcessError, RuntimeError) as error:
            summary["status"] = "failed"
            summary["error"] = str(error)
            print(str(error), file=sys.stderr)
        finally:
            try:
                save_reports(run_dir, started, args.profile)
            except (OSError, RuntimeError) as error:
                summary["status"] = "failed"
                summary["report_error"] = str(error)
            summary["seconds"] = round((time.time_ns() - started) / 1e9, 3)
            (run_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n")
            print(f"{summary['status']}: {run_dir}")
        return 0 if summary["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
