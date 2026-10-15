#!/usr/bin/env python3
"""Check the shipped APK's dependency boundaries and native binding signatures."""
import json,pathlib,re,struct,zipfile
P=pathlib.Path(__file__).resolve().parents[1]
api=json.loads((P/'vendor/api-inventory.json').read_text())
required={
 'Lorg/videolan/libvlc/MediaPlayer;':{'getTime':[],'getLength':[],'getPlayerState':[],'isSeekable':[],'getVideoTracks':[],'getVideoTrack':[],'setVideoTrack':['I'],'setTime':['J'],'setRate':['F'],'setVolume':['I'],'setAudioTrack':['I'],'setSpuTrack':['I'],'setAudioDelay':['J'],'setSpuDelay':['J'],'getChapters':['I'],'setChapter':['I'],'addSlave':['I','Landroid/net/Uri;','Z'],'setEqualizer':['Lorg/videolan/libvlc/MediaPlayer$Equalizer;'],'getVLCVout':[],'setRenderer':['Lorg/videolan/libvlc/RendererItem;']},
 'Lorg/videolan/libvlc/interfaces/IVLCVout;':{'setVideoView':['Landroid/view/TextureView;'],'setSubtitlesView':['Landroid/view/TextureView;'],'attachViews':['Lorg/videolan/libvlc/interfaces/IVLCVout$OnNewVideoLayoutListener;'],'setWindowSize':['I','I']},
 'Lorg/videolan/libvlc/MediaPlayer$Equalizer;':{'create':[],'setAmp':['I','F'],'setPreAmp':['F']},
 'Lorg/videolan/libvlc/RendererDiscoverer;':{'list':['Lorg/videolan/libvlc/interfaces/ILibVLC;'],'start':[],'stop':[],'setEventListener':['Lorg/videolan/libvlc/RendererDiscoverer$EventListener;']}}
for cls,methods in required.items():
 for name,params in methods.items():assert any(m['name']==name and m['parameters']==params for m in api.get(cls,[])),(cls,name,params)
print('PASS:',sum(len(v) for v in required.values()),'native API signatures match the pinned LibVLC dependency')
apk=P/'dist/VLC-Glass-Android-0.2.0-arm64.apk'
with zipfile.ZipFile(apk) as z:
 names=z.namelist()
 for f in ['index.html','style.css','anatomy.css','deck.js','host.js','app.js','glass.js','player.js']:assert 'assets/ui/'+f in names,f
 assert 'lib/arm64-v8a/libvlc.so' in names and 'lib/arm64-v8a/libvlcjni.so' in names
 assert not any('fermata' in x.lower() for x in names)
 assert not any('signing' in x.lower() or x.endswith('.p12') for x in names)
 classes=[]
 for n in names:
  if not re.match(r'classes[0-9]*\.dex$',n):continue
  b=z.read(n);u=lambda o:struct.unpack_from('<I',b,o)[0]
  strings=[]
  for i in range(u(56)):
   o=u(u(60)+i*4)
   while b[o]&128:o+=1
   o+=1;strings.append(b[o:b.index(0,o)].decode('utf8',errors='replace'))
  types=[strings[u(u(68)+i*4)] for i in range(u(64))]
  classes += [types[u(u(100)+i*32)] for i in range(u(96))]
 assert all(c.startswith(('Lcom/harsh/vlcglass/','Lorg/videolan/libvlc/')) for c in classes),[c for c in classes if not c.startswith(('Lcom/harsh/vlcglass/','Lorg/videolan/libvlc/'))]
 assert len([c for c in classes if c.startswith('Lorg/videolan/libvlc/')])==136
 print('PASS: APK contains app code and 136 isolated LibVLC classes; no donor application components')
 print('PASS: all UI assets and both arm64 native libraries are packaged; signing key excluded')
html=(P/'assets/ui/index.html').read_text();assert 'object-src' in html and 'base-uri' in html
native=(P/'android/src/com/harsh/vlcglass/MainActivity.java').read_text();assert 'setAllowFileAccess(false)' in native and 'setAllowContentAccess(false)' in native
assert 'vlcglass.local' in native and 'takePersistableUriPermission' in native
assert 'html.player-open' in (P/'assets/ui/style.css').read_text()
print('PASS: file-access boundary, persistent document grants, and native video transparency are present')
