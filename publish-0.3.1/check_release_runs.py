"""Publish only after the concrete desktop and Android integration checks pass."""
import json,os,re,subprocess,time
checks=[('935b530a8f75b6b034ee9617ce198bc8f4740d6e','Build VLC Glass 0.3.1'),('8ace2c2f23fbe68fc281b691e27fbed95a7be0c1','Android playback and form-factor checks')]
repo=os.environ['GITHUB_REPOSITORY']
for i in range(90):
 pending=[]
 for sha,name in checks:
  assert re.fullmatch('[0-9a-f]{40}',sha)
  data=json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/actions/runs?head_sha={sha}']))
  runs=[r for r in data['workflow_runs'] if r['name']==name]
  if not runs:pending.append(name);continue
  run=max(runs,key=lambda r:r['id'])
  if run['status']=='completed':
   if run['conclusion']!='success':raise SystemExit(name+' failed; release publication stopped. See '+run['html_url'])
  else:pending.append(name)
 if not pending:print('All desktop and Android integration checks passed');break
 print('Waiting for: '+', '.join(pending),flush=True);time.sleep(10)
else:raise SystemExit('Timed out waiting for integration checks')
