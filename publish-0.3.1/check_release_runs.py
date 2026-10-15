"""Publish only after the concrete desktop and Android integration checks pass."""
import json,os,re,subprocess,time
checks=[('74b8a3e09122792389d63fc3066e94f3b4cd4cf6','Build VLC Glass 0.3.1'),('b24b9d663675be09c1f38d5dff9516014d74559d','Android playback and form-factor checks')]
repo=os.environ['GITHUB_REPOSITORY']
for i in range(270):
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
