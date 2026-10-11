"""Validate restored fixed-code PCM thread device captures and summarize timings."""
import argparse,json,re,statistics,hashlib
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('capture',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
s=(a.capture/'0-baseline.txt').read_text();restore=json.loads((a.capture/'restore.json').read_text())
assert re.search(r'^status=complete',s,re.M)
assert restore['status']=='restored_and_hash_verified' and all(restore['verified'].values())
expected_codes=['e2ff0ad48c2670a16133384ce2136a7d1462dd3815b5ae327dd9746ce3e96f39','d6adf131995c065844a99909622456dc5b641c4dfe021bc8141c47a78a8ff943']
expected_pcm=['ad43fe3c69460d0bf331b54fb67b1d4ac86a859d7b658f47532548d19ae3e181','2f5349afff59dbe6902112e0e3c0fd19aaf31ee1ef7384f3e9ae79f56c321952']
for i in [0,1]:
 assert f'request={i} codes_sha256={expected_codes[i]}' in s
 assert f'request={i} pcm_sha256={expected_pcm[i]}' in s
checks=[dict(re.findall(r'(\w+)=([^\s]+)',l)) for l in s.splitlines() if 'metric=pcm_threads ' in l]
assert len(checks)==36 and all(x['bit_equal']=='true' for x in checks)
rows=[]
for l in s.splitlines():
 if 'metric=decoder_process ' not in l or ' repeat=' not in l:continue
 v=dict(re.findall(r'(\w+)=([^\s]+)',l));assert v['reused']=='true'
 rows.append({k:int(v[k]) for k in ['request','pcm_trial','repeat','threads','pcm_forward_ms','total_ms']})
assert len(rows)==36
keys={(x['request'],x['pcm_trial'],x['threads'],x['repeat']) for x in rows};assert len(keys)==36
for request in [0,1]:
 for trial in range(3):
  for n in [1,2,4]:
   for repeat in range(2):assert (request,trial,n,repeat) in keys
out=dict(rows=rows,median_pcm_forward_ms={str(i):{str(n):statistics.median(x['pcm_forward_ms'] for x in rows if x['request']==i and x['threads']==n) for n in [1,2,4]} for i in [0,1]},restore=restore,all_36_bit_equal_to_default=True,capture_sha256=hashlib.sha256(s.encode()).hexdigest(),backup=json.loads((a.capture/'backup.json').read_text()),limitations=['Requested thread count is reported; actual scheduling/cores are not measured.','Same captured codes reused per sentence; frequency/cache/thermals uncontrolled.','CPU only; no NPU result or default behavior promotion.'])
a.output.write_text(json.dumps(out,indent=2)+'\n');print(json.dumps(out['median_pcm_forward_ms']))
