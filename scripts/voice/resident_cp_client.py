"""Opt-in localhost-only diagnostic client. No automatic ADB or discovery."""
import socket
import numpy as np
from resident_cp_wire import RESET, STEP, RESULT, encode, decode


class ResidentCpClient:
    def __init__(self, port: int, secret: bytes, timeout=15):
        if not 1 <= port <= 65535 or len(secret) != 32:
            raise ValueError("Invalid diagnostic endpoint credentials")
        self.port, self.secret, self.timeout = port, secret, timeout
        self.connection = None
        self.frame = 0
        self.position = -1

    def __enter__(self):
        self.connection = socket.create_connection(("127.0.0.1", self.port), self.timeout)
        self.connection.settimeout(self.timeout)
        self.stream = self.connection.makefile("rb")
        self.connection.sendall(self.secret)
        return self

    def reset(self):
        if self.position != -1:
            raise ValueError("Current frame not complete")
        self.connection.sendall(encode(RESET, self.frame, 0))
        self.position = 0

    def step(self, embedding):
        if not 0 <= self.position <= 15:
            raise ValueError("Must reset before stepping")
        values = np.asarray(embedding, dtype="<f4")
        if values.shape != (1024,) or not np.isfinite(values).all():
            raise ValueError("Non-finite or incorrectly shaped embedding")
        pos = self.position
        self.connection.sendall(encode(STEP, self.frame, pos, values.tobytes()))
        kind, frame, position, payload = decode(self.stream)
        if (kind, frame, position) != (RESULT, self.frame, pos):
            raise ValueError("Incorrect resident response")
        hidden = np.frombuffer(payload, dtype="<f4").copy()
        if not np.isfinite(hidden).all():
            raise ValueError("Non-finite resident result")
        self.position += 1
        if self.position == 16:
            self.position = -1
            self.frame += 1
        return hidden

    def __exit__(self, *_):
        if self.connection:
            self.stream.close()
            self.connection.close()
            self.connection = None
