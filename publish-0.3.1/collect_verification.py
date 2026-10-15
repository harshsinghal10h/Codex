"""Attach the actual emulator reports to the release after the immutable gate."""
from pathlib import Path
import io,json,os,subprocess,zipfile
repo=os.environ['GITHUB_REPOSITORY']
sha='968a6fb3846abf8349973b1284048bee286cc94f'
def api(path):return subprocess.check_output(['gh','api',f'repos/{repo}/{path}'])
runs=json.loads(api(f'actions/runs?head_sha={sha}'))['workflow_runs']
run=max((r for r in runs if r['name']=='Android playback and form-factor checks'),key=lambda r:r['id'])
if run['status']!='completed' or run['conclusion']!='success':raise SystemExit('Android integration checks have not passed')
reports=[]
for item in json.loads(api(f"actions/runs/{run['id']}/artifacts"))['artifacts']:
 if item['name'] not in ('QA-Android-9-phone','QA-Android-15-tablet'):continue
 with zipfile.ZipFile(io.BytesIO(api(f"actions/artifacts/{item['id']}/zip"))) as z:
  name=next(n for n in z.namelist() if Path(n).name=='android-smoke.json')
  report=json.loads(z.read(name))
  if not report.get('passed') or not report.get('protected_replay') or not report.get('protected_previous') or not report.get('portrait_video_frame') or not report.get('landscape_video_frame') or not report.get('portrait_visible_ui') or not report.get('landscape_visible_ui'):raise ValueError('Incomplete playback evidence')
  reports.append({'form_factor':item['name'],**report})
if {r['api'] for r in reports}!={28,35}:raise ValueError('Missing phone or tablet evidence')
manifest=json.loads((Path(__file__).parent/'manifest.json').read_text())
evidence={'source_commit':manifest['source_commit'],'desktop_source_commit':'74b8a3e09122792389d63fc3066e94f3b4cd4cf6','android_test_commit':sha,'workflow_run':run['html_url'],'emulator_reports':reports,'physical_device_tested':False,'classifier_accuracy_evaluated':False,'cloud_search_evaluated':False,'laya_checkpoint_evaluated':False}
(Path(__file__).parent/'release/Android-playback-checks-0.3.1.json').write_text(json.dumps(evidence,indent=2)+'\n')
print('Collected successful phone and tablet integration reports')
