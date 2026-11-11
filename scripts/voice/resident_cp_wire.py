"""Strict binary framing for a future *local-only* debug resident CP transport.

This module does not open sockets or invoke ADB. Transport endpoints are not
enabled by its presence; Android implementation will require separate review.
"""
import io
import struct

MAGIC = b"LCP1"
WIDTH = 1024
MAX_POSITION = 15
HEADER = struct.Struct("<4sBBHI")
RESET = 1
STEP = 2
RESULT = 3
ERROR = 4
MAX_PAYLOAD = WIDTH * 4


def encode(kind: int, frame: int, position: int, payload=b"") -> bytes:
    if kind not in (RESET, STEP, RESULT, ERROR):
        raise ValueError("unknown message type")
    if not 0 <= frame < 256 or not 0 <= position <= MAX_POSITION:
        raise ValueError("frame/position out of bounds")
    if kind in (STEP, RESULT) and len(payload) != MAX_PAYLOAD:
        raise ValueError("expected 1024 float32 values")
    if kind == RESET and payload:
        raise ValueError("reset cannot carry payload")
    if kind == ERROR and len(payload) > 256:
        raise ValueError("oversized error payload")
    return HEADER.pack(MAGIC, kind, frame, position, len(payload)) + payload


def read_exact(stream, count: int) -> bytes:
    if not 0 <= count <= MAX_PAYLOAD:
        raise ValueError("oversized read")
    result = bytearray()
    while len(result) < count:
        part = stream.read(count - len(result))
        if not part:
            raise EOFError("truncated resident message")
        result.extend(part)
    return bytes(result)


def decode(stream) -> tuple[int, int, int, bytes]:
    magic, kind, frame, position, length = HEADER.unpack(read_exact(stream, HEADER.size))
    if magic != MAGIC or kind not in (RESET, STEP, RESULT, ERROR):
        raise ValueError("invalid resident message header")
    if length > MAX_PAYLOAD:
        raise ValueError("oversized resident message")
    payload = read_exact(stream, length)
    encode(kind, frame, position, payload)  # validate all dimensions and lengths
    return kind, frame, position, payload
