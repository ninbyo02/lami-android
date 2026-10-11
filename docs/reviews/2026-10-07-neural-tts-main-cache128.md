# Main INT8 cache128 isolated host pilot

The copy-delegated INT8 main model retains all 196 dynamic per-channel INT8 weights and reduces KV capacity from 256 to 128. CP cache16 remains fixed. Production and Android bundle defaults remain unchanged.

## Full feedback

Both held-out Japanese sentences reach EOS (49 and 50 codec frames). Codes and WAV files are byte-identical to the previous main cache256 + CP cache16 host runs. Synthetic positions 0, 32 and 127 also have bit-exact hidden outputs and active KV prefixes. This is bounded host evidence, not broad voice acceptance.

## Timing

Seven alternating trials per position, three warmups and eight timed forwards, two CPU threads, warm loaded methods and zero-cache synthetic inputs. Concurrent host workloads were not controlled.

| Position | Cache256 ms | Cache128 ms | Speed ratio |
| --- | ---: | ---: | ---: |
| 0 | 52.025 | 33.247 | 1.56x |
| 32 | 63.471 | 39.150 | 1.62x |
| 127 | 47.672 | 32.082 | 1.49x |

KV input buffers shrink from 58,720,256 to 29,360,128 bytes. Model file size remains 442,932,352 bytes; weights dominate storage. No Android latency or realtime claim follows from these host timings.

## Capacity limits and verification

The feedback validator accepts --main-capacity 64/128/256, defaults to 256, and requires --main-candidate for reduced capacities. Prefill that leaves no generation capacity is rejected before inference. A generation that exhausts its bound without EOS withholds audio. Long input support is reduced; use cache256 when needed.

Python syntax, git diff whitespace, CLI candidate guard and invalid-capacity guards passed. Android integration and device comparison remain pending. No additional Voice Lab clips were published because both host WAVs are identical to previous clips.

Artifact directory: /home/sato/project/lami-android-voice-dataset/qat/main-cache128-pilot-20261007
