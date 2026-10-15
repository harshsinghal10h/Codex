"""Publish only tested packages from the pinned matrix, independent of tag moves."""
from pathlib import Path
import hashlib,json,os,subprocess,sys
manifest=json.loads(Path('desktop-release/manifest.json').read_text());repo=os.environ['GITHUB_REPOSITORY']
def api(path):return json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/{path}']))
run=api(f"actions/runs/{manifest['workflow_run']}")
if run['head_sha']!=manifest['source_commit']:raise ValueError('Unexpected build source')
jobs=api(f"actions/runs/{manifest['workflow_run']}/jobs?per_page=100")['jobs']
expected={'Windows-x64','macOS-arm64','macOS-x64','Linux-x64'}
passed={label for label in expected if any(j['name'].startswith('desktop (') and label in j['name'] and j['conclusion']=='success' for j in jobs)}
if passed!=expected:raise ValueError('All four package jobs must pass')
if '--check-jobs' in sys.argv:print('Verified all four desktop package jobs');raise SystemExit()
root=Path('release-assets');paths=[p for p in root.rglob('*') if p.suffix in ('.exe','.zip','.gz')]
if len(paths)!=4:raise ValueError('Expected exactly four desktop packages')
reports=list(root.glob('VLC-Glass-*/ui-smoke.json'))
if len(reports)!=4:raise ValueError('Missing packaged UI evidence')
for p in reports:
 evidence=json.loads(p.read_text())
 if not all(evidence.get(k) for k in ('passed','visible_overlay','visible_video','protected_previous','protected_replay','responsive_prompt')):raise ValueError('Packaged visual check failed: '+str(p))
for p in root.glob('VLC-Glass-*/*smoke.json'):p.rename(p.with_name(p.parent.name+'-'+p.name))
(root/'SHA256SUMS-desktop.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in paths))
(root/'Desktop-build-0.3.1.json').write_text(json.dumps({'source_commit':manifest['source_commit'],'build_run':run['html_url'],'packages':[{'file':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in paths],'physical_device_tested':False},indent=2)+'\n')
print('Verified desktop packages and visual evidence')
