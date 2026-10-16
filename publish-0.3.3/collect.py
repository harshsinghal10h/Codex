"""Verify exact artifact bytes and real integration reports before publishing."""
from pathlib import Path
import hashlib,json,os,subprocess,shutil
P=Path(__file__).resolve().parent;repo=os.environ['GITHUB_REPOSITORY'];gate=json.loads((P/'release-gate.json').read_text());runs=json.loads((P/'gate-evidence.json').read_text());out=P/'release'
required=('passed','visible_overlay','visible_video','protected_previous','protected_replay','responsive_prompt','fullscreen_control','responsive_layouts','direct_controls','touch_targets','controls_fit','steady_dom','semantic_result_seek','play_pause_control','seek_buttons','speed_control','track_queue_access','volume_control','dialog_focus','keyboard_button','lock_controls','queue_navigation')
def download(run,name,destination):subprocess.run(['gh','run','download',str(run),'-R',repo,'-n',name,'-D',str(destination)],check=True)
packages=[];desktop_reports=[]
for label in ('Windows-x64','macOS-arm64','macOS-x64','Linux-x64'):
 directory=P/'verified'/label;download(runs['desktop_test_commit']['run_id'],'VLC-Glass-'+label,directory)
 report=json.loads((directory/'ui-smoke.json').read_text())
 if not all(report.get(k) for k in required):raise ValueError('Incomplete native UI evidence: '+label)
 for file in directory.glob('*smoke.json'):shutil.copyfile(file,out/('VLC-Glass-'+label+'-'+file.name))
 for image in directory.glob('ui-*.png'):shutil.copyfile(image,out/('VLC-Glass-'+label+'-'+image.name))
 paths=[p for p in directory.rglob('*') if p.is_file() and p.suffix in ('.exe','.zip','.gz')]
 if len(paths)!=1:raise ValueError('Expected one native package: '+label)
 file=paths[0];shutil.copyfile(file,out/file.name);packages.append({'file':file.name,'sha256':hashlib.sha256(file.read_bytes()).hexdigest(),'bytes':file.stat().st_size});desktop_reports.append({'platform':label,**report})
android_reports=[]
for label in ('Android-9-phone','Android-15-tablet'):
 directory=P/'verified'/label;download(runs['android_test_commit']['run_id'],'QA-'+label,directory);report=json.loads((directory/'android-smoke.json').read_text())
 flags=('passed','native_playback','scan_complete','synthetic_skip','mask_applied','protected_previous','protected_replay','portrait_playback','landscape_playback','portrait_video_frame','landscape_video_frame','portrait_visible_ui','landscape_visible_ui','native_video_chunk','native_vector_retrieval','embedding2_request_contract','rotation_control','return_portrait_playback','return_portrait_video_frame')
 if not all(report.get(k) for k in flags):raise ValueError('Incomplete Android playback evidence: '+label)
 for orientation in ('portrait','landscape'):
  if not all(report.get(orientation+'_contract',{}).get(k) for k in ('direct_controls','touch_targets','controls_fit','steady_dom','semantic_result_seek','play_pause_control','seek_buttons','speed_control','track_queue_access','volume_control','dialog_focus','keyboard_button','lock_controls','queue_navigation')):raise ValueError('Android UI/search contract failed: '+orientation)
 for image in directory.glob('android-*.png'):shutil.copyfile(image,out/('VLC-Glass-'+label+'-'+image.name))
 android_reports.append({'form_factor':label,**report})
manifest=json.loads((P/'manifest.json').read_text())
android_source=gate.get('android_source_commit',gate['source_commit'])
if manifest['source_commit']!=android_source:raise ValueError('Unexpected Android source identity')
source_trees={}
def source_tree(commit):
 if commit not in source_trees:
  tree=json.loads(subprocess.check_output(['gh','api','repos/'+repo+'/git/trees/'+commit+'?recursive=1']))
  if tree.get('truncated'):raise ValueError('Truncated source tree')
  source_trees[commit]=tree
 return source_trees[commit]
def android_inputs(commit):
 tree=source_tree(commit)
 prefixes=('VLC-Glass/android/','VLC-Glass/assets/','VLC-Glass/vendor/','VLC-Glass/upstream/','VLC-Glass/tools/')
 files=('VLC-Glass/package.json','VLC-Glass/package-lock.json')
 return {entry['path']:entry['sha'] for entry in tree['tree'] if entry['type']=='blob' and (entry['path'].startswith(prefixes) or entry['path'] in files)}
def product_inputs(commit):return {e['path']:e['sha'] for e in source_tree(commit)['tree'] if e['type']=='blob' and e['path'].startswith('VLC-Glass/')}
if product_inputs(gate['source_commit'])!=product_inputs(gate['desktop_test_commit']):raise ValueError('Desktop package source differs from the archived product source')
# The follow-up source change is confined to desktop QA. Reuse the already
# tested signed APKs only when every Android build input is byte-identical.
if android_inputs(android_source)!=android_inputs(gate['source_commit']):raise ValueError('Android build inputs changed; fresh APK tests required')
desktop_packages=list(packages)
for item in manifest['files']:packages.append({'file':item['name'],'bytes':item['bytes'],'sha256':item['sha256']})
(out/'SHA256SUMS-desktop.txt').write_text(''.join(p['sha256']+'  '+p['file']+'\n' for p in desktop_packages))
(out/'SHA256SUMS-packages.txt').write_text(''.join(p['sha256']+'  '+p['file']+'\n' for p in packages))
(out/'Build-checks-0.3.3.json').write_text(json.dumps({'source_commit':gate['source_commit'],'android_source_commit':android_source,'android_build_inputs_match':True,'desktop_source_inputs_match':True,'runs':runs,'packages':packages,'desktop_reports':desktop_reports,'android_reports':android_reports,'physical_device_tested':False,'live_cloud_search_tested':False,'recognition_accuracy_evaluated':False},indent=2)+'\n')
print('Verified all package reports and artifact hashes')
