#!/usr/bin/env python3
"""Fail-closed deploy-future-standard implementation for the laptop gate."""
from __future__ import annotations

from dataclasses import dataclass
import fcntl
import hashlib
import os
from pathlib import Path
import re
import selectors
import signal
import stat
import subprocess
import sys
import time

BUILD_HOME = Path("/home/lami-build")
REPO = BUILD_HOME / "repos/lami-android"
LOGS = BUILD_HOME / ".deploy-logs"
SNAPSHOTS = BUILD_HOME / ".deploy-snapshots"
LOCK = BUILD_HOME / ".build.lock"
APK_RELATIVE = Path("app/build/outputs/apk/standard/debug/app-standard-debug.apk")
ORIGIN_URL = "https://github.com/ninbyo02/lami-android.git"
FUTURE_REFSPEC = "+refs/heads/future:refs/remotes/origin/future"
ADB = "/opt/android-sdk/platform-tools/adb"
ADB_SERVER_SOCKET = "tcp:127.0.0.1:5037"
DEVICE_HOST = "192.168.3.19"
EXPECTED_MODEL = "NX733J"
PACKAGE = "io.github.ninbyo02.lami"
ACTIVITY = "io.github.ninbyo02.lami.MainActivity"
MAX_COMMAND_OUTPUT = 4 * 1024 * 1024
BUILD_TIMEOUT = 3600
ADB_TIMEOUT = 45
OBSERVATION_INTERVAL = 3.0
LOCAL_PROPERTIES_BYTES = b"sdk.dir=/opt/android-sdk\n"
GIT_FIXED_ARGS = (
    "-c", "core.hooksPath=/dev/null",
    "-c", "core.fsmonitor=false",
    "-c", "credential.helper=",
    "-c", "protocol.file.allow=never",
)


class DeployError(RuntimeError):
    pass


@dataclass(frozen=True)
class Paths:
    repo: Path = REPO
    logs: Path = LOGS
    snapshots: Path = SNAPSHOTS
    lock: Path = LOCK


@dataclass(frozen=True)
class DeviceIdentity:
    model: str
    hardware_serial: str


@dataclass(frozen=True)
class DeployResult:
    commit: str
    apk_sha256: str
    device_serial: str
    hardware_serial: str
    pid: int
    log_path: Path
    provenance_log_path: Path


def parse_port(text: str) -> int:
    if not re.fullmatch(r"[1-9][0-9]{0,4}", text, flags=re.ASCII):
        raise DeployError("port must be canonical decimal in 1..65535")
    port = int(text, 10)
    if port > 65535:
        raise DeployError("port must be canonical decimal in 1..65535")
    return port


def sanitized_environment() -> dict[str, str]:
    return {
        "HOME": str(BUILD_HOME),
        "PATH": "/usr/local/bin:/usr/bin:/bin",
        "ANDROID_HOME": "/opt/android-sdk",
        "ANDROID_SDK_ROOT": "/opt/android-sdk",
        "ADB_SERVER_SOCKET": ADB_SERVER_SOCKET,
        "LANG": "C.UTF-8",
        "LC_ALL": "C.UTF-8",
    }


class Runner:
    """Absolute-command runner with timeout and hard output cap."""

    def __init__(self, environment: dict[str, str] | None = None):
        self.environment = dict(environment or sanitized_environment())

    def run(self, argv, *, cwd=None, timeout=None, output_limit=None, log_path=None, pass_fds=()) -> str:
        argv = [str(value) for value in argv]
        if not argv or not argv[0].startswith("/"):
            raise DeployError("runner requires an absolute executable path")
        limit = MAX_COMMAND_OUTPUT if output_limit is None else output_limit
        deadline = time.monotonic() + (timeout or ADB_TIMEOUT)
        log_fd = None
        if log_path is not None:
            log_fd = os.open(
                log_path,
                os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW | os.O_CLOEXEC,
                0o600,
            )
        output = bytearray()
        process = None
        selector = None
        try:
            process = subprocess.Popen(
                argv,
                cwd=cwd,
                env=self.environment,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                start_new_session=True,
                pass_fds=tuple(pass_fds),
            )
            selector = selectors.DefaultSelector()
            assert process.stdout is not None
            selector.register(process.stdout, selectors.EVENT_READ)
            while selector.get_map():
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise DeployError(f"command timed out: {argv[0]}")
                events = selector.select(min(remaining, 0.5))
                if not events and process.poll() is not None:
                    chunk = process.stdout.read()
                    if chunk:
                        output.extend(chunk)
                        if len(output) > limit:
                            raise DeployError(f"command output exceeded {limit} bytes")
                        if log_fd is not None:
                            Deployment._write_all(log_fd, chunk)
                    selector.unregister(process.stdout)
                    break
                for key, _ in events:
                    chunk = os.read(key.fileobj.fileno(), 65536)
                    if not chunk:
                        selector.unregister(key.fileobj)
                        continue
                    output.extend(chunk)
                    if len(output) > limit:
                        raise DeployError(f"command output exceeded {limit} bytes")
                    if log_fd is not None:
                        Deployment._write_all(log_fd, chunk)
            rc = process.wait(timeout=max(0.1, deadline - time.monotonic()))
        except BaseException:
            if process is not None and process.poll() is None:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
            raise
        finally:
            had_error = sys.exc_info()[0] is not None
            cleanup_error = None
            if selector is not None:
                try:
                    selector.close()
                except BaseException as exc:
                    cleanup_error = cleanup_error or exc
            if process is not None and process.stdout is not None:
                try:
                    process.stdout.close()
                except BaseException as exc:
                    cleanup_error = cleanup_error or exc
            if log_fd is not None:
                try:
                    os.fsync(log_fd)
                except BaseException as exc:
                    cleanup_error = cleanup_error or exc
                finally:
                    try:
                        os.close(log_fd)
                    except BaseException as exc:
                        cleanup_error = cleanup_error or exc
            if cleanup_error is not None and not had_error:
                raise cleanup_error
        text = output.decode("utf-8", errors="replace")
        if rc != 0:
            excerpt = text[-2000:].replace("\x00", "?")
            raise DeployError(f"command failed ({rc}): {argv[0]}\n{excerpt}")
        return text


class Deployment:
    def __init__(
        self,
        runner=None,
        paths: Paths = Paths(),
        *,
        lock_already_held: bool = False,
        sleeper=time.sleep,
        observation_interval: float = OBSERVATION_INTERVAL,
    ):
        self.runner = runner or Runner()
        self.paths = paths
        self.lock_already_held = lock_already_held
        self.sleeper = sleeper
        self.observation_interval = observation_interval
        self._lock_fd = None

    def _run(
        self, argv, *, timeout=ADB_TIMEOUT, output_limit=MAX_COMMAND_OUTPUT,
        log_path=None, pass_fds=(),
    ) -> str:
        return self.runner.run(
            argv,
            cwd=self.paths.repo,
            timeout=timeout,
            output_limit=output_limit,
            log_path=log_path,
            pass_fds=pass_fds,
        )

    @staticmethod
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

    def _git(self, *args: str) -> str:
        return self._run(("/usr/bin/git", *GIT_FIXED_ARGS, *args))

    def _adb(
        self, serial: str, *args: str, timeout=ADB_TIMEOUT,
        output_limit=MAX_COMMAND_OUTPUT, pass_fds=(),
    ) -> str:
        return self._run(
            (ADB, "-s", serial, *args), timeout=timeout,
            output_limit=output_limit, pass_fds=pass_fds,
        )

    def _acquire_lock(self) -> None:
        if self.lock_already_held:
            return
        try:
            info = self.paths.lock.lstat()
            if (
                not stat.S_ISREG(info.st_mode)
                or info.st_nlink != 1
                or info.st_uid != os.geteuid()
                or stat.S_IMODE(info.st_mode) != 0o600
            ):
                raise DeployError("existing build lock is unsafe")
            self._lock_fd = os.open(self.paths.lock, os.O_WRONLY | os.O_NOFOLLOW | os.O_CLOEXEC)
            opened = os.fstat(self._lock_fd)
            current = self.paths.lock.lstat()
            if (
                (opened.st_dev, opened.st_ino) != (info.st_dev, info.st_ino)
                or (current.st_dev, current.st_ino) != (info.st_dev, info.st_ino)
                or not stat.S_ISREG(opened.st_mode)
                or opened.st_nlink != 1
                or opened.st_uid != os.geteuid()
                or stat.S_IMODE(opened.st_mode) != 0o600
            ):
                raise DeployError("existing build lock is unsafe")
            fcntl.flock(self._lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            locked = os.fstat(self._lock_fd)
            current = self.paths.lock.lstat()
            if (
                (locked.st_dev, locked.st_ino) != (info.st_dev, info.st_ino)
                or (current.st_dev, current.st_ino) != (info.st_dev, info.st_ino)
                or not stat.S_ISREG(locked.st_mode)
                or locked.st_nlink != 1
                or locked.st_uid != os.geteuid()
                or stat.S_IMODE(locked.st_mode) != 0o600
                or not stat.S_ISREG(current.st_mode)
                or current.st_nlink != 1
                or current.st_uid != os.geteuid()
                or stat.S_IMODE(current.st_mode) != 0o600
            ):
                raise DeployError("existing build lock is unsafe after flock")
        except (OSError, BlockingIOError) as exc:
            if self._lock_fd is not None:
                os.close(self._lock_fd)
                self._lock_fd = None
            raise DeployError("A LAMI build is already running or lock is unsafe") from exc
        except BaseException:
            if self._lock_fd is not None:
                os.close(self._lock_fd)
                self._lock_fd = None
            raise

    def _verify_repo(self) -> None:
        try:
            info = self.paths.repo.lstat()
        except OSError as exc:
            raise DeployError("repository path is unavailable") from exc
        if not stat.S_ISDIR(info.st_mode) or self.paths.repo.resolve() != self.paths.repo:
            raise DeployError("repository path must be the fixed real directory")
        if self._git("status", "--porcelain=v1", "--untracked-files=all"):
            raise DeployError("repository is dirty")
        origin = self._git("config", "--get", "remote.origin.url").strip()
        if origin != ORIGIN_URL:
            raise DeployError("origin URL mismatch")

    def _checkout_future(self) -> str:
        self._git("clean", "-ffdx")
        self._git("fetch", "--no-tags", "origin", FUTURE_REFSPEC)
        fetched = self._git("rev-parse", "FETCH_HEAD").strip()
        if not re.fullmatch(r"[0-9a-f]{40}", fetched):
            raise DeployError("fetched commit is invalid")
        self._git("checkout", "--detach", fetched)
        self._git("clean", "-ffdx")
        head = self._git("rev-parse", "HEAD").strip()
        if head != fetched:
            raise DeployError("detached checkout mismatch")
        if self._git("status", "--porcelain=v1", "--untracked-files=all"):
            raise DeployError("repository became dirty after checkout")
        return fetched

    def _write_fixed_local_properties(self) -> Path:
        path = self.paths.repo / "local.properties"
        try:
            fd = os.open(
                path,
                os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW | os.O_CLOEXEC,
                0o600,
            )
        except OSError as exc:
            raise DeployError("could not create fixed local.properties") from exc
        try:
            self._write_all(fd, LOCAL_PROPERTIES_BYTES)
            os.fsync(fd)
        finally:
            os.close(fd)
        self._fsync_dir(self.paths.repo)
        return path

    def _verify_fixed_local_properties(self, path: Path) -> None:
        try:
            info = path.lstat()
            fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC)
        except OSError as exc:
            raise DeployError("local.properties changed during build") from exc
        try:
            content = bytearray()
            while True:
                chunk = os.read(fd, 4096)
                if not chunk:
                    break
                content.extend(chunk)
        finally:
            os.close(fd)
        if (
            not stat.S_ISREG(info.st_mode)
            or info.st_nlink != 1
            or info.st_uid != os.geteuid()
            or stat.S_IMODE(info.st_mode) != 0o600
            or bytes(content) != LOCAL_PROPERTIES_BYTES
        ):
            raise DeployError("local.properties changed during build")

    def _probe_device(self, serial: str) -> DeviceIdentity:
        if self._adb(serial, "get-state").strip() != "device":
            raise DeployError("exact ADB serial is not in device state")
        model = self._adb(serial, "shell", "getprop", "ro.product.model").strip()
        if model != EXPECTED_MODEL:
            raise DeployError(f"device model mismatch: {model!r}")
        hardware_serial = self._adb(serial, "shell", "getprop", "ro.serialno").strip()
        if not re.fullmatch(r"[A-Za-z0-9._:-]{1,128}", hardware_serial, flags=re.ASCII):
            raise DeployError("device ro.serialno is empty or invalid")
        return DeviceIdentity(model=model, hardware_serial=hardware_serial)

    def _remove_stale_apk(self, apk: Path) -> None:
        try:
            relative = apk.relative_to(self.paths.repo)
        except ValueError as exc:
            raise DeployError("stale APK is outside the fixed repository") from exc
        if relative != APK_RELATIVE:
            raise DeployError("stale APK path is not fixed")
        fds = []
        try:
            current_fd = os.open(
                self.paths.repo,
                os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC,
            )
            fds.append(current_fd)
            for component in relative.parts[:-1]:
                try:
                    current_fd = os.open(
                        component,
                        os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC,
                        dir_fd=current_fd,
                    )
                except FileNotFoundError:
                    return
                fds.append(current_fd)
            try:
                info = os.stat(relative.name, dir_fd=current_fd, follow_symlinks=False)
            except FileNotFoundError:
                return
            if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1:
                raise DeployError("stale APK path is not a single-link regular file")
            os.unlink(relative.name, dir_fd=current_fd)
            os.fsync(current_fd)
        except DeployError:
            raise
        except OSError as exc:
            raise DeployError("stale APK path is unsafe") from exc
        finally:
            for fd in reversed(fds):
                os.close(fd)

    def _build(self, apk: Path, log_path: Path) -> None:
        self._remove_stale_apk(apk)
        local_properties = self._write_fixed_local_properties()
        self._run(
            (str(self.paths.repo / "gradlew"), "--no-daemon", "--console=plain", ":app:assembleStandardDebug"),
            timeout=BUILD_TIMEOUT,
            output_limit=MAX_COMMAND_OUTPUT,
            log_path=log_path,
        )
        self._verify_fixed_local_properties(local_properties)


    def _prepare_private_dir(self, path: Path, label: str) -> None:
        try:
            path.mkdir(mode=0o700)
        except FileExistsError:
            pass
        info = path.lstat()
        if (
            not stat.S_ISDIR(info.st_mode)
            or path.resolve() != path
            or stat.S_IMODE(info.st_mode) != 0o700
            or info.st_uid != os.geteuid()
        ):
            raise DeployError(f"{label} directory is unsafe")

    def _prepare_snapshot_dir(self) -> None:
        self._prepare_private_dir(self.paths.snapshots, "snapshot")

    def _write_provenance(
        self,
        path: Path,
        *,
        commit: str,
        apk_hash: str,
        serial: str,
        identity: DeviceIdentity,
    ) -> None:
        payload = (
            f"commit={commit}\n"
            f"apk_sha256={apk_hash}\n"
            f"adb_serial={serial}\n"
            f"model={identity.model}\n"
            f"ro.serialno={identity.hardware_serial}\n"
            "install_mode=adb install -r\n"
        ).encode("ascii")
        fd = os.open(
            path,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW | os.O_CLOEXEC,
            0o600,
        )
        try:
            self._write_all(fd, payload)
            os.fsync(fd)
        finally:
            os.close(fd)
        self._fsync_dir(path.parent)

    @staticmethod
    def _hash_fd(fd: int) -> str:
        digest = hashlib.sha256()
        offset = 0
        while True:
            chunk = os.pread(fd, 1024 * 1024, offset)
            if not chunk:
                return digest.hexdigest()
            digest.update(chunk)
            offset += len(chunk)

    def _open_sealed_apk(self, apk: Path) -> tuple[int, str]:
        self._prepare_snapshot_dir()
        try:
            relative = apk.relative_to(self.paths.repo)
        except ValueError as exc:
            raise DeployError("built APK is outside the fixed repository") from exc
        if relative != APK_RELATIVE:
            raise DeployError("built APK path is not fixed")
        fds = []
        sealed_fd = None
        try:
            current_fd = os.open(
                self.paths.repo,
                os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC,
            )
            fds.append(current_fd)
            for component in relative.parts[:-1]:
                current_fd = os.open(
                    component,
                    os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC,
                    dir_fd=current_fd,
                )
                fds.append(current_fd)
            source_fd = os.open(
                relative.name,
                os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC,
                dir_fd=current_fd,
            )
            fds.append(source_fd)
            before = os.fstat(source_fd)
            if not stat.S_ISREG(before.st_mode) or before.st_nlink != 1:
                raise DeployError("built APK must be a single-link regular file")
            sealed_fd = os.memfd_create(
                "lami-standard-debug-apk",
                os.MFD_CLOEXEC | os.MFD_ALLOW_SEALING,
            )
            while True:
                chunk = os.read(source_fd, 1024 * 1024)
                if not chunk:
                    break
                self._write_all(sealed_fd, chunk)
            os.fsync(sealed_fd)
            copied_hash = self._hash_fd(sealed_fd)
            source_hash = self._hash_fd(source_fd)
            after = os.fstat(source_fd)
            identity_before = (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns)
            identity_after = (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns)
            if identity_before != identity_after or source_hash != copied_hash:
                raise DeployError("APK changed while copying to sealed FD")
            seals = fcntl.F_SEAL_WRITE | fcntl.F_SEAL_GROW | fcntl.F_SEAL_SHRINK | fcntl.F_SEAL_SEAL
            fcntl.fcntl(sealed_fd, fcntl.F_ADD_SEALS, seals)
            self._verify_sealed_apk(sealed_fd, copied_hash)
            result_fd = sealed_fd
            sealed_fd = None
            return result_fd, copied_hash
        except DeployError:
            raise
        except OSError as exc:
            raise DeployError("built APK must be regular and safely secured in a sealed FD") from exc
        finally:
            if sealed_fd is not None:
                os.close(sealed_fd)
            for fd in reversed(fds):
                os.close(fd)

    @staticmethod
    def _fsync_dir(path: Path) -> None:
        fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY | os.O_CLOEXEC)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)

    def _verify_sealed_apk(self, fd: int, expected_hash: str) -> None:
        required = fcntl.F_SEAL_WRITE | fcntl.F_SEAL_GROW | fcntl.F_SEAL_SHRINK | fcntl.F_SEAL_SEAL
        try:
            actual_seals = fcntl.fcntl(fd, fcntl.F_GET_SEALS)
            info = os.fstat(fd)
            actual = self._hash_fd(fd)
        except OSError as exc:
            raise DeployError("sealed APK FD became unavailable") from exc
        if not stat.S_ISREG(info.st_mode) or actual_seals & required != required:
            raise DeployError("APK FD is not fully sealed")
        if actual != expected_hash:
            raise DeployError("APK sealed FD hash mismatch")

    @staticmethod
    def _is_fixed_component(token: str) -> bool:
        return token in (f"{PACKAGE}/.MainActivity", f"{PACKAGE}/{ACTIVITY}")

    def _verify_launch_output(self, output: str) -> None:
        status_ok = re.search(r"(?m)^\s*Status:\s*ok\s*$", output) is not None
        activity_ok = False
        for line in output.splitlines():
            matched = re.fullmatch(r"\s*Activity:\s*(\S+)\s*", line)
            if matched and self._is_fixed_component(matched.group(1)):
                activity_ok = True
                break
        if not status_ok or not activity_ok:
            raise DeployError("am start did not confirm the fixed MainActivity")

    def _verify_top_resumed(self, output: str) -> None:
        for line in output.splitlines():
            matched = re.fullmatch(r"\s*(?:mResumedActivity|topResumedActivity)\s*[:=]\s*(.*?)\s*", line)
            if not matched:
                continue
            tokens = re.findall(r"(?<![A-Za-z0-9_.])([A-Za-z0-9_.]+/(?:\.[A-Za-z0-9_.]+|[A-Za-z0-9_.]+))(?![A-Za-z0-9_.])", matched.group(1))
            if tokens and self._is_fixed_component(tokens[0]):
                return
        raise DeployError("MainActivity is not the exact top-resumed component")

    def _fresh_crash_check(self, serial: str, pid: int, log_path: Path) -> None:
        common_args = ("-d", "-v", "threadtime", "-t", "500")
        all_output = self._adb(
            serial,
            "logcat",
            *common_args,
            timeout=30,
            output_limit=1024 * 1024,
        )
        pid_output = self._adb(
            serial,
            "logcat",
            f"--pid={pid}",
            *common_args,
            timeout=30,
            output_limit=1024 * 1024,
        )
        output = "== fresh all-process logcat ==\n" + all_output + "\n== fresh PID logcat ==\n" + pid_output
        fd = os.open(log_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW | os.O_CLOEXEC, 0o600)
        try:
            self._write_all(fd, output.encode("utf-8", errors="replace"))
            os.fsync(fd)
        finally:
            os.close(fd)
        package_crash = (
            re.search(rf"FATAL EXCEPTION[\s\S]{{0,2000}}Process:\s*{re.escape(PACKAGE)}(?:,|\s)", all_output)
            or re.search(rf"Fatal signal[^\n]*>>>\s*{re.escape(PACKAGE)}\s*<<<", all_output)
            or re.search(rf"Process\s+{re.escape(PACKAGE)}(?:,|\s).*has died", all_output)
        )
        pid_crash = re.search(r"FATAL EXCEPTION|Fatal signal|Process .* has died", pid_output)
        if package_crash or pid_crash:
            raise DeployError("fresh crash evidence found in bounded logcat")

    def _observe_runtime(self, serial: str, pid: int, log_path: Path) -> None:
        if self.observation_interval < 0:
            raise DeployError("runtime observation interval is invalid")
        self.sleeper(self.observation_interval)
        self._fresh_crash_check(serial, pid, log_path)
        pid_after = self._adb(serial, "shell", "pidof", PACKAGE).strip()
        if pid_after != str(pid):
            raise DeployError(f"application PID changed during crash window: {pid!r} -> {pid_after!r}")
        resumed_after = self._adb(
            serial, "shell", "dumpsys", "activity", "activities", output_limit=1024 * 1024,
        )
        self._verify_top_resumed(resumed_after)

    def run(self, port_text: str) -> DeployResult:
        port = parse_port(port_text)
        serial = f"{DEVICE_HOST}:{port}"
        self._acquire_lock()
        sealed_apk_fd = None
        try:
            self._prepare_private_dir(self.paths.logs, "deploy log")
            timestamp = time.strftime("%Y%m%d-%H%M%S", time.gmtime())
            build_log = self.paths.logs / f"deploy-future-standard-{timestamp}-{os.getpid()}.log"
            provenance_log = self.paths.logs / f"deploy-future-standard-{timestamp}-{os.getpid()}-provenance.log"
            pid_log = self.paths.logs / f"deploy-future-standard-{timestamp}-{os.getpid()}-pid.logcat"
            self._verify_repo()
            commit = self._checkout_future()
            self._run((ADB, "connect", serial), timeout=ADB_TIMEOUT)
            initial_identity = self._probe_device(serial)
            apk = self.paths.repo / APK_RELATIVE
            self._build(apk, build_log)
            sealed_apk_fd, apk_hash = self._open_sealed_apk(apk)
            before_install = self._probe_device(serial)
            if before_install != initial_identity:
                raise DeployError("device identity drift before install")
            self._verify_sealed_apk(sealed_apk_fd, apk_hash)
            self._write_provenance(
                provenance_log,
                commit=commit,
                apk_hash=apk_hash,
                serial=serial,
                identity=before_install,
            )
            inherited_path = f"/proc/self/fd/{sealed_apk_fd}"
            try:
                install_output = self._adb(
                    serial, "install", "-r", inherited_path,
                    timeout=300, pass_fds=(sealed_apk_fd,),
                )
            except OSError as exc:
                self._verify_sealed_apk(sealed_apk_fd, apk_hash)
                raise DeployError("APK snapshot hash mismatch attempt was blocked by sealed FD") from exc
            if not re.search(r"(?m)^Success\s*$", install_output):
                raise DeployError("ADB install did not report Success")
            self._verify_sealed_apk(sealed_apk_fd, apk_hash)
            after_install = self._probe_device(serial)
            if after_install != initial_identity:
                raise DeployError("device identity drift after install")
            self._adb(serial, "logcat", "-c")
            launch_output = self._adb(
                serial,
                "shell",
                "am",
                "start",
                "-W",
                "-n",
                f"{PACKAGE}/{ACTIVITY}",
            )
            self._verify_launch_output(launch_output)
            pid_text = self._adb(serial, "shell", "pidof", PACKAGE).strip()
            if not re.fullmatch(r"[1-9][0-9]*", pid_text, flags=re.ASCII):
                raise DeployError("application PID is missing or ambiguous")
            pid = int(pid_text)
            resumed = self._adb(serial, "shell", "dumpsys", "activity", "activities", output_limit=1024 * 1024)
            self._verify_top_resumed(resumed)
            self._observe_runtime(serial, pid, pid_log)
            return DeployResult(
                commit,
                apk_hash,
                serial,
                initial_identity.hardware_serial,
                pid,
                build_log,
                provenance_log,
            )
        finally:
            if sealed_apk_fd is not None:
                os.close(sealed_apk_fd)
            if self._lock_fd is not None:
                os.close(self._lock_fd)
                self._lock_fd = None


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print("usage: deploy-future-standard <port>", file=sys.stderr)
        return 64
    try:
        result = Deployment().run(argv[1])
    except DeployError as exc:
        print(f"deploy-future-standard: FAIL: {exc}", file=sys.stderr)
        return 1
    print("deploy-future-standard: OK")
    print(f"commit={result.commit}")
    print(f"apk_sha256={result.apk_sha256}")
    print(f"adb_serial={result.device_serial}")
    print(f"ro.serialno={result.hardware_serial}")
    print(f"pid={result.pid}")
    print(f"build_log={result.log_path}")
    print(f"provenance_log={result.provenance_log_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
