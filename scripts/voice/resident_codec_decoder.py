"""EOS-gated decoder bridge for codec rows produced by resident CP sessions.

NPU transport generates backbone hidden states, not final code rows. The host
sampler must supply one main token and exactly fifteen sampled CP codes per frame.
No WAV is released before EOS and at least two complete frames.
"""
from pathlib import Path
from decode_codec_rows import validate_rows, decode


class ResidentCodecDecoder:
    def __init__(self, model_root: Path, output: Path, max_frames: int = 256):
        if not 2 <= max_frames <= 256:
            raise ValueError("Invalid frame limit")
        self.root = Path(model_root)
        self.output = Path(output)
        self.limit = max_frames
        self.rows = []
        self.finished = False

    def append(self, main_code: int, cp_codes):
        if self.finished or len(self.rows) >= self.limit:
            raise ValueError("Closed or full codec stream")
        row = [main_code, *list(cp_codes)]
        validate_rows([row])
        self.rows.append(row)

    def finish(self, eos: bool):
        if self.finished:
            raise ValueError("Already finalized")
        self.finished = True
        if not eos or len(self.rows) < 2:
            return {"status": "audio_withheld", "frames": len(self.rows), "eos_reached": bool(eos)}
        report = decode(self.rows, self.root, self.output)
        return {"status": "decoded", "eos_reached": True, **report}
