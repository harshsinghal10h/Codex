"""Publish only after the concrete desktop and Android integration checks pass."""
import json,os,re,subprocess,time
checks=[('74b8a3e09122792389d63fc3066e94f3b4cd4cf6','Build VLC Glass 0.3.1'),('db5348777dcc46589012b28f1ee9fde26b393427','Android playback and form-factor checks')]
repo=os.environ['GITHUB_REPOSITORY']
# Android-only fixes may advance the source archive; the tested desktop source
# must remain byte-identical outside Android before its artifacts are reused.
from pathlib import Path
manifest=json.loads((Path(__file__).parent/'manifest.json').read_text())
def component(source):
 commit=json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/git/commits/{source}']))
 tree=json.loads(subprocess.check_output(['gh','api',f"repos/{repo}/git/trees/{commit['tree']['sha']}?recursive=1"]))
 if tree.get('truncated'):raise ValueError('Incomplete source tree')
 return {e['path']:e['sha'] for e in tree['tree'] if e['type']=='blob' and e['path'].startswith('VLC-Glass/') and not e['path'].startswith('VLC-Glass/android/')}
if component(manifest['source_commit'])!=component(checks[0][0]):raise SystemExit('Desktop source changed since its successful package checks')

for i in range(270):
 pending=[]
 for sha,name in checks:
  assert re.fullmatch('[0-9a-f]{40}',sha)
  data=json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/actions/runs?head_sha={sha}']))
  runs=[r for r in data['workflow_runs'] if r['name']==name]
  if not runs:pending.append(name);continue
  run=max(runs,key=lambda r:r['id'])
  if name=='Build VLC Glass 0.3.1':
   jobs=json.loads(subprocess.check_output(['gh','api',f"repos/{repo}/actions/runs/{run['id']}/jobs?per_page=100"]))['jobs']
   expected={'Windows-x64','macOS-arm64','macOS-x64','Linux-x64'}
   if all(any(j['name'].startswith('desktop (') and label in j['name'] and j['conclusion']=='success' for j in jobs) for label in expected):continue
  if run['status']=='completed':
   if run['conclusion']!='success':raise SystemExit(name+' failed; release publication stopped. See '+run['html_url'])
  else:pending.append(name)
 if not pending:print('All desktop and Android integration checks passed');break
 print('Waiting for: '+', '.join(pending),flush=True);time.sleep(10)
else:raise SystemExit('Timed out waiting for integration checks')
