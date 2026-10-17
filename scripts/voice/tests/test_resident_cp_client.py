import socket
import threading
import unittest
from pathlib import Path
import numpy as np

root = Path(__file__).resolve().parents[1]
import sys
sys.path.insert(0, str(root))
from resident_cp_client import ResidentCpClient
from resident_cp_wire import RESET, STEP, RESULT, encode, decode

class ClientTests(unittest.TestCase):
    def test_two_frames_full_roundtrip(self):
        secret = bytes(range(32))
        listener = socket.socket()
        listener.bind(("127.0.0.1", 0))
        listener.listen(1)
        errors = []
        def worker():
            try:
                conn, _ = listener.accept()
                with conn:
                    stream = conn.makefile("rb")
                    assert stream.read(32) == secret
                    for frame in range(2):
                        assert decode(stream)[:3] == (RESET, frame, 0)
                        for pos in range(16):
                            kind, got_frame, got_pos, data = decode(stream)
                            assert (kind, got_frame, got_pos) == (STEP, frame, pos)
                            conn.sendall(encode(RESULT, frame, pos, data))
            except Exception as exc:
                errors.append(exc)
            finally:
                listener.close()
        thread = threading.Thread(target=worker)
        thread.start()
        with ResidentCpClient(listener.getsockname()[1], secret) as client:
            for _ in range(2):
                client.reset()
                for _ in range(16):
                    np.testing.assert_array_equal(client.step(np.ones(1024, np.float32)), np.ones(1024, np.float32))
        thread.join(3)
        self.assertFalse(thread.is_alive())
        self.assertFalse(errors)

    def test_reject_bad_credentials(self):
        with self.assertRaises(ValueError):
            ResidentCpClient(1, b"short")

class RejectionTests(unittest.TestCase):
    def test_reject_invalid_timeout_and_frame_limit(self):
        with self.assertRaises(ValueError):
            ResidentCpClient(1234, bytes(32), timeout=0)
        client = ResidentCpClient(1234, bytes(32))
        client.frame = 256
        with self.assertRaises(ValueError):
            client.reset()

    def test_reject_unconnected_step(self):
        client = ResidentCpClient(1234, bytes(32))
        with self.assertRaises(ValueError):
            client.step(np.ones(1024, dtype=np.float32))

    def test_wrong_position_reply_closes_connection(self):
        key = bytes(range(32))
        listener = socket.socket()
        listener.bind(("127.0.0.1", 0))
        listener.listen(1)
        port = listener.getsockname()[1]
        failures = []
        def server():
            try:
                conn, _ = listener.accept()
                with conn:
                    stream = conn.makefile("rb")
                    self.assertEqual(key, stream.read(32))
                    self.assertEqual(RESET, decode(stream)[0])
                    frame, position = decode(stream)[1:3]
                    conn.sendall(encode(RESULT, frame, position + 1, np.ones(1024, np.float32).tobytes()))
            except Exception as exc:
                failures.append(exc)
            finally:
                listener.close()
        thread = threading.Thread(target=server)
        thread.start()
        with ResidentCpClient(port, key, timeout=3) as client:
            client.reset()
            with self.assertRaises(ValueError):
                client.step(np.ones(1024, np.float32))
            self.assertIsNone(client.connection)
        thread.join(3)
        self.assertFalse(thread.is_alive())
        self.assertFalse(failures)
