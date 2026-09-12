from pathlib import Path
import subprocess,zipfile,hashlib,json,shutil
D=Path('/tmp/lami-gpu-controlled-results');base=Path('/tmp/lami-gpu-controlled/app/build/outputs/apk/standard/debug/app-standard-debug.apk');minimal=Path('/tmp/lami-gpu-stack-compare/minimal.apk')
bt=Path('/home/sato/Android/Sdk/build-tools/36.0.0')
shutil.copy2(base,D/'standard.apk')
z=zipfile.ZipFile(base);m=zipfile.ZipFile(minimal)
with zipfile.ZipFile(D/'unsigned.apk','w',compression=zipfile.ZIP_DEFLATED) as o:
 for n in z.namelist():
  if n.startswith('lib/arm64-v8a/') or (n.startswith('META-INF/') and n.endswith(('.RSA','.SF','.MF'))):continue
  o.writestr(z.getinfo(n),z.read(n))
 for n in m.namelist():
  if n.startswith('lib/arm64-v8a/') and n.endswith('.so'):o.writestr(n,m.read(n))
subprocess.run([str(bt/'zipalign'),'-f','-p','4',str(D/'unsigned.apk'),str(D/'aligned.apk')],check=True)
subprocess.run([str(bt/'apksigner'),'sign','--ks','/home/sato/.android/debug.keystore','--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out',str(D/'minimal.apk'),str(D/'aligned.apk')],check=True)
a=zipfile.ZipFile(D/'standard.apk');b=zipfile.ZipFile(D/'minimal.apk')
keys=[n for n in a.namelist() if not n.startswith(('lib/arm64-v8a/','META-INF/'))]
assert all(a.read(n)==b.read(n) for n in keys)
manifest={'same_non_native_payload':True,'apks':{}}
for label in ['standard','minimal']:
 p=D/(label+'.apk');subprocess.run([str(bt/'apksigner'),'verify',str(p)],check=True)
 zz=zipfile.ZipFile(p);manifest['apks'][label]={'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'native':{n:hashlib.sha256(zz.read(n)).hexdigest() for n in zz.namelist() if n.startswith('lib/arm64-v8a/') and n.endswith('.so')}}
(D/'manifest.json').write_text(json.dumps(manifest,indent=2));print('verified identical non-native payload',flush=True)
