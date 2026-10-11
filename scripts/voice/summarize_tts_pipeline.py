#!/usr/bin/env python3
"""Summarize existing two-request device probes without double-counting prefill."""
import argparse, hashlib, json, re
from pathlib import Path
p=argparse.ArgumentParser(); p.add_argument("capture", type=Path); p.add_argument("output", type=Path); a=p.parse_args()
s=json.loads((a.capture/"summary.json").read_text())
assert s["all_outputs_match"] and s["restore"]["status"] == "restored_and_hash_verified"
rows=[]
for q in s["requests"]:
    f=a.capture/f'{q["run"]}-{q["variant"]}.txt'
    lines=[x for x in f.read_text().splitlines() if x.startswith(f'request={q["request"]} ')]
    def event(name):
        matches=[int(re.search(r"elapsed_ms=(\d+)",x).group(1)) for x in lines if name in x]
        assert len(matches)==1, (name,matches)
        return matches[0]
    start=event("synthesis=started")
    m=q["metrics"]; n=m["codec"]["frames"]
    rows.append(dict(run=q["run"],variant=q["variant"],request=q["request"],frames=n,
        audio_ms=n*80,codec_ms=m["codec"]["ms"],prefill_ms=m["prefill"]["ms"],
        cp_forward_ms=m["cp_forward"]["ms"],cp_heads_ms=m["cp_heads"]["ms"],
        cp_sampling_ms=m["cp_sampling"]["ms"],pcm_forward_ms=m["decoder_process"]["pcm_forward_ms"],
        decoder_total_ms=m["decoder_process"]["total_ms"],
        synthesis_ms=event("synthesis=complete")-start,
        playback_start_ms=event("playback=started")-start,
        codec_plus_pcm_rtf=(m["codec"]["ms"]+m["decoder_process"]["pcm_forward_ms"])/(n*80),
        capture_sha256=hashlib.sha256(f.read_bytes()).hexdigest()))
out=dict(requests=rows,limits=["Captured serial diagnostic path; not normal chat latency or concurrent streaming measurement.","Codec includes main autoregressive and CP work; main timing totals also include prefill and must not be added to codec.","PCM time normalized per frame is amortized batch cost, not a measured streaming chunk cost.","RTF omits startup, prefill, process overhead and playback; device frequency/thermal state uncontrolled."],
    next_step="Replay fixed accepted codes through the PCM decoder; compare warmed whole-runtime thread settings with output parity before attempting concurrency. Repeated cumulative prefix decoding is not a stateful incremental decoder.")
a.output.write_text(json.dumps(out,ensure_ascii=False,indent=2)+"\n")
for r in rows: print(r)
