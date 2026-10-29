"""Fixed-input host traced CP thread probe; no Android performance claim."""
import argparse, hashlib, json, subprocess, statistics
from pathlib import Path
from executorch.devtools import Inspector

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--runner', type=Path, required=True)
p.add_argument('--model', type=Path, required=True)
p.add_argument('--sha256', required=True)
p.add_argument('--inputs', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
if hashlib.sha256(a.model.read_bytes()).hexdigest() != a.sha256:
    raise ValueError('Model hash mismatch')
a.output.mkdir(parents=True, exist_ok=False)
files = [a.inputs / f'input-{i}.raw' for i in range(7)]
input_hashes = {f.name: hashlib.sha256(f.read_bytes()).hexdigest() for f in files}
rows = []
for trial in range(3):
    for threads in ([1, 2, 4] if trial % 2 == 0 else [4, 2, 1]):
        trace = a.output / f'trial-{trial}-threads-{threads}.etdp'
        command = [str(a.runner), '--model_path='+str(a.model),
                   '--inputs='+','.join(str(f) for f in files),
                   '--cpu_threads='+str(threads), '--num_executions=16',
                   '--print_output=none', '--etdump_path='+str(trace)]
        result = subprocess.run(command, capture_output=True, timeout=120)
        trace.with_suffix('.log').write_bytes(result.stdout+result.stderr)
        result.check_returncode()
        df = Inspector(etdump_path=str(trace)).to_dataframe()
        df = df.rename(columns={c: c.replace(' (ms)', '') for c in df.columns})
        events = df[df.event_block_name == 'Execute']
        row = {'trial': trial, 'threads': threads,
               'execute_ms': float(events[events.event_name == 'Method::execute'].avg.sum()),
               'delegate_ms': float(events[events.event_name == 'DELEGATE_CALL'].avg.sum()),
               'native_ms': float(events[events.event_name.str.startswith('native_call_')].avg.sum())}
        rows.append(row)
        print(json.dumps(row), flush=True)
report = {'model_sha256': a.sha256, 'input_sha256': input_hashes, 'rows': rows,
          'median_execute_ms': {str(n): statistics.median(r['execute_ms'] for r in rows if r['threads']==n) for n in [1,2,4]},
          'limitations': ['Host x86 CPU only; no Android speed claim.',
                          'Tracing overhead included; cache and scheduling uncontrolled.',
                          'Output equality across thread counts not checked; do not promote.']}
(a.output/'report.json').write_text(json.dumps(report, indent=2)+'\n')
