#!/usr/bin/env python3
"""Run the 14 host-only BRAVIA tests without Gradle or dependency downloads.

Python 3.11+, JDK 21+, and the exact pinned jars in an existing Gradle cache
are required. The cache is only read. This small JVM profile covers command
parsing, XML parsing and protocol behavior (including a loopback HTTP server),
not Android UI, packaging, Gradle resolution or a real television.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import tomllib


PACKAGE = "io.github.ninbyo02.lami.tools.bravia"
MAIN_NAMES = ("BraviaCommand", "BraviaDiscovery", "BraviaProtocol")
TEST_NAMES = tuple(name + "Test" for name in MAIN_NAMES)
EXPECTED_TESTS = 14

# An explicit local JVM profile, not a Gradle dependency resolver. Runtime pins
# reflect the existing standardDebug resolved dependency model; the app declares
# OkHttp 4.9.3, which Gradle promotes to 4.12.0 through its dependency graph.
# Never substitute a different cached version or download missing dependencies.
COMPILER_COORDINATES = (
    ("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.3.0"),
    ("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.0"),
    ("org.jetbrains.kotlin", "kotlin-script-runtime", "2.3.0"),
    ("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"),
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0"),
    ("org.jetbrains", "annotations", "23.0.0"),
)
RUNTIME_COORDINATES = (
    ("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.0"),
    ("com.google.code.gson", "gson", "2.13.2"),
    ("com.squareup.okhttp3", "okhttp", "4.12.0"),
    ("com.squareup.okhttp3", "mockwebserver", "4.9.3"),
    ("com.squareup.okio", "okio-jvm", "3.8.0"),
    ("junit", "junit", "4.13.2"),
    ("org.hamcrest", "hamcrest-core", "1.3"),
    ("org.jetbrains", "annotations", "23.0.0"),
)


class RunnerError(Exception):
    """An actionable local prerequisite or validation failure."""


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def resolve_jar(cache: Path, coordinate: tuple[str, str, str]) -> Path:
    group, artifact, version = coordinate
    version_dir = cache / group / artifact / version
    candidates = sorted(version_dir.glob(f"*/{artifact}-{version}.jar"))
    if not candidates:
        raise RunnerError(
            f"Missing cached jar {group}:{artifact}:{version} under {cache}. "
            "No download or version substitution was attempted. Populate the "
            "approved build cache separately, then run into a fresh output directory."
        )
    # Gradle may contain duplicate identical content under multiple hash folders.
    if len({sha256(path) for path in candidates}) != 1:
        raise RunnerError(f"Conflicting cached jars for {group}:{artifact}:{version}")
    return candidates[0].resolve()


def source_files(repo: Path) -> list[Path]:
    package_path = Path(*PACKAGE.split("."))
    return [
        repo / "app/src" / kind / "java" / package_path / (name + ".kt")
        for kind, names in (("main", MAIN_NAMES), ("test", TEST_NAMES))
        for name in names
    ]


def check_profile(repo: Path, sources: list[Path]) -> None:
    versions = tomllib.loads((repo / "gradle/libs.versions.toml").read_text())["versions"]
    if versions.get("kotlin") != "2.3.0" or versions.get("junit") != "4.13.2":
        raise RunnerError("Kotlin/JUnit declarations changed; review the pinned JVM profile first.")
    build = (repo / "app/build.gradle.kts").read_text()
    for artifact in ("okhttp", "mockwebserver"):
        if f'com.squareup.okhttp3:{artifact}:4.9.3' not in build:
            raise RunnerError(f"{artifact} declaration changed; review the pinned JVM profile first.")
    for path in sources:
        if not path.is_file():
            raise RunnerError(f"Required BRAVIA source is missing: {path.relative_to(repo)}")
    count = sum(len(re.findall(r"@Test\b", path.read_text())) for path in sources[3:])
    if count != EXPECTED_TESTS:
        raise RunnerError(
            f"Expected {EXPECTED_TESTS} BRAVIA tests, found {count}; review the runner's coverage."
        )


def junit_count(output: str) -> int | None:
    match = re.search(r"^OK \((\d+) tests?\)\s*$", output, re.MULTILINE)
    return int(match.group(1)) if match else None


def record_junit_summary(output: str, report: dict) -> None:
    count = junit_count(output)
    failures = 0 if count is not None else None
    if count is None:
        match = re.search(r"^Tests run: (\d+),\s+Failures: (\d+)\s*$", output, re.MULTILINE)
        if match:
            count, failures = map(int, match.groups())
    report["tests_run"] = count
    report["test_failures"] = failures
    report["tests_passed"] = count - failures if count is not None and failures is not None else None
    if "java.net.SocketException: Operation not permitted" in output:
        report["status"] = "blocked"
        report["blocking_reason"] = "Sandbox denied the loopback MockWebServer socket; no retry or escalation attempted"


def run_step(name: str, command: list[str], repo: Path, output: Path,
             report: dict, timeout: int) -> str:
    log = output / (name + ".log")
    step = {"name": name, "command": command, "log": str(log), "timeout_seconds": timeout}
    report["steps"].append(step)
    started = time.monotonic()
    env = os.environ.copy()
    # Prevent user JVM injection/profilers from changing these host-only checks.
    for key in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH"):
        env.pop(key, None)
    try:
        with log.open("x") as handle:
            process = subprocess.run(command, cwd=repo, env=env, stdout=handle,
                                     stderr=subprocess.STDOUT, timeout=timeout, check=False)
        step["returncode"] = process.returncode
    except subprocess.TimeoutExpired as error:
        step["status"] = "timeout"
        raise RunnerError(f"{name} exceeded {timeout}s; see {log}") from error
    finally:
        step["elapsed_seconds"] = round(time.monotonic() - started, 3)
    if process.returncode:
        raise RunnerError(f"{name} failed (exit {process.returncode}); see {log}. No retry was attempted.")
    return log.read_text(errors="replace")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output-dir", "--output", type=Path, required=True,
                        help="Fresh or empty directory for classes, logs, and result.json")
    gradle_home = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle")))
    parser.add_argument("--gradle-cache", type=Path,
                        default=gradle_home / "caches/modules-2/files-2.1",
                        help="Existing files-2.1 cache; only exact pinned jars are read")
    parser.add_argument("--java", default=None, help="JDK 21+ java executable (default JAVA_HOME/PATH)")
    args = parser.parse_args(argv)
    repo, output, cache = args.repo.resolve(), args.output_dir.resolve(), args.gradle_cache.resolve()
    if output == cache or cache in output.parents:
        parser.error("output-dir must be outside the read-only dependency cache")
    if output.exists() and (not output.is_dir() or any(output.iterdir())):
        parser.error("output-dir must be a fresh or empty directory; existing results are preserved")
    report = {
        "profile": "bravia-host-jvm-v1", "status": "failed", "repo": str(repo),
        "started_at": dt.datetime.now(dt.timezone.utc).isoformat(), "steps": [],
        "coverage": "3 BRAVIA source classes, 3 JUnit classes; 14 host-only tests",
        "limitations": "Not Android UI, Gradle dependency resolution, APK/AAB packaging, or device validation",
        "dependency_cache": str(cache), "dependency_downloads": False,
        "dependency_profile": "Explicit fixed host profile; declared OkHttp 4.9.3 uses the existing Gradle-resolved 4.12.0 pin",
        "network": "Existing MockWebServer test uses 127.0.0.1; no television or ADB access",
    }
    try:
        output.mkdir(parents=True, exist_ok=True)
        sources = source_files(repo)
        check_profile(repo, sources)
        report["sources"] = [
            {"path": str(path.relative_to(repo)), "sha256": sha256(path)} for path in sources
        ]
        coordinates = tuple(dict.fromkeys(COMPILER_COORDINATES + RUNTIME_COORDINATES))
        jars = {coordinate: resolve_jar(cache, coordinate) for coordinate in coordinates}
        report["dependencies"] = [
            {"coordinate": ":".join(coordinate), "path": str(path), "sha256": sha256(path)}
            for coordinate, path in jars.items()
        ]
        java = args.java
        if java is None:
            java_home = os.environ.get("JAVA_HOME")
            java = str(Path(java_home) / "bin/java") if java_home else shutil.which("java")
        if not java:
            raise RunnerError("JDK 21+ java executable not found; set JAVA_HOME or --java.")
        version_output = run_step("java-version", [java, "-version"], repo, output, report, 15)
        version = re.search(r'version "(\d+)', version_output)
        if not version or int(version.group(1)) < 21:
            raise RunnerError("JDK 21+ is required; see java-version.log.")
        classes = output / "classes"
        classes.mkdir()
        temp = output / "java-tmp"
        temp.mkdir()
        compiler_cp = os.pathsep.join(str(jars[c]) for c in COMPILER_COORDINATES)
        runtime_cp = os.pathsep.join(str(jars[c]) for c in RUNTIME_COORDINATES)
        command = [java, "-Xmx1024m", f"-Djava.io.tmpdir={temp}", "-cp", compiler_cp,
                   "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect",
                   "-jvm-target", "11", "-classpath", runtime_cp, "-d", str(classes)]
        command.extend(str(path) for path in sources)
        run_step("compile", command, repo, output, report, 180)
        test_cp = str(classes) + os.pathsep + runtime_cp
        test_command = [java, "-Xmx512m", f"-Djava.io.tmpdir={temp}", "-cp", test_cp,
                        "org.junit.runner.JUnitCore"] + [PACKAGE + "." + name for name in TEST_NAMES]
        junit_output = run_step("junit", test_command, repo, output, report, 120)
        record_junit_summary(junit_output, report)
        if report["tests_run"] != EXPECTED_TESTS:
            raise RunnerError("JUnit success count did not match 14; see junit.log.")
        if any(sha256(repo / item["path"]) != item["sha256"] for item in report["sources"]):
            raise RunnerError("BRAVIA source changed during validation; result is not accepted.")
        report["status"] = "passed"
    except (RunnerError, OSError, ValueError, KeyError) as error:
        report["error"] = str(error)
        junit_log = output / "junit.log"
        if junit_log.is_file():
            record_junit_summary(junit_log.read_text(errors="replace"), report)
    finally:
        report["finished_at"] = dt.datetime.now(dt.timezone.utc).isoformat()
        try:
            (output / "result.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
        except OSError as error:
            print(f"Unable to save result.json: {error}", file=sys.stderr)
            return 1
    print(f"BRAVIA host JVM: {report['status']}; {output / 'result.json'}")
    if "error" in report:
        print(report["error"], file=sys.stderr)
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
