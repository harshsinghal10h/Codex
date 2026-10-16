"""Verify exact artifact bytes and real integration reports before publishing."""
from pathlib import Path
import hashlib,json,os,subprocess,shutil
P=Path(__file__).resolve().parent;repo=os.environ['GITHUB_REPOSITORY'];gate=json.loads((P/'release-gate.json').read_text());runs=json.loads((P/'gate-evidence.json').read_text());out=P/'release'
required=('passed','visible_overlay','visible_video','protected_previous','protected_replay','responsive_prompt','compact_controls','controls_fit','steady_dom','semantic_result_seek')
def download(run,name,destination):subprocess.run(['gh','run','download',str(run),'-R',repo,'-n',name,'-D',str(destination)],check=True)
packages=[];desktop_reports=[]
for label in ('Windows-x64','macOS-arm64','macOS-x64','Linux-x64'):
 directory=P/'verified'/label;download(runs['desktop_test_commit']['run_id'],'VLC-Glass-'+label,directory)
 report=json.loads((directory/'ui-smoke.json').read_text())
 if not all(report.get(k) for k in required):raise ValueError('Incomplete native UI evidence: '+label)
 for file in directory.glob('*smoke.json'):shutil.copyfile(file,out/(label+'-'+file.name))
 paths=[p for p in directory.iterdir() if p.suffix in ('.exe','.zip','.gz')]
 if len(paths)!=1:raise ValueError('Expected one native package: '+label)
 file=paths[0];shutil.copyfile(file,out/file.name);packages.append({'file':file.name,'sha256':hashlib.sha256(file.read_bytes()).hexdigest(),'bytes':file.stat().st_size});desktop_reports.append({'platform':label,**report})
android_reports=[]
for label in ('Android-9-phone','Android-15-tablet'):
 directory=P/'verified'/label;download(runs['android_test_commit']['run_id'],'QA-'+label,directory);report=json.loads((directory/'android-smoke.json').read_text())
 flags=('passed','native_playback','scan_complete','synthetic_skip','mask_applied','protected_previous','protected_replay','portrait_playback','landscape_playback','portrait_video_frame','landscape_video_frame','portrait_visible_ui','landscape_visible_ui','native_video_chunk','native_vector_retrieval','embedding2_request_contract')
 if not all(report.get(k) for k in flags):raise ValueError('Incomplete Android playback evidence: '+label)
 for orientation in ('portrait','landscape'):
  if not all(report.get(orientation+'_contract',{}).get(k) for k in ('compact_controls','controls_fit','steady_dom','semantic_result_seek')):raise ValueError('Android UI/search contract failed: '+orientation)
 android_reports.append({'form_factor':label,**report})
manifest=json.loads((P/'manifest.json').read_text())
if manifest['source_commit']!=gate['source_commit']:raise ValueError('Unexpected Android source identity')
desktop_packages=list(packages)
for item in manifest['files']:packages.append({'file':item['name'],'bytes':item['bytes'],'sha256':item['sha256']})
(out/'SHA256SUMS-desktop.txt').write_text(''.join(p['sha256']+'  '+p['file']+'\n' for p in desktop_packages))
(out/'SHA256SUMS-packages.txt').write_text(''.join(p['sha256']+'  '+p['file']+'\n' for p in packages))
(out/'Build-checks-0.3.2.json').write_text(json.dumps({'source_commit':gate['source_commit'],'runs':runs,'packages':packages,'desktop_reports':desktop_reports,'android_reports':android_reports,'physical_device_tested':False,'live_cloud_search_tested':False,'recognition_accuracy_evaluated':False},indent=2)+'\n')
print('Verified all package reports and artifact hashes')
