#!/usr/bin/env python3
"""Fixed, zero-argument M10 restoration-map acceptance smoke for Build PC emulator."""

import fcntl
import hashlib
import json
import os
import re
import stat
import subprocess
import sys

import time
import xml.etree.ElementTree as ET
from pathlib import Path

EXPECTED_COMMIT = "1c32d6b83789d46763278daf02d0b0d972d535ed"
EXPECTED_APK_SHA256 = "33251167a53792e9f9a040b2a9a0c757d69678ee6a966abdd9fe7bdd6b33962a"
APK_PATH = Path(__file__).resolve().with_name("app-debug.apk")
SERIAL = "emulator-5554"
PACKAGE = "io.github.ninbyo02.lami.chronicle"
COMPONENT = f"{PACKAGE}/.app.MainActivity"
CHILD_QUEST_ID = "magnetic-field-tower-restoration"
ADB = Path.home() / "lami-android-sdk/platform-tools/adb"
ARTIFACT_ROOT = Path.home() / "build-logs/chronicle-m10-map-smoke"
MAX_APK_BYTES = 64 * 1024 * 1024
MAX_PNG_BYTES = 8 * 1024 * 1024
MAX_XML_BYTES = 2 * 1024 * 1024
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
REMOTE_XML = "/data/local/tmp/chronicle-m10-map-smoke.xml"
REMOTE_SEED = "/data/local/tmp/chronicle-m10-map-seed.json"


class SmokeFailure(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SmokeFailure(message)


def adb(*args: str, input_bytes: bytes | None = None, pass_fds: tuple[int, ...] = (), text: bool = True):
    cmd = [str(ADB), "-s", SERIAL, *args]
    result = subprocess.run(
        cmd,
        input=input_bytes,
        capture_output=True,
        text=text if input_bytes is None else False,
        check=False,
        timeout=180,
        pass_fds=pass_fds,
    )
    if result.returncode != 0:
        stderr = result.stderr if isinstance(result.stderr, str) else result.stderr.decode("utf-8", "replace")
        raise SmokeFailure(f"fixed adb operation failed: {args[0] if args else 'unknown'}: {stderr[-500:]}")
    return result.stdout


def write_all(fd: int, data: bytes) -> None:
    view = memoryview(data)
    while view:
        try:
            written = os.write(fd, view)
        except InterruptedError:
            continue
        if written <= 0:
            raise SmokeFailure("memfd write made no progress")
        view = view[written:]


def hash_fd(fd: int) -> str:
    os.lseek(fd, 0, os.SEEK_SET)
    digest = hashlib.sha256()
    while True:
        chunk = os.read(fd, 1024 * 1024)
        if not chunk:
            break
        digest.update(chunk)
    return digest.hexdigest()


def emulator_preflight() -> None:
    require(ADB.is_file() and os.access(ADB, os.X_OK), "fixed adb binary unavailable")
    require(adb("get-state").strip() == "device", "emulator-5554 is not a device")
    require(adb("shell", "getprop", "sys.boot_completed").strip() == "1", "emulator boot incomplete")
    require(adb("shell", "getprop", "ro.kernel.qemu").strip() == "1", "target is not an emulator")


def download_and_install() -> int:
    source_fd = os.open(APK_PATH, os.O_RDONLY | os.O_CLOEXEC | os.O_NOFOLLOW)
    fd = os.memfd_create("lami-chronicle-m10-apk", os.MFD_CLOEXEC | os.MFD_ALLOW_SEALING)
    digest = hashlib.sha256()
    total = 0
    try:
        before = os.fstat(source_fd)
        path_before = os.lstat(APK_PATH)
        require(stat.S_ISREG(before.st_mode), "APK source is not a regular file")
        require(before.st_nlink == 1, "APK source hardlink count invalid")
        require((before.st_dev, before.st_ino) == (path_before.st_dev, path_before.st_ino), "APK source identity mismatch")
        require(0 < before.st_size <= MAX_APK_BYTES, "APK source size invalid")
        while True:
            chunk = os.read(source_fd, 1024 * 1024)
            if not chunk:
                break
            total += len(chunk)
            require(total <= MAX_APK_BYTES, "APK exceeds fixed byte ceiling")
            digest.update(chunk)
            write_all(fd, chunk)
        require(total > 0, "empty APK download")
        digest = digest.hexdigest()
        if digest != EXPECTED_APK_SHA256:
            raise SmokeFailure("APK SHA-256 mismatch")
        after = os.fstat(source_fd)
        path_after = os.lstat(APK_PATH)
        identity = lambda item: (item.st_dev, item.st_ino, item.st_size, item.st_mtime_ns, item.st_ctime_ns)
        require(identity(before) == identity(after), "APK source changed during snapshot")
        require((after.st_dev, after.st_ino) == (path_after.st_dev, path_after.st_ino), "APK path changed during snapshot")
        os.fsync(fd)
        require(os.fstat(fd).st_size == total, "APK memfd size mismatch")
        require(hash_fd(fd) == EXPECTED_APK_SHA256, "APK memfd hash mismatch")
        required_seals = fcntl.F_SEAL_SEAL | fcntl.F_SEAL_SHRINK | fcntl.F_SEAL_GROW | fcntl.F_SEAL_WRITE
        fcntl.fcntl(fd, fcntl.F_ADD_SEALS, required_seals)
        require((fcntl.fcntl(fd, fcntl.F_GET_SEALS) & required_seals) == required_seals, "APK memfd not sealed")
        sealed = os.fstat(fd)
        require(sealed.st_size == total, "sealed APK memfd size mismatch")
        require(hash_fd(fd) == EXPECTED_APK_SHA256, "sealed APK memfd hash mismatch")
        adb("install", "-r", "-t", f"/proc/self/fd/{fd}", pass_fds=(fd,))
        installed = os.fstat(fd)
        require(
            (installed.st_dev, installed.st_ino, installed.st_size)
            == (sealed.st_dev, sealed.st_ino, sealed.st_size),
            "sealed APK memfd identity changed during install",
        )
        require(hash_fd(fd) == EXPECTED_APK_SHA256, "sealed APK memfd hash changed during install")
        return total
    finally:
        os.close(source_fd)
        os.close(fd)


def start_app() -> None:
    adb("shell", "am", "start", "-W", "-n", COMPONENT)
    time.sleep(2)
    pid = adb("shell", "pidof", PACKAGE).strip()
    require(bool(pid), "Chronicle process did not start")


def force_stop() -> None:
    adb("shell", "am", "force-stop", PACKAGE)


def swipe_up() -> None:
    adb("shell", "input", "swipe", "540", "1900", "540", "500", "350")
    time.sleep(0.5)


def swipe_down() -> None:
    adb("shell", "input", "swipe", "540", "500", "540", "1900", "350")
    time.sleep(0.5)


def pull_ui_xml(destination: Path) -> str:
    adb("shell", "uiautomator", "dump", REMOTE_XML)
    tmp = destination.with_name(destination.name + ".pull-tmp")
    tmp.unlink(missing_ok=True)
    adb("pull", REMOTE_XML, str(tmp))
    require(tmp.is_file() and 0 < tmp.stat().st_size <= MAX_XML_BYTES, "UI XML size invalid")
    raw = tmp.read_text(encoding="utf-8")
    ET.fromstring(raw)
    os.chmod(tmp, 0o600)
    os.replace(tmp, destination)
    return raw


def capture_stage(stage: str, *required_fragments: str, max_swipes: int = 6) -> str:
    require(re.fullmatch(r"[0-9]{2}_[a-z0-9_]+", stage) is not None, "invalid fixed stage")
    xml = out_dir / f"{stage}.xml"
    png = out_dir / f"{stage}.png"
    candidate_xml = out_dir / ".capture.xml"
    raw = ""
    for attempt in range(max_swipes + 1):
        raw = pull_ui_xml(candidate_xml)
        if all(fragment in raw for fragment in required_fragments):
            break
        if attempt < max_swipes:
            swipe_up()
    else:
        raise SmokeFailure(f"stage {stage} missing required UI oracle")
    screenshot = adb("exec-out", "screencap", "-p", text=False)
    require(isinstance(screenshot, bytes), "screenshot type invalid")
    require(screenshot.startswith(PNG_SIGNATURE), "screenshot PNG signature invalid")
    require(0 < len(screenshot) <= MAX_PNG_BYTES, "screenshot size invalid")
    verification_xml = out_dir / ".verify.xml"
    verification_raw = pull_ui_xml(verification_xml)
    verification_xml.unlink(missing_ok=True)
    require(
        all(fragment in verification_raw for fragment in required_fragments),
        f"stage {stage} changed during screenshot",
    )
    tmp_png = png.with_name(png.name + ".tmp")
    tmp_xml = xml.with_name(xml.name + ".publish-tmp")
    tmp_png.write_bytes(screenshot)
    tmp_xml.write_text(raw, encoding="utf-8")
    os.chmod(tmp_png, 0o600)
    os.chmod(tmp_xml, 0o600)
    os.replace(tmp_png, png)
    os.replace(tmp_xml, xml)
    candidate_xml.unlink(missing_ok=True)
    os.chmod(png, 0o600)
    os.chmod(xml, 0o600)
    print(f"stage={stage} png={png} png_sha256={hashlib.sha256(screenshot).hexdigest()} xml={xml}")
    return raw


def _nodes_with_parents(raw: str):
    root = ET.fromstring(raw)
    parents = {child: parent for parent in root.iter() for child in parent}
    return root, parents


def _bounds(node) -> tuple[int, int]:
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if match is None:
        raise SmokeFailure("UI node has no fixed bounds")
    x1, y1, x2, y2 = map(int, match.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap_node(*, text_value: str | None = None, desc_value: str | None = None, class_name: str | None = None, max_swipes: int = 7) -> None:
    tap_xml = out_dir / ".tap.xml"
    search_moves = [None, *(["down"] * max_swipes), *(["up"] * (max_swipes * 2))]
    for move in search_moves:
        if move == "down":
            swipe_down()
        elif move == "up":
            swipe_up()
        raw = pull_ui_xml(tap_xml)
        root, parents = _nodes_with_parents(raw)
        for node in root.iter("node"):
            if text_value is not None and node.attrib.get("text") != text_value:
                continue
            if desc_value is not None and node.attrib.get("content-desc") != desc_value:
                continue
            if class_name is not None and node.attrib.get("class") != class_name:
                continue
            target = node
            while target is not None and target.attrib.get("clickable") != "true":
                target = parents.get(target)
            if target is None:
                target = node
            x, y = _bounds(target)
            adb("shell", "input", "tap", str(x), str(y))
            time.sleep(0.8)
            return
    raise SmokeFailure(f"fixed UI target unavailable: {text_value or desc_value or class_name}")


def answer_choice(choice: str) -> None:
    tap_node(text_value=choice)
    tap_node(text_value="回答する")
    tap_node(text_value="解説を確認して次へ")


def choice_record(question_id: str, choice: str) -> dict:
    return {
        "questionId": question_id,
        "selectedChoiceId": choice,
        "isCorrect": True,
        "explanationAcknowledged": True,
    }


def numeric_record(question_id: str, value: str) -> dict:
    return {
        "questionId": question_id,
        "selectedChoiceId": "",
        "isCorrect": True,
        "explanationAcknowledged": True,
        "numericAnswer": value,
    }


def fixed_unlock_seed() -> dict:
    choices = [
        ("dc-ohms-law", "d"), ("dc-series-resistance", "e"), ("dc-power", "c"),
        ("dc-parallel-6-3", "a"), ("dc-parallel-4-12", "b"), ("dc-voltage-divider", "c"),
        ("dc-current-divider", "d"), ("dc-parallel-branch-current", "e"), ("dc-kcl-node-1", "a"),
        ("dc-kcl-node-2", "b"), ("dc-kvl-loop-1", "c"), ("dc-kvl-loop-2", "d"),
        ("dc-series-parallel-integration", "e"), ("es-charge-unit", "b"), ("es-field-force", "d"),
        ("es-field-from-force", "a"), ("es-uniform-field-voltage", "c"), ("es-work-from-voltage", "e"),
        ("es-capacitor-charge-choice", "b"), ("es-capacitor-voltage-choice", "d"),
        ("es-capacitor-dielectric", "a"), ("es-parallel-capacitance-choice", "c"),
        ("es-series-capacitance-choice", "e"), ("es-series-equal-capacitors", "b"),
        ("es-capacitor-energy-choice", "d"), ("es-energy-density-field", "a"),
        ("es-electron-field-direction", "c"),
    ]
    numerics = [
        ("numeric-ohms-voltage", "48 V"), ("numeric-parallel-resistance", "6 Ω"),
        ("numeric-power-current", "5 A"), ("es-numeric-field-force", "0.006 N"),
        ("es-numeric-capacitor-charge", "0.000072 C"), ("es-numeric-series-capacitance", "2 μF"),
        ("es-numeric-uniform-field", "8000 V/m"), ("es-numeric-capacitor-energy", "0.002 J"),
        ("es-numeric-coulomb-force", "0.006 N"),
    ]
    return {
        "schemaVersion": 1,
        "profileId": "local-profile",
        "activeQuestId": "control-room-restoration",
        "answers": [choice_record(*item) for item in choices] + [numeric_record(*item) for item in numerics],
        "restoredFacilityIds": [],
        "acknowledgedLearningCardIds": [],
        "questionMasteries": [],
    }


def install_seed() -> None:
    seed = fixed_unlock_seed()
    seed_path = out_dir / "fixed-unlock-seed.json"
    seed_path.write_text(json.dumps(seed, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    os.chmod(seed_path, 0o600)
    adb("push", str(seed_path), REMOTE_SEED)
    adb("shell", "run-as", PACKAGE, "sh", "-c", f"cat {REMOTE_SEED} > files/chronicle-save.json")
    adb("shell", "rm", "-f", REMOTE_SEED)


def publish_success() -> None:
    artifacts = {}
    for artifact in sorted(out_dir.iterdir()):
        if artifact.name.startswith(".") or not artifact.is_file() or artifact.is_symlink():
            continue
        artifacts[artifact.name] = hashlib.sha256(artifact.read_bytes()).hexdigest()
    manifest = {
        "status": "PASS",
        "runId": out_dir.name,
        "commit": EXPECTED_COMMIT,
        "apkSha256": EXPECTED_APK_SHA256,
        "artifacts": artifacts,
    }
    manifest_tmp = out_dir / ".SUCCESS.json.tmp"
    manifest_path = out_dir / "SUCCESS.json"
    with manifest_tmp.open("x", encoding="utf-8") as handle:
        json.dump(manifest, handle, ensure_ascii=False, sort_keys=True)
        handle.write("\n")
        handle.flush()
        os.fsync(handle.fileno())
    os.chmod(manifest_tmp, 0o600)
    os.replace(manifest_tmp, manifest_path)
    out_fd = os.open(out_dir, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(out_fd)
    finally:
        os.close(out_fd)
    latest = ARTIFACT_ROOT / "latest"
    latest_tmp = ARTIFACT_ROOT / ".latest.tmp"
    latest_tmp.unlink(missing_ok=True)
    latest_tmp.symlink_to(out_dir.name)
    os.replace(latest_tmp, latest)
    root_fd = os.open(ARTIFACT_ROOT, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(root_fd)
    finally:
        os.close(root_fd)


def run_smoke() -> None:
    apk_size = download_and_install()
    adb("shell", "pm", "clear", PACKAGE)
    start_app()
    capture_stage(
        "20_locked_home",
        "復旧マップ",
        "磁界測定塔・基礎復旧",
        "状態：未解放",
        "解放条件：発電所制御室、静電界観測室を復旧",
    )

    force_stop()
    install_seed()
    start_app()
    capture_stage(
        "21_unlocked_home",
        "磁界測定塔・基礎復旧",
        "状態：復旧可能",
    )
    tap_node(desc_value="磁界測定塔・基礎復旧 復旧可能")

    force_stop()
    start_app()
    for _ in range(8):
        swipe_down()
    capture_stage(
        "22_child_selected_after_restart",
        "磁界測定塔・基礎復旧",
        "状態：復旧可能・選択中",
    )
    for _ in range(8):
        swipe_down()
    tap_node(text_value="クエスト開始")
    tap_node(text_value="理解した")
    capture_stage("23_child_unique_question", "紙面に垂直な直線導体")

    answer_choice("反時計回り")
    answer_choice("4倍")
    tap_node(text_value="理解した")
    answer_choice("紙面の奥向き")
    answer_choice("0.30 N")
    tap_node(class_name="android.widget.EditText")
    adb("shell", "input", "text", "0.300N")
    adb("shell", "input", "keyevent", "4")
    tap_node(text_value="回答する")
    tap_node(text_value="解説を確認して次へ")
    capture_stage(
        "24_child_result",
        "磁界測定塔・基礎復旧：完了",
        "正答率 100%（5/5）",
        "復旧状態：復旧済み",
    )
    force_stop()
    adb("shell", "rm", "-f", REMOTE_XML, REMOTE_SEED)
    (out_dir / ".tap.xml").unlink(missing_ok=True)
    publish_success()
    print(f"chronicle_commit={EXPECTED_COMMIT}")
    print(f"chronicle_child_quest_id={CHILD_QUEST_ID}")
    print(f"chronicle_apk_sha256={EXPECTED_APK_SHA256}")
    print(f"chronicle_apk_size={apk_size}")
    print(f"chronicle_m10_artifact={out_dir}")
    print("chronicle_locked_state=ok")
    print("chronicle_derived_unlock=ok")
    print("chronicle_child_selection_restart=ok")
    print("chronicle_child_unique_content=ok")
    print("chronicle_child_result=ok")
    print("== CHRONICLE M10 MAP SMOKE OK ==")


def main() -> int:
    global out_dir
    if sys.argv[1:]:
        raise SystemExit("this helper accepts no arguments")
    stamp = time.strftime("%Y%m%d-%H%M%S", time.gmtime())
    ARTIFACT_ROOT.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(ARTIFACT_ROOT, 0o700)
    latest = ARTIFACT_ROOT / "latest"
    if latest.exists() or latest.is_symlink():
        require(latest.is_symlink(), "latest artifact pointer is not a symlink")
        latest.unlink()
        root_fd = os.open(ARTIFACT_ROOT, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(root_fd)
        finally:
            os.close(root_fd)
    out_dir = ARTIFACT_ROOT / stamp
    out_dir.mkdir(mode=0o700)
    try:
        emulator_preflight()
        run_smoke()
        return 0
    except Exception as exc:
        if ADB.exists():
            for cleanup in (
                ["shell", "am", "force-stop", PACKAGE],
                ["shell", "rm", "-f", REMOTE_XML, REMOTE_SEED],
            ):
                subprocess.run(
                    [str(ADB), "-s", SERIAL, *cleanup],
                    capture_output=True,
                    text=True,
                    check=False,
                    timeout=30,
                )
        print(f"CHRONICLE_M10_SMOKE_FAILED type={type(exc).__name__} reason={str(exc)[:500]}", file=sys.stderr)
        return 65


if __name__ == "__main__":
    raise SystemExit(main())
