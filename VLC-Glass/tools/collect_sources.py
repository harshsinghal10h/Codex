#!/usr/bin/env python3
"""Prepare small connector batches of source files; never include private keys."""
import json,pathlib
P=pathlib.Path(__file__).resolve().parents[1]
def files():
 for p in P.rglob('*'):
  rel=p.relative_to(P)
  if not p.is_file() or any(v in rel.parts for v in ['build','dist','private','__pycache__','libvlc-aar','onnx-android','node_modules','vlc']):continue
  if p.suffix in ['.so','.dex','.jar','.onnx','.zip','.aar','.pyc','.exe','.p12','.jks']:continue
  if rel.parts[0]=='upstream' and 'sentrysearch' in rel.parts and not (p.suffix=='.py' and p.parent.name=='sentrysearch') and p.name not in ['LICENSE','pyproject.toml']:continue
  yield p,rel
if __name__=='__main__':
 batches=[];batch=[];size=0
 for p,rel in files():
  content=p.read_text();n=len(content.encode())
  if batch and size+n>100000:batches.append(batch);batch=[];size=0
  batch.append({'path':'VLC-Glass/'+rel.as_posix(),'mode':'100644','type':'blob','content':content});size+=n
 if batch:batches.append(batch)
 destination=P.parent/'connector-batches';destination.mkdir(exist_ok=True)
 for i,b in enumerate(batches):(destination/(str(i)+'.json')).write_text(json.dumps(b))
 print(json.dumps({'batches':len(batches),'files':sum(map(len,batches))}))
