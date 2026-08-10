#!/usr/bin/env python3
"""Atomic root installer for the laptop deploy-future-standard successor."""
from __future__ import annotations

from dataclasses import dataclass
import ast
import fcntl
import hashlib
import os
from pathlib import Path
import pwd
import secrets
import stat
import subprocess
import sys

BASELINE_SHA256 = "cb863e73ed1a72fe586dbe92d0a93eb413f666fca534b4a0cad0aaf060be0473"
# build_package.py must stamp this from deploy_future_standard.py before hashing install.py.
# There is no hash cycle: this pins the separate extension, not the installer itself.
SOURCE_EXTENSION_SHA256 = "c5acc73acb51eddee196172a384f4e293a7225b7a5ee905f63b504e62be5ecc7"
GATE_PATH = Path("/usr/local/sbin/lami-build-gate")
EXTENSION_PATH = Path("/usr/local/libexec/lami-deploy-future-standard.py")
BACKUP_PATH = Path(f"/usr/local/sbin/lami-build-gate.pre-deploy-future-standard-{BASELINE_SHA256[:12]}.bak")
SOURCE_EXTENSION = Path(__file__).with_name("deploy_future_standard.py")
BUILD_LOCK_PATH = Path("/home/lami-build/.build.lock")


class InstallError(RuntimeError):
    pass


@dataclass(frozen=True)
class InstallResult:
    status: str
    gate_sha256: str
    extension_sha256: str
    backup_path: Path


@dataclass
class AtomicReplaceState:
    rename_completed: bool = False


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _write_all(fd: int, data: bytes) -> None:
    view = memoryview(data)
    while view:
        try:
            written = os.write(fd, view)
        except InterruptedError:
            continue
        if written <= 0:
            raise OSError("write made no progress")
        view = view[written:]


def _replace_once(data: bytes, old: bytes, new: bytes, label: str) -> bytes:
    count = data.count(old)
    if count != 1:
        raise InstallError(f"baseline anchor {label!r} count is {count}, expected 1")
    return data.replace(old, new, 1)


def transform_gate(baseline: bytes, extension_sha256: str) -> bytes:
    if sha256_bytes(baseline) != BASELINE_SHA256:
        raise InstallError("gate baseline SHA-256 mismatch")
    if not extension_sha256 or len(extension_sha256) != 64:
        raise InstallError("extension SHA-256 is invalid")
    constants_anchor = (
        b'QAIRT_EXTENSION_SHA256="99631431604344db84bd09d185c81bd4533698054ebadf235b8df904d134659c"\n'
    )
    constants = constants_anchor + (
        b'\nDEPLOY_EXTENSION="/usr/local/libexec/lami-deploy-future-standard.py"\n'
        + f'DEPLOY_EXTENSION_SHA256="{extension_sha256}"\n'.encode()
    )
    baseline = _replace_once(baseline, constants_anchor, constants, "extension constants")

    function_anchor = b"valid_ref() {\n"
    function = b'''run_deploy_future_standard() {
  local port="$1"
  local actual_sha metadata
  [[ -f "${DEPLOY_EXTENSION}" && ! -L "${DEPLOY_EXTENSION}" ]] || {
    echo "deploy extension is missing or unsafe." >&2
    exit 66
  }
  metadata="$(/usr/bin/stat -c '%u:%g:%a:%h' -- "${DEPLOY_EXTENSION}")"
  [[ "${metadata}" == "0:0:755:1" ]] || {
    echo "deploy extension metadata mismatch." >&2
    exit 65
  }
  actual_sha="$(/usr/bin/sha256sum -- "${DEPLOY_EXTENSION}")"
  actual_sha="${actual_sha%% *}"
  [[ "${actual_sha}" == "${DEPLOY_EXTENSION_SHA256}" ]] || {
    echo "deploy extension SHA-256 mismatch." >&2
    exit 65
  }
  exec /usr/bin/env -i \
    HOME="/home/lami-build" \
    PATH="/usr/bin:/bin" \
    ADB_SERVER_SOCKET="tcp:127.0.0.1:5037" \
    /usr/bin/python3 -I -E -s "${DEPLOY_EXTENSION}" "${port}"
}

valid_ref() {
'''
    baseline = _replace_once(baseline, function_anchor, function, "deploy function")

    case_anchor = b'case "${cmd}" in\n'
    dispatch = br'''case "${cmd}" in
  deploy-future-standard\ *)
    if [[ "${cmd}" =~ ^deploy-future-standard\ ([1-9][0-9]{0,4})$ ]] &&
       (( 10#${BASH_REMATCH[1]} >= 1 && 10#${BASH_REMATCH[1]} <= 65535 )); then
      run_deploy_future_standard "${BASH_REMATCH[1]}"
    fi
    echo "not allowed: ${cmd}" >&2
    exit 64
    ;;
'''
    baseline = _replace_once(baseline, case_anchor, dispatch, "outer dispatch")

    help_anchor = b"  fetch-standard-debug-apk\nEOF_USAGE\n"
    help_text = b"  fetch-standard-debug-apk\n  deploy-future-standard <port>\nEOF_USAGE\n"
    return _replace_once(baseline, help_anchor, help_text, "help")


def _fsync_dir(path: Path) -> None:
    fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY | os.O_CLOEXEC)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def _validate_parent(path: Path, expected_uid: int, expected_gid: int) -> None:
    info = path.parent.lstat()
    if not stat.S_ISDIR(info.st_mode) or path.parent.resolve() != path.parent:
        raise InstallError(f"parent is not a real directory: {path.parent}")
    if info.st_uid != expected_uid or info.st_gid != expected_gid:
        raise InstallError(f"parent owner mismatch: {path.parent}")
    if stat.S_IMODE(info.st_mode) & 0o022:
        raise InstallError(f"parent is group/world writable: {path.parent}")


def _secure_file_bytes(
    path: Path,
    expected_uid: int,
    expected_gid: int,
    *,
    required_mode=0o755,
    expected_sha256: str | None = None,
) -> bytes:
    try:
        info = path.lstat()
    except FileNotFoundError as exc:
        raise InstallError(f"required file is missing: {path}") from exc
    if stat.S_ISLNK(info.st_mode):
        raise InstallError(f"refusing symlink: {path}")
    if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1:
        raise InstallError(f"file is not a single-link regular file: {path}")
    if info.st_uid != expected_uid or info.st_gid != expected_gid:
        raise InstallError(f"file owner mismatch: {path}")
    if stat.S_IMODE(info.st_mode) != required_mode:
        raise InstallError(f"file mode mismatch: {path}")
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC)
    try:
        opened = os.fstat(fd)
        current = path.lstat()
        if (
            (opened.st_dev, opened.st_ino) != (info.st_dev, info.st_ino)
            or (current.st_dev, current.st_ino) != (info.st_dev, info.st_ino)
            or not stat.S_ISREG(opened.st_mode)
            or opened.st_nlink != 1
            or opened.st_uid != expected_uid
            or opened.st_gid != expected_gid
            or stat.S_IMODE(opened.st_mode) != required_mode
        ):
            raise InstallError(f"file inode changed or opened metadata mismatch: {path}")
        chunks = []
        while True:
            chunk = os.read(fd, 1024 * 1024)
            if not chunk:
                break
            chunks.append(chunk)
        data = b"".join(chunks)
        final_opened = os.fstat(fd)
        final_current = path.lstat()
        if (
            (final_opened.st_dev, final_opened.st_ino) != (info.st_dev, info.st_ino)
            or (final_current.st_dev, final_current.st_ino) != (info.st_dev, info.st_ino)
            or final_opened.st_size != len(data)
        ):
            raise InstallError(f"file inode changed while reading: {path}")
        if expected_sha256 is not None and sha256_bytes(data) != expected_sha256:
            raise InstallError("source extension SHA-256 mismatch")
        return data
    finally:
        os.close(fd)


def _atomic_replace(
    path: Path,
    data: bytes,
    uid: int,
    gid: int,
    mode: int = 0o755,
    *,
    state: AtomicReplaceState | None = None,
) -> None:
    if state is None:
        state = AtomicReplaceState()
    temporary = path.parent / f".{path.name}.new-{os.getpid()}-{secrets.token_hex(8)}"
    fd = os.open(
        temporary,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW | os.O_CLOEXEC,
        0o600,
    )
    try:
        _write_all(fd, data)
        os.fchmod(fd, mode)
        os.fchown(fd, uid, gid)
        os.fsync(fd)
    except BaseException:
        os.close(fd)
        temporary.unlink(missing_ok=True)
        raise
    else:
        os.close(fd)
    try:
        os.replace(temporary, path)
        state.rename_completed = True
        _fsync_dir(path.parent)
    finally:
        temporary.unlink(missing_ok=True)


def _syntax_check_gate(candidate: bytes, directory: Path) -> None:
    path = directory / f".lami-build-gate.syntax-{os.getpid()}-{secrets.token_hex(8)}"
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW | os.O_CLOEXEC, 0o600)
    try:
        _write_all(fd, candidate)
        os.fsync(fd)
    finally:
        os.close(fd)
    try:
        result = subprocess.run(
            ("/usr/bin/bash", "-n", str(path)),
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            env={"PATH": "/usr/bin:/bin", "LC_ALL": "C"},
            timeout=10,
            check=False,
        )
        if result.returncode != 0:
            raise InstallError("generated gate failed bash -n: " + result.stdout.decode(errors="replace"))
    finally:
        path.unlink(missing_ok=True)


def _acquire_build_lock(path: Path, uid: int, gid: int) -> tuple[int, int]:
    fd = None
    try:
        before = path.lstat()
    except OSError as exc:
        raise InstallError("build lock is missing or unsafe") from exc
    mode = stat.S_IMODE(before.st_mode)
    if (
        not stat.S_ISREG(before.st_mode)
        or before.st_nlink != 1
        or before.st_uid != uid
        or before.st_gid != gid
        or mode not in (0o600, 0o664)
    ):
        raise InstallError("build lock metadata is unsafe")
    try:
        fd = os.open(path, os.O_RDWR | os.O_NOFOLLOW | os.O_CLOEXEC)
        opened = os.fstat(fd)
        current = path.lstat()
        if (
            (opened.st_dev, opened.st_ino) != (before.st_dev, before.st_ino)
            or (current.st_dev, current.st_ino) != (before.st_dev, before.st_ino)
            or not stat.S_ISREG(opened.st_mode)
            or opened.st_nlink != 1
            or opened.st_uid != uid
            or opened.st_gid != gid
            or stat.S_IMODE(opened.st_mode) != mode
        ):
            raise InstallError("build lock inode changed")
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        locked = os.fstat(fd)
        current = path.lstat()
        if (
            (locked.st_dev, locked.st_ino) != (before.st_dev, before.st_ino)
            or (current.st_dev, current.st_ino) != (before.st_dev, before.st_ino)
            or not stat.S_ISREG(locked.st_mode)
            or locked.st_nlink != 1
            or locked.st_uid != uid
            or locked.st_gid != gid
            or stat.S_IMODE(locked.st_mode) != mode
            or not stat.S_ISREG(current.st_mode)
            or current.st_nlink != 1
            or current.st_uid != uid
            or current.st_gid != gid
            or stat.S_IMODE(current.st_mode) != mode
        ):
            raise InstallError("build lock inode changed after flock")
    except (OSError, BlockingIOError) as exc:
        if fd is not None:
            os.close(fd)
        raise InstallError("build lock is contended or unsafe") from exc
    except BaseException:
        if fd is not None:
            os.close(fd)
        raise
    return fd, mode


def _install_package_locked(
    *,
    gate_path: Path,
    extension_path: Path,
    backup_path: Path,
    source_extension: Path,
    expected_uid: int,
    expected_gid: int,
    source_expected_uid: int | None = None,
    source_expected_gid: int | None = None,
    after_extension=None,
) -> InstallResult:
    for path in (gate_path, extension_path, backup_path):
        _validate_parent(path, expected_uid, expected_gid)
    if source_expected_uid is None:
        source_expected_uid = expected_uid
    if source_expected_gid is None:
        source_expected_gid = expected_gid
    extension_bytes = _secure_file_bytes(
        source_extension,
        source_expected_uid,
        source_expected_gid,
        required_mode=0o644,
        expected_sha256=SOURCE_EXTENSION_SHA256,
    )
    try:
        ast.parse(extension_bytes, filename=str(source_extension))
    except SyntaxError as exc:
        raise InstallError("extension is not valid Python") from exc
    extension_sha = SOURCE_EXTENSION_SHA256

    gate_bytes = _secure_file_bytes(gate_path, expected_uid, expected_gid)
    if sha256_bytes(gate_bytes) == BASELINE_SHA256:
        baseline = gate_bytes
    else:
        fixture = Path(__file__).with_name("tests") / "fixtures" / "lami-build-gate.cb863e73"
        baseline = fixture.read_bytes()
        expected_gate = transform_gate(baseline, extension_sha)
        if gate_bytes != expected_gate:
            raise InstallError("gate baseline SHA-256 mismatch and successor verification failed")
        installed_extension = _secure_file_bytes(extension_path, expected_uid, expected_gid)
        if installed_extension != extension_bytes:
            raise InstallError("installed extension differs from package")
        return InstallResult("already-installed", sha256_bytes(gate_bytes), extension_sha, backup_path)

    candidate_gate = transform_gate(baseline, extension_sha)
    _syntax_check_gate(candidate_gate, gate_path.parent)

    if backup_path.exists() or backup_path.is_symlink():
        backup_bytes = _secure_file_bytes(backup_path, expected_uid, expected_gid)
        if backup_bytes != baseline:
            raise InstallError("existing backup does not match exact baseline")
    else:
        _atomic_replace(backup_path, baseline, expected_uid, expected_gid)

    old_extension = None
    if extension_path.exists() or extension_path.is_symlink():
        old_extension = _secure_file_bytes(extension_path, expected_uid, expected_gid)

    extension_state = AtomicReplaceState()
    gate_state = AtomicReplaceState()
    try:
        _atomic_replace(
            extension_path,
            extension_bytes,
            expected_uid,
            expected_gid,
            state=extension_state,
        )
        if after_extension is not None:
            after_extension()
        _atomic_replace(
            gate_path,
            candidate_gate,
            expected_uid,
            expected_gid,
            state=gate_state,
        )
        if _secure_file_bytes(extension_path, expected_uid, expected_gid) != extension_bytes:
            raise InstallError("post-install extension verification failed")
        if _secure_file_bytes(gate_path, expected_uid, expected_gid) != candidate_gate:
            raise InstallError("post-install gate verification failed")
    except BaseException:
        rollback_errors = []
        try:
            if gate_state.rename_completed:
                _atomic_replace(gate_path, baseline, expected_uid, expected_gid)
        except BaseException as exc:
            rollback_errors.append(f"gate rollback: {exc}")
        try:
            if extension_state.rename_completed:
                if old_extension is None:
                    extension_path.unlink(missing_ok=True)
                    _fsync_dir(extension_path.parent)
                else:
                    _atomic_replace(extension_path, old_extension, expected_uid, expected_gid)
        except BaseException as exc:
            rollback_errors.append(f"extension rollback: {exc}")
        if rollback_errors:
            raise InstallError("ROLLBACK FAILED: " + "; ".join(rollback_errors))
        raise

    return InstallResult("installed", sha256_bytes(candidate_gate), extension_sha, backup_path)


def install_package(
    *,
    gate_path: Path,
    extension_path: Path,
    backup_path: Path,
    source_extension: Path,
    expected_uid: int,
    expected_gid: int,
    build_lock_path: Path,
    build_lock_uid: int,
    build_lock_gid: int,
    source_expected_uid: int | None = None,
    source_expected_gid: int | None = None,
    after_extension=None,
) -> InstallResult:
    lock_fd, original_mode = _acquire_build_lock(build_lock_path, build_lock_uid, build_lock_gid)
    mode_changed = False
    try:
        try:
            if original_mode != 0o600:
                os.fchmod(lock_fd, 0o600)
                mode_changed = True
                os.fsync(lock_fd)
            return _install_package_locked(
                gate_path=gate_path,
                extension_path=extension_path,
                backup_path=backup_path,
                source_extension=source_extension,
                expected_uid=expected_uid,
                expected_gid=expected_gid,
                source_expected_uid=source_expected_uid,
                source_expected_gid=source_expected_gid,
                after_extension=after_extension,
            )
        except BaseException as primary:
            if mode_changed:
                try:
                    os.fchmod(lock_fd, original_mode)
                    os.fsync(lock_fd)
                except BaseException as restore_error:
                    raise InstallError(f"LOCK MODE RESTORE FAILED: {restore_error}") from primary
            raise
    finally:
        os.close(lock_fd)


def _source_owner_from_sudo_environment() -> tuple[int, int]:
    raw_uid = os.environ.get("SUDO_UID")
    raw_gid = os.environ.get("SUDO_GID")
    if raw_uid is None and raw_gid is None:
        return 0, 0
    if (
        raw_uid is None
        or raw_gid is None
        or not raw_uid.isascii()
        or not raw_gid.isascii()
        or not raw_uid.isdecimal()
        or not raw_gid.isdecimal()
        or str(int(raw_uid)) != raw_uid
        or str(int(raw_gid)) != raw_gid
    ):
        raise InstallError("sudo source owner environment is invalid")
    return int(raw_uid), int(raw_gid)


def main(argv: list[str]) -> int:
    if len(argv) != 1:
        print("installer takes no arguments", file=sys.stderr)
        return 64
    if os.geteuid() != 0 or os.getegid() != 0:
        print("installer must run as root", file=sys.stderr)
        return 77
    try:
        build_user = pwd.getpwnam("lami-build")
        source_uid, source_gid = _source_owner_from_sudo_environment()
        result = install_package(
            gate_path=GATE_PATH,
            extension_path=EXTENSION_PATH,
            backup_path=BACKUP_PATH,
            source_extension=SOURCE_EXTENSION,
            expected_uid=0,
            expected_gid=0,
            source_expected_uid=source_uid,
            source_expected_gid=source_gid,
            build_lock_path=BUILD_LOCK_PATH,
            build_lock_uid=build_user.pw_uid,
            build_lock_gid=build_user.pw_gid,
        )
    except (InstallError, KeyError, OSError, subprocess.SubprocessError) as exc:
        print(f"install: FAIL: {exc}", file=sys.stderr)
        return 1
    print(f"status={result.status}")
    print(f"gate_sha256={result.gate_sha256}")
    print(f"extension_sha256={result.extension_sha256}")
    print(f"backup={result.backup_path}")
    print("authorized_keys=unchanged")
    print("sshd_config=unchanged")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
