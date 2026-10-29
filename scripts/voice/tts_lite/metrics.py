"""Metrics deliberately describe PCM availability, never speaker playback latency."""
from __future__ import annotations

import math


def audio_metrics(pcm, sample_rate: int, elapsed_ms: float) -> dict:
    import numpy as np
    audio = np.asarray(pcm)
    if sample_rate <= 0 or not math.isfinite(elapsed_ms) or elapsed_ms < 0:
        raise ValueError("Invalid sample rate or elapsed time")
    if audio.ndim != 1 or audio.size == 0 or not np.isfinite(audio).all():
        raise ValueError("Expected nonempty, finite, mono PCM")
    peak = float(np.max(np.abs(audio)))
    rms = float(np.sqrt(np.mean(audio.astype(np.float64) ** 2)))
    if peak > 1.0 or rms < 1e-6:
        raise ValueError("Clipped or silent PCM")
    duration = audio.size / sample_rate
    return {
        "samples": int(audio.size), "audio_seconds": duration,
        "pcm_ready_ms": elapsed_ms, "rtf": elapsed_ms / 1000 / duration,
        "peak": peak, "rms": rms,
    }


def percentile(values, p: float) -> float:
    values = sorted(values)
    if not values or not 0 <= p <= 100 or not all(math.isfinite(x) for x in values):
        raise ValueError("Expected finite observations and a percentile in [0, 100]")
    index = (len(values) - 1) * p / 100
    lower, upper = math.floor(index), math.ceil(index)
    return values[lower] + (values[upper] - values[lower]) * (index - lower)


def summarize(rows: list[dict]) -> dict:
    warm = [r for r in rows if r.get("status") == "ok" and r.get("start_kind") == "warm"]
    result = {"ok": sum(r.get("status") == "ok" for r in rows),
              "failed": sum(r.get("status") != "ok" for r in rows), "warm_observations": len(warm)}
    if warm:
        result.update({
            "warm_pcm_ready_ms_p50": percentile([r["pcm_ready_ms"] for r in warm], 50),
            "warm_pcm_ready_ms_p95": percentile([r["pcm_ready_ms"] for r in warm], 95),
            "warm_rtf_p50": percentile([r["rtf"] for r in warm], 50),
            "warm_rtf_p95": percentile([r["rtf"] for r in warm], 95),
            "warm_rtf_aggregate": sum(r["pcm_ready_ms"] for r in warm) / 1000 / sum(r["audio_seconds"] for r in warm),
        })
    for label, selected in [
        ("first_pass", [r for r in warm if r.get("repeat") == 0]),
        ("repeated", [r for r in warm if r.get("repeat", 0) > 0]),
    ]:
        if selected:
            result[label] = {
                "observations": len(selected),
                "pcm_ready_ms_p50": percentile([r["pcm_ready_ms"] for r in selected], 50),
                "rtf_p50": percentile([r["rtf"] for r in selected], 50),
                "rtf_p95": percentile([r["rtf"] for r in selected], 95),
            }
    return result
