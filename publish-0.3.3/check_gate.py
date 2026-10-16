"""Require the pinned four packaged desktops and two emulator jobs to pass."""
from pathlib import Path
import json,os,subprocess,time
P=Path(__file__).resolve().parent;gate=json.loads((P/'release-gate.json').read_text());repo=os.environ['GITHUB_REPOSITORY']
def api(path):return json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/{path}']))
checks=[('desktop_test_commit','Build VLC Glass 0.3.3'),('android_test_commit','Android playback and form-factor checks')]
for attempt in range(240):
 evidence={};pending=[]
 for key,name in checks:
  runs=[r for r in api('actions/runs?head_sha='+gate[key])['workflow_runs'] if r['name']==name]
  if not runs:pending.append(name);continue
  run=max(runs,key=lambda v:v['id']);jobs=api(f"actions/runs/{run['id']}/jobs?per_page=100")['jobs']
  expected=['Windows-x64','macOS-arm64','macOS-x64','Linux-x64'] if key.startswith('desktop') else ['Android-9-phone','Android-15-tablet']
  passed=all(any(label in j['name'] and j['conclusion']=='success' for j in jobs) for label in expected)
  if run['status']!='completed':pending.append(name);continue
  if not passed:raise SystemExit(name+' failed; publication stopped. '+run['html_url'])
  evidence[key]={'sha':gate[key],'run_id':run['id'],'url':run['html_url'],'jobs':[{'name':j['name'],'conclusion':j['conclusion']} for j in jobs]}
 if not pending:
  (P/'gate-evidence.json').write_text(json.dumps(evidence,indent=2));print('All six native platform jobs passed');break
 print('Waiting for '+', '.join(pending),flush=True);time.sleep(10)
else:raise SystemExit('Timed out waiting for native package checks')
