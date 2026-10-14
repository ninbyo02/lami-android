"""Compile the real Android-free speech pipeline and run JVM tests, offline."""
import argparse
import json
import os
import subprocess
import tempfile
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=Path, default=Path.home() / ".gradle/caches/modules-2/files-2.1")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--replay-fixture", type=Path)
    parser.add_argument("--pcm-fixture", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    def jar(group, name, version):
        files = sorted((args.cache / group / name / version).glob("*/*.jar"))
        if len(files) != 1:
            raise RuntimeError(f"Expected one cached jar: {group}:{name}:{version}")
        return str(files[0])
    kotlin = "2.3.0"
    stdlib = jar("org.jetbrains.kotlin", "kotlin-stdlib", kotlin)
    core = jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.9.0")
    runtime = [
        stdlib, core,
        jar("org.jetbrains.kotlinx", "kotlinx-coroutines-test-jvm", "1.9.0"),
        jar("junit", "junit", "4.13.2"),
        jar("org.hamcrest", "hamcrest-core", "1.3"),
        jar("org.jetbrains", "annotations", "23.0.0"),
    ]
    compiler = [
        jar("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"),
        jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", kotlin),
        jar("org.jetbrains.kotlin", "kotlin-script-runtime", kotlin),
        jar("org.jetbrains.kotlin", "kotlin-daemon-embeddable", kotlin),
    ] + runtime
    sources = [
        root / "app/src/main/java/io/github/ninbyo02/lami/tts/LamiSpeechPipeline.kt",
        root / "app/src/test/java/io/github/ninbyo02/lami/tts/LamiSpeechPipelineTest.kt",
        root / "app/src/test/java/io/github/ninbyo02/lami/tts/LamiSpeechPipelineHostFailureTest.kt",
    ]
    if args.replay_fixture:
        sources.append(root / "app/src/test/java/io/github/ninbyo02/lami/tts/LamiSpeechPipelineRecordedReplayTest.kt")
        if not args.replay_fixture.is_file():
            raise ValueError("Replay fixture missing")
    if args.pcm_fixture:
        sources.append(root / "app/src/test/java/io/github/ninbyo02/lami/tts/LamiSpeechPipelinePcmReplayTest.kt")
        if not args.pcm_fixture.is_file():
            raise ValueError("PCM fixture missing")
    with tempfile.TemporaryDirectory(prefix="lami-pipeline-host-") as directory:
        compiled = subprocess.run(
            ["java", "-cp", os.pathsep.join(compiler),
             "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect",
             "-classpath", os.pathsep.join(runtime), "-d", directory] + list(map(str, sources)),
            capture_output=True, text=True, timeout=90,
        )
        tested = None
        if compiled.returncode == 0:
            tested = subprocess.run(
                ["java"] + (["-Dlami.pcm.fixture=" + str(args.pcm_fixture.resolve())] if args.pcm_fixture else []) + (["-Dlami.replay.fixture=" + str(args.replay_fixture.resolve())] if args.replay_fixture else []) + ["-cp", os.pathsep.join([directory] + runtime), "org.junit.runner.JUnitCore",
                 "io.github.ninbyo02.lami.tts.LamiSpeechPipelineTest",
                 "io.github.ninbyo02.lami.tts.LamiSpeechPipelineHostFailureTest"] + (["io.github.ninbyo02.lami.tts.LamiSpeechPipelineRecordedReplayTest"] if args.replay_fixture else []) + (["io.github.ninbyo02.lami.tts.LamiSpeechPipelinePcmReplayTest"] if args.pcm_fixture else []),
                capture_output=True, text=True, timeout=60,
            )
    success = compiled.returncode == 0 and tested is not None and tested.returncode == 0
    report = {"host_pipeline_tests": "passed" if success else "failed",
              "compile_output": compiled.stdout + compiled.stderr,
              "test_output": None if tested is None else tested.stdout + tested.stderr,
              "npu_execution": "not_run", "android_audio": "not_run",
              "limitations": ["Real queue/cancellation code tested with injected preparation and playback.",
                              "No JNI, driver crash, memory pressure or hardware timing reproduction."]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if success else 1


if __name__ == "__main__":
    raise SystemExit(main())
