from pathlib import Path
import subprocess,time,base64,json,re
D=Path('/tmp/lami-gpu-controlled-results');A=['adb','-s','192.168.52.52:39859'];pkg='io.github.ninbyo02.lami.gpucontrolled';results=[]
def run(args,**kw):return subprocess.run(A+args,check=True,timeout=90,**kw)
try:
 assert json.loads((D/'manifest.json').read_text())['same_non_native_payload']
 run(['install','-r',str(D/'standard.apk')])
 run(['shell','am','start','-n',pkg+'/io.github.ninbyo02.lami.MainActivity'])
 time.sleep(3)
 run(['shell','run-as',pkg,'mkdir','-p','files/cases'])
 run(['shell','cat /data/local/tmp/lami-gpu-model-ab.litertlm | run-as '+pkg+' tee files/model.litertlm > /dev/null'])
 identity=subprocess.check_output(A+['shell','run-as',pkg,'sha256sum','files/model.litertlm'],text=True).split()[0]
 assert identity=='ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42'
 last='standard'
 for stack,context,cache in [('standard',4096,'null'),('minimal',4096,'null'),('minimal',1024,'null'),('minimal',1024,'fresh')]:
  label=f'{stack}_{context}_{cache}';stamp='controlled_'+label+'_'+str(int(time.time()));print('START',label,flush=True)
  run(['shell','am','force-stop',pkg])
  if last!=stack:run(['install','-r',str(D/(stack+'.apk'))]);last=stack
  rel='files/cases/'+stamp;run(['shell','run-as',pkg,'mkdir','-p',rel]);run(['shell','run-as',pkg,'cp','files/model.litertlm',rel+'/model.litertlm'])
  cmd=['shell','am','broadcast','--include-stopped-packages','--receiver-foreground','-n',pkg+'/io.github.ninbyo02.lami.gpu.LiteRtLmGpuBenchmarkReceiver','-a','io.github.ninbyo02.lami.action.LITERT_LM_GPU_BENCHMARK','--es','timestamp',stamp,'--es','backend_variant','gallery-chat-parity','--es','close_policy','normal','--es','phase','send-message','--ei','controlled_context',str(context),'--es','controlled_cache',cache,'--el','timeout_ms','45000']
  for key,value in [('model_path_base64','/data/user/0/'+pkg+'/'+rel+'/model.litertlm'),('prompts_base64','こんにちは'),('max_output_tokens_list_base64',str(context))]:cmd+=['--es',key,base64.b64encode(value.encode()).decode()]
  run(['shell','am','start','-n',pkg+'/io.github.ninbyo02.lami.MainActivity'])
  time.sleep(4)
  run(cmd)
  deadline=time.monotonic()+65;report=None
  while time.monotonic()<deadline:
   v=subprocess.run(A+['exec-out','run-as',pkg,'cat','files/litert_lm_gpu_benchmark_'+stamp+'.md'],capture_output=True,timeout=10)
   if b'route_type: `litert_lm_gpu_benchmark`' in v.stdout and b'### sanitized_output' in v.stdout:
    report=v.stdout.decode();break
   time.sleep(2)
  markers=subprocess.run(A+['exec-out','run-as',pkg,'cat','files/litert_lm_gpu_benchmark_marker_history.txt'],capture_output=True,timeout=10).stdout.decode(errors='replace')
  (D/(label+'-markers.txt')).write_text(markers)
  if report:
   (D/(label+'.md')).write_text(report)
   summary='\n'.join(x for x in report.splitlines() if x.startswith('- ') and any(k in x for k in ['status:','reason:','send_exception','callback_on_','model_length:','send_api_variant:','timeout:']))
   print(label,summary,report[report.find('### raw_output'):][:450],flush=True)
  else:summary='host_deadline';print(label,summary,markers[-1600:],flush=True)
  results.append({'label':label,'timestamp':stamp,'summary':summary,'terminal_report':report is not None})
  (D/'results.json').write_text(json.dumps(results,indent=2))
  run(['shell','am','force-stop',pkg])
  run(['shell','run-as',pkg,'rm',rel+'/model.litertlm'])
finally:
 subprocess.run(A+['shell','am','force-stop',pkg],timeout=15)
 subprocess.run(A+['shell','am','start','-n','com.openai.chatgpt/.MainActivity'],timeout=15)
 print('FINISHED',flush=True)
