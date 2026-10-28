"""Canonical next-main-model embedding from a complete 16-code Qwen TTS frame."""
import numpy as np


def next_main_input(code_row, main_embedding, cp_embeddings, pad):
    if len(code_row) != 16:
        raise ValueError("Complete 16-code frame required")
    if not isinstance(code_row[0], (int, np.integer)) or not 0 <= code_row[0] < 2048:
        raise ValueError("Invalid main code")
    if len(cp_embeddings) != 15:
        raise ValueError("Fifteen CP embedding tables required")
    row = np.asarray(code_row)
    if row.dtype.kind not in "iu" or np.any(row[1:] < 0) or np.any(row[1:] >= 2048):
        raise ValueError("Invalid CP codes")
    result = np.asarray(main_embedding[int(row[0])], dtype=np.float32).copy()
    for i, code in enumerate(row[1:]):
        result += np.asarray(cp_embeddings[i][int(code)], dtype=np.float32)
    result += np.asarray(pad, dtype=np.float32)
    if result.shape != (1024,) or not np.isfinite(result).all():
        raise ValueError("Invalid next-main embedding")
    return result
