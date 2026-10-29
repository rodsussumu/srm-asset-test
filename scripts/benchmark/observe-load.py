#!/usr/bin/env python3
"""Observe a pre-started disposable Compose profile; login secrets are inherited, never recorded.
Run from the repository root. This script does not start, stop or seed services.
"""
from pathlib import Path
import subprocess,json,sys,threading,datetime,argparse,re
parser=argparse.ArgumentParser(description="Measure the 2 CPU/4 GiB Compose profile with bounded runtime and resource sampling.")
parser.add_argument('--project',required=True)
parser.add_argument('--env-file',required=True)
parser.add_argument('--base-url',required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--timeout',type=int,default=900)
args=parser.parse_args()
if args.timeout < 30: parser.error('Use a timeout of at least 30 seconds.')
out=args.output
if out.exists() and any(out.iterdir()): parser.error('Choose a new empty output directory; previous attempts must be preserved.')
out.mkdir(parents=True,exist_ok=True)
compose=['docker','compose','-p',args.project,'-f','compose.yaml','-f','compose.benchmark.yaml','--env-file',args.env_file]
services=['backend','postgres','frontend','fx-mock']
ids={service:subprocess.check_output(compose+['ps','-q',service],text=True).strip() for service in services}
assert all(ids.values())
template='{"NanoCpus":{{.HostConfig.NanoCpus}},"Memory":{{.HostConfig.Memory}},"MemorySwap":{{.HostConfig.MemorySwap}},"OOMKilled":{{.State.OOMKilled}},"RestartCount":{{.RestartCount}},"Status":{{json .State.Status}}}'
def inspect():
 return {service:json.loads(subprocess.check_output(['docker','inspect','--format',template,identifier],text=True)) for service,identifier in ids.items()}
def cgroups():
 result={}
 for service,identifier in ids.items():
  result[service]={}
  for name in ['cpu.stat','memory.events','memory.peak']:
   p=subprocess.run(['docker','exec',identifier,'cat','/sys/fs/cgroup/'+name],capture_output=True,text=True)
   result[service][name]=p.stdout.strip() if p.returncode==0 else 'unavailable'
 return result
before=inspect()
assert sum(item['NanoCpus'] for item in before.values())==2000000000
assert sum(item['Memory'] for item in before.values())==4*1024**3
assert all(item['MemorySwap']==item['Memory'] and item['Status']=='running' for item in before.values())
metadata={'started_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'limits_before':before,'cgroups_before':cgroups(),'monitor_command':['docker','stats','--no-trunc','--format','{{json .}}']+list(ids.values()),'load_command':[sys.executable,'-u',str(Path(__file__).with_name('http-load.py')),'--base-url',args.base_url,'--clients','10','--requests','50000','--warmup','1000','--output',str(out)]}
(out/'resources.json').write_text(json.dumps(metadata,indent=2)+'\n')
monitor=subprocess.Popen(metadata['monitor_command'],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
def parse_sample(line):
 return json.loads(re.sub(r"\x1b\[[0-?]*[ -/]*[@-~]", "", line))

observed_names=set()
sampling_errors=[]
def sample():
 with (out/'resource-samples.ndjson').open('w') as f:
  for line in monitor.stdout:
   try:
    item=parse_sample(line);observed_names.add(item.get('Name'));item['sampled_at']=datetime.datetime.now(datetime.timezone.utc).isoformat();f.write(json.dumps(item)+'\n');f.flush()
   except json.JSONDecodeError as error:
    if len(sampling_errors)<5:sampling_errors.append(str(error))
thread=threading.Thread(target=sample,daemon=True);thread.start()
try:
 with (out/'http-run.txt').open('w') as log:
  run=subprocess.run(metadata['load_command'],stdout=log,stderr=subprocess.STDOUT,timeout=args.timeout)
 metadata['load_exit_code']=run.returncode
except subprocess.TimeoutExpired:
 metadata['load_exit_code']=124
 metadata['abort_reason']='Global runtime limit exceeded; this attempt is not a successful benchmark.'
finally:
 monitor.terminate()
 try:monitor.wait(timeout=5)
 except subprocess.TimeoutExpired:monitor.kill();monitor.wait()
 thread.join(timeout=5)
 metadata['observed_container_count']=len(observed_names);metadata['sampling_errors']=sampling_errors;metadata['observer_exit_code']=0 if metadata['load_exit_code']==0 and len(observed_names)==4 else 1
 metadata['limits_after']=inspect();metadata['cgroups_after']=cgroups();metadata['finished_at']=datetime.datetime.now(datetime.timezone.utc).isoformat()
 (out/'resources.json').write_text(json.dumps(metadata,indent=2)+'\n')
print('Load exit code:',metadata['load_exit_code'])
sys.exit(metadata['observer_exit_code'])
