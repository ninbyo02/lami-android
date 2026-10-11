"""Carry actual NPU cache through 17 synthetic steps using isolated QNN tools.

Reloads the context per step: a correctness probe, not a latency benchmark.
Requires previously compiled backbone and matching runtime already on device.
"""
import argparse
import json
import subprocess
from pathlib import Path


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--adb', required=True)
    p.add_argument('--device', required=True)
    p.add_argument('--runtime', required=True)
    p.add_argument('--remote-dir', required=True)
    p.add_argument('--cpu-model', type=Path, required=True)
    p.add_argument('--work-dir', type=Path, required=True)
    p.add_argument('--report', type=Path, required=True)
    a = p.parse_args()
    import numpy as np
    from ai_edge_litert.interpreter import Interpreter
    a.work_dir.mkdir(parents=True, exist_ok=True)
    local = a.work_dir / 'inputs'
    local.mkdir(exist_ok=True)
    adb = [a.adb, '-s', a.device]
    def run(args):
        return subprocess.run(adb + args, check=True, capture_output=True, timeout=120)
    i = Interpreter(model_path=str(a.cpu_model), num_threads=4)
    i.allocate_tensors()
    reference = i.get_signature_runner()
    rng = np.random.default_rng(2713)
    cache = {f'kv_cache_{kind}_{n}': np.zeros((1,32,8,128),np.float32)
             for kind in ('k','v') for n in range(5)}
    records = []
    for pos in range(17):
        mask = np.full((1,1,1,32),-10000,np.float32)
        mask[..., :pos+1] = 0
        inputs = dict(cache, embeddings=rng.normal(0,.1,(1,1,1024)).astype(np.float32),
                      input_ids=np.array([pos],np.int32), mask=mask)
        cpu = reference(**inputs)
        # Match the compiled graph input order, including the native int32 position.
        order = ['embeddings','input_ids','mask'] + list(cache)
        files = []
        for name in order:
            fname = 'serving_default_' + name + '.raw'
            inputs[name].tofile(local / fname)
            files.append(a.remote_dir + '/inputs/' + fname)
        (local / 'rollout-list.txt').write_text(' '.join(files)+'\n')
        run(['push',str(local),a.remote_dir+'/'])
        rt = a.runtime
        command = (f'LD_LIBRARY_PATH={rt} ADSP_LIBRARY_PATH="{rt};/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/vendor/dsp" '
                   f'timeout 90 {rt}/qnn-net-run --backend {rt}/libQnnHtp.so '
                   f'--retrieve_context {a.remote_dir}/mtp-backbone-context.bin '
                   f'--input_list {a.remote_dir}/inputs/rollout-list.txt '
                   f'--output_dir {a.remote_dir}/rollout-{pos} '
                   '--use_native_input_files --use_native_output_files --log_level warn')
        run(['shell',command])
        run(['pull',a.remote_dir+f'/rollout-{pos}/Result_0',str(a.work_dir/f'step-{pos}')])
        outputs = {}
        differences = {}
        for name, expected in cpu.items():
            value = np.fromfile(a.work_dir/f'step-{pos}'/('serving_default_'+name+'_output.raw'),np.float32).reshape(expected.shape)
            if not np.isfinite(value).all():
                raise ValueError(f'Nonfinite NPU output: position={pos} {name}')
            outputs[name] = value
            differences[name] = float(np.max(np.abs(value.astype(np.float64)-expected)))
            if name in cache:
                np.testing.assert_array_equal(value[:,:pos],cache[name][:,:pos])
                np.testing.assert_array_equal(value[:,pos+1:],cache[name][:,pos+1:])
        cache = {name: outputs[name] for name in cache}
        records.append({'position':pos,'max_abs_diff_vs_cpu_same_npu_cache_input':differences})
        print(f'position={pos} PASS',flush=True)
    report = {'status':'17_step_actual_npu_cache_carry_pass','steps':records,
              'checks':['finite outputs','unchanged cache outside written position','actual NPU cache used as next input'],
              'limitations':['synthetic embeddings, no speech or sampling test','CPU comparison uses the same NPU-produced cache input, not independent CPU rollout','context reloaded per step; ADB/file transfer; no latency claim','no selected-head CPU timing']}
    a.report.parent.mkdir(parents=True,exist_ok=True)
    a.report.write_text(json.dumps(report,indent=2)+'\n')


if __name__ == '__main__':
    main()
