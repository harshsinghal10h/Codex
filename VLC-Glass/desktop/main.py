#!/usr/bin/env python3
"""Qt + LibVLC desktop host for the shared VLC Glass interface."""
import concurrent.futures
import ctypes
import json
import os
from pathlib import Path
import sqlite3
import sys
import time

ROOT=Path(getattr(sys,'_MEIPASS',Path(__file__).resolve().parents[1]))
sys.path.insert(0,str(ROOT))
if '--companion' in sys.argv:
    from intelligence.server import main
    main(sys.argv[sys.argv.index('--companion')+1:]);raise SystemExit()
if (ROOT/'vendor/vlc').exists():
    folder=ROOT/'vendor/vlc'
    if sys.platform=='darwin' and not getattr(sys,'frozen',False):folder=Path('/Applications/VLC.app/Contents/MacOS')
    if sys.platform=='win32':
        DLL_HANDLE=os.add_dll_directory(str(folder));os.environ['PYTHON_VLC_LIB_PATH']=str(folder/'libvlc.dll')
    elif sys.platform=='darwin':os.environ['PYTHON_VLC_LIB_PATH']=str(folder/'lib/libvlc.dylib')
    else:os.environ['PYTHON_VLC_LIB_PATH']=str(folder/'libvlc.so.5')
    os.environ['VLC_PLUGIN_PATH']=str(folder/'plugins')
    os.environ['PYTHON_VLC_MODULE_PATH']=str(folder)

import vlc
from PySide6.QtCore import Qt,QObject,Signal,Slot,QTimer,QUrl,QBuffer,QByteArray,QIODevice,QStandardPaths
from PySide6.QtGui import QColor,QKeySequence,QShortcut,QSurfaceFormat
from PySide6.QtWidgets import QApplication,QMainWindow,QWidget,QStackedLayout,QFileDialog,QMessageBox
from PySide6.QtWebEngineCore import QWebEngineUrlScheme,QWebEngineUrlSchemeHandler,QWebEnginePage,QWebEngineScript
from PySide6.QtWebEngineWidgets import QWebEngineView
from PySide6.QtWebChannel import QWebChannel
from intelligence.vision import Scanner
from intelligence.timeline import Schedule
from intelligence.sentry_adapter import SentryAdapter

video_format=QSurfaceFormat.defaultFormat();video_format.setAlphaBufferSize(8);QSurfaceFormat.setDefaultFormat(video_format)

scheme=QWebEngineUrlScheme(b'vlcglass');scheme.setSyntax(QWebEngineUrlScheme.Syntax.HostAndPort);scheme.setDefaultPort(443)
scheme.setFlags(QWebEngineUrlScheme.Flag.SecureScheme|QWebEngineUrlScheme.Flag.LocalScheme|QWebEngineUrlScheme.Flag.LocalAccessAllowed)
QWebEngineUrlScheme.registerScheme(scheme)

class Assets(QWebEngineUrlSchemeHandler):
    def requestStarted(self,job):
        from mimetypes import guess_type
        if job.requestUrl().host()!='local':job.fail(job.Error.UrlNotFound);return
        path=job.requestUrl().path().lstrip('/') or 'index.html'
        if '/' in path or '..' in path:job.fail(job.Error.UrlNotFound);return
        file=ROOT/'assets/ui'/path
        if not file.is_file():job.fail(job.Error.UrlNotFound);return
        data=file.read_bytes()
        if path=='index.html':
            # Chromium does not consistently match CSP 'self' for custom schemes.
            # Name this app's local host explicitly; retain CSP enforcement.
            data=data.replace(b"'self'",b"'self' vlcglass://local:*")
        buffer=QBuffer(job);buffer.setData(QByteArray(data));buffer.open(QIODevice.OpenModeFlag.ReadOnly)
        job.reply((guess_type(str(file))[0] or 'application/octet-stream').encode(),buffer)

class Page(QWebEnginePage):
    def acceptNavigationRequest(self,url,kind,main):
        return url.scheme()=='vlcglass' and url.host()=='local'

class Bridge(QObject):
    incoming=Signal(str)
    @Slot(str)
    def request(self,text):
        if len(text)<100000:self.incoming.emit(text)

class Player(QMainWindow):
    worker_event=Signal(object)
    def __init__(self):
        super().__init__();self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground);self.setWindowTitle('VLC Glass');self.resize(1200,780);self.setMinimumSize(320,360)
        self.data=Path(QStandardPaths.writableLocation(QStandardPaths.StandardLocation.AppLocalDataLocation));self.data.mkdir(parents=True,exist_ok=True)
        self.db=sqlite3.connect(self.data/'library.sqlite');self.db.execute('CREATE TABLE IF NOT EXISTS media(id INTEGER PRIMARY KEY,uri TEXT UNIQUE,title TEXT,kind TEXT,duration INTEGER DEFAULT 0,size INTEGER DEFAULT 0,position INTEGER DEFAULT 0,favorite INTEGER DEFAULT 0,added INTEGER,lastplayed INTEGER DEFAULT 0)')
        self.db.execute('CREATE TABLE IF NOT EXISTS playlists(id INTEGER PRIMARY KEY,name TEXT,entries TEXT DEFAULT "[]")');self.db.commit();self.db.row_factory=sqlite3.Row
        self.settings_file=self.data/'settings.json'
        try:self.settings=json.loads(self.settings_file.read_text())
        except (OSError,ValueError):self.settings={}
        self.current=-1;self.queue=[];self.cursor=-1;self.guard={};self.schedule=None;self.generation=0;self.seek_target=None;self.seek_at=0;self.repeat='off';self.shuffle=False;self.sleep_at=0;self.ab_a=self.ab_b=-1;self.bookmarks={};self.pending_resume=0;self.fault='';self.ended=False
        self.scans=concurrent.futures.ThreadPoolExecutor(max_workers=1,thread_name_prefix='video-scan');self.searches=concurrent.futures.ThreadPoolExecutor(max_workers=1,thread_name_prefix='scene-search')
        self.scanner=Scanner(ROOT/'assets/models/320n.onnx',self.data/'scan-maps')
        upstream=next((ROOT/'upstream/sentrysearch').glob('sentrysearch-*'))
        self.sentry=SentryAdapter(self.data/'sentry-index',upstream)
        self.vlc=vlc.Instance('--no-plugins-cache','--no-video-title-show','--no-snapshot-preview','--audio-time-stretch');self.player=self.vlc.media_player_new()
        from desktop.video_surface import VideoSurface
        self.software_video=True
        self.surface=VideoSurface();self.surface.attach(self.player)
        container=self.surface;self.setCentralWidget(container)
        layout=QStackedLayout(container);layout.setContentsMargins(0,0,0,0)
        # The frame surface and web controls share Qt's backing store. Native
        # video child windows bypass that composition and obscure the controls.
        self.web=QWebEngineView(container)
        self.web.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)
        self.web.setAttribute(Qt.WidgetAttribute.WA_OpaquePaintEvent,False)
        page=Page(self.web);page.setBackgroundColor(QColor(0,0,0,0));self.web.setPage(page);layout.addWidget(self.web);layout.setCurrentWidget(self.web)
        self.assets=Assets(self.web);page.profile().installUrlSchemeHandler(b'vlcglass',self.assets)
        self.bridge=Bridge();self.bridge.incoming.connect(self.request);channel=QWebChannel(page);channel.registerObject('native',self.bridge);page.setWebChannel(channel);self.channel=channel
        from PySide6.QtCore import QFile
        file=QFile(':/qtwebchannel/qwebchannel.js');file.open(QIODevice.OpenModeFlag.ReadOnly);lib=bytes(file.readAll()).decode()
        script=QWebEngineScript();script.setInjectionPoint(QWebEngineScript.InjectionPoint.DocumentCreation);script.setWorldId(QWebEngineScript.ScriptWorldId.MainWorld);script.setRunsOnSubFrames(False)
        script.setSourceCode("(()=>{let q=[],bridge;window.GlassHost={request:s=>bridge?bridge.request(s):q.push(s)};"+lib+";new QWebChannel(qt.webChannelTransport,c=>{bridge=c.objects.native;q.forEach(s=>bridge.request(s));q=[];});})();")
        page.scripts().insert(script)
        self.worker_event.connect(self.handle_worker_event)
        self.web.loadFinished.connect(self.compose_overlay)
        self.web.setUrl(QUrl('vlcglass://local/index.html'))
        self.clock=QTimer(self);self.clock.timeout.connect(self.tick);self.clock.start(50);self.last_ui=0;self.last_save=0
        QShortcut(QKeySequence('F11'),self,activated=self.fullscreen)
        try:
            import keyring
            key=keyring.get_password('VLCGlass','gemini')
            if key:os.environ['GEMINI_API_KEY']=key
        except Exception:pass
    def compose_overlay(self,*args):
        from PySide6.QtQuickWidgets import QQuickWidget
        for child in self.web.findChildren(QQuickWidget):
            child.setClearColor(QColor(0,0,0,0))
            child.setAttribute(Qt.WidgetAttribute.WA_AlwaysStackOnTop,True)
            child.setAttribute(Qt.WidgetAttribute.WA_OpaquePaintEvent,False)
        self.web.page().setBackgroundColor(QColor(0,0,0,255))
        self.web.page().setBackgroundColor(QColor(0,0,0,0))
        self.web.update();self.surface.update();self.update()
    def row(self,id=None):
        result=self.db.execute('SELECT * FROM media WHERE id=?',(self.current if id is None else id,)).fetchone();return dict(result) if result else None
    def library(self):return [dict(r) for r in self.db.execute('SELECT * FROM media ORDER BY added DESC')]
    def playlists(self):return [{**dict(r),'entries':json.loads(r['entries'])} for r in self.db.execute('SELECT * FROM playlists')]
    def send(self,type,data):self.web.page().runJavaScript('window.GlassApp&&window.GlassApp.receive('+json.dumps({'type':type,'data':data},ensure_ascii=True)+')')
    def refresh(self):self.send('library',{'media':self.library(),'playlists':self.playlists()})
    def details(self):
        def tracks(value):return [{'id':a,'name':b.decode(errors='replace') if isinstance(b,bytes) else b} for a,b in value or []]
        try:w,h=self.player.video_get_size()
        except Exception:w,h=0,0
        return {'version':vlc.libvlc_get_version().decode(),'videoWidth':w,'videoHeight':h,'audioTracks':tracks(self.player.audio_get_track_description()),'subtitleTracks':tracks(self.player.video_get_spu_description()),'videoTracks':tracks(self.player.video_get_track_description()),'audioTrack':self.player.audio_get_track(),'subtitleTrack':self.player.video_get_spu(),'videoTrack':self.player.video_get_track(),'chapters':[]}
    def state(self):
        status={vlc.State.NothingSpecial:0,vlc.State.Opening:1,vlc.State.Buffering:2,vlc.State.Playing:3,vlc.State.Paused:4,vlc.State.Stopped:5,vlc.State.Ended:6,vlc.State.Error:7}.get(self.player.get_state(),0)
        return {'current':self.current,'item':self.row(),'playing':bool(self.player.is_playing()),'time':max(0,self.player.get_time()),'duration':max(0,self.player.get_length()) or self.guard.get('duration',0),'seekable':bool(self.player.is_seekable()),'status':status,'speed':self.player.get_rate() or 1,'volume':max(0,self.player.audio_get_volume()) if self.player.audio_get_volume()>=0 else int(self.settings.get('volume',80)),'queue':self.queue,'cursor':self.cursor,'repeat':self.repeat,'shuffle':self.shuffle,'sleepAt':self.sleep_at,'abA':self.ab_a,'abB':self.ab_b,'guard':self.guard,'details':self.details() if self.current>0 else {},'bookmarks':self.bookmarks.get(str(self.current),[]),'error':self.fault}
    def protect(self,hidden):
        if self.software_video:self.surface.set_masked(hidden)
        self.player.audio_set_mute(bool(hidden))
        if not hidden:self.web.raise_()
    def save_progress(self):
        if self.current>0 and self.player.get_length()>0:
            self.db.execute('UPDATE media SET position=?,duration=?,lastplayed=? WHERE id=?',(max(0,self.player.get_time()),self.player.get_length(),int(time.time()*1000),self.current));self.db.commit()
    def import_files(self,paths):
        for file in paths:
            p=Path(file)
            if not p.is_file():continue
            kind='audio' if p.suffix.lower() in ('.mp3','.flac','.wav','.m4a','.opus','.ogg','.aac') else 'video'
            if p.suffix.lower() not in ('.mp3','.flac','.wav','.m4a','.opus','.ogg','.aac','.mp4','.mkv','.avi','.webm','.mov','.ts','.m4v'):continue
            self.db.execute('INSERT OR IGNORE INTO media(uri,title,kind,size,added) VALUES(?,?,?,?,?)',(str(p.resolve()),p.name,kind,p.stat().st_size,int(time.time()*1000)))
        self.db.commit();self.refresh()
    def open(self,id,queue=None,resume=True):
        self.save_progress();self.generation+=1;self.seek_target=None;self.schedule=None;self.guard={};self.player.stop();self.current=int(id)
        item=self.row()
        if not item:raise ValueError('This media is no longer available.')
        if queue is not None:self.queue=[int(x) for x in queue if self.row(x)]
        if self.current not in self.queue:self.queue.append(self.current)
        self.cursor=self.queue.index(self.current);self.pending_resume=item['position'] if resume and self.settings.get('resume',True) else 0;self.ended=False;self.fault='';self.ab_a=self.ab_b=-1
        if self.settings.get('sceneGuard') and item['kind']!='audio':
            self.protect(True);self.guard={'blocked':True,'phase':'hashing','progress':0};generation=self.generation
            def progress(state):self.worker_event.emit({'kind':'guard-progress','generation':generation,'state':state})
            def scan():
                try:self.worker_event.emit({'kind':'guard-ready','generation':generation,'map':self.scanner.scan(item['uri'],progress,lambda:generation!=self.generation)})
                except InterruptedError:pass
                except Exception as e:self.worker_event.emit({'kind':'guard-error','generation':generation,'error':str(e)})
            self.scans.submit(scan)
        else:self.begin()
        self.send('state',self.state())
    def begin(self):
        item=self.row();start=self.pending_resume
        if self.schedule:
            start=self.schedule.target(start) or start
            if start>=self.schedule.duration-80:raise ValueError('All remaining video is inside detected sections.')
        medium=self.vlc.media_new(item['uri'])
        if self.software_video or self.settings.get('decoder')=='software':medium.add_option(':avcodec-hw=none')
        medium.add_option(':network-caching='+str(self.settings.get('networkCache',1000)))
        if start>0:medium.add_option(':start-time='+str(start/1000))
        self.player.set_media(medium);medium.release();self.player.audio_set_volume(int(self.settings.get('volume',80)));self.player.set_rate(float(self.settings.get('speed',1)))
        if self.schedule:self.protect(True);self.seek_target=start;self.seek_at=time.monotonic()
        else:self.protect(False)
        self.player.play()
    def seek(self,target):
        if self.guard.get('blocked'):return
        target=max(0,min(int(target),max(0,self.player.get_length())))
        if self.schedule:
            target=self.schedule.target(target) or target
            if target>=self.schedule.duration-80:self.next(True);return
            self.protect(True);self.seek_target=target;self.seek_at=time.monotonic()
        self.player.set_time(target)
    def next(self,ended=False):
        import random
        if ended and self.repeat=='one':self.open(self.current,resume=False);return
        n=self.cursor+1
        if self.shuffle and len(self.queue)>1:n=random.choice([i for i in range(len(self.queue)) if i!=self.cursor])
        if n>=len(self.queue):
            if self.repeat=='all':n=0
            else:self.player.stop();return
        self.open(self.queue[n],resume=False)
    @Slot(object)
    def handle_worker_event(self,event):
        if event.get('generation') is not None and event['generation']!=self.generation:return
        try:
            if event['kind']=='guard-progress':self.guard.update(event['state'])
            elif event['kind']=='guard-ready':
                self.guard={**event['map'],'blocked':False,'phase':'ready','progress':100};self.schedule=Schedule(self.guard['intervals'],self.guard['duration']);self.begin()
            elif event['kind']=='guard-error':self.guard.update(blocked=True,error=event['error']);self.protect(True)
            else:self.send(event['kind'],event['data'])
        except Exception as e:self.guard.update(blocked=True,error=str(e));self.protect(True)
        self.send('state',self.state())
    @Slot(str)
    def request(self,raw):
        try:
            req=json.loads(raw);op=req['op'];a=req.get('args',{})
            if op=='ready':self.send('init',{'media':self.library(),'playlists':self.playlists(),'settings':self.settings,'native':True,'platform':sys.platform,'refractiveGlass':False,'state':self.state()});return
            if op=='settings':
                changed=bool(self.settings.get('sceneGuard'))!=bool(a.get('sceneGuard'));self.settings=a;self.settings_file.write_text(json.dumps(a))
                if changed and self.current>0:self.open(self.current)
            elif op=='open':self.open(a['id'],a.get('queue'))
            elif op=='guardScan':
                if self.current>0:self.open(self.current)
            elif op in ('toggle','play','pause'):
                if not self.guard.get('blocked'):
                    if op=='pause' or op=='toggle' and self.player.is_playing():self.player.set_pause(1)
                    elif self.schedule and self.seek_target is None and self.player.get_state() in (vlc.State.NothingSpecial,vlc.State.Stopped,vlc.State.Ended):
                        self.pending_resume=0;self.ended=False;self.begin()
                    else:
                        if self.schedule and self.seek_target is None:
                            target=self.schedule.target(max(0,self.player.get_time()))
                            if target is not None:self.seek(target)
                        self.player.play()
            elif op=='seek':self.seek(a['time'])
            elif op=='skip':self.seek(self.player.get_time()+a.get('seconds',10)*1000)
            elif op=='volume':self.settings['volume']=int(a['value']);self.player.audio_set_volume(int(a['value']))
            elif op=='speed':self.player.set_rate(float(a['value']))
            elif op=='next':self.next()
            elif op=='previous':self.seek(0) if self.player.get_time()>3000 or self.cursor<=0 else self.open(self.queue[self.cursor-1])
            elif op=='stop':self.generation+=1;self.save_progress();self.player.stop();self.current=-1;self.guard={};self.schedule=None;self.queue=[];self.cursor=-1
            elif op=='pick':self.import_files(QFileDialog.getOpenFileNames(self,'Open media')[0])
            elif op=='folder':
                folder=QFileDialog.getExistingDirectory(self,'Choose folder')
                if folder:self.import_files(list(Path(folder).rglob('*'))[:5000])
            elif op=='pickSubtitle':
                file=QFileDialog.getOpenFileName(self,'Open subtitles',filter='Subtitles (*.srt *.vtt *.ass *.ssa)')[0]
                if file:self.player.add_slave(vlc.MediaSlaveType.subtitle,QUrl.fromLocalFile(file).toString(),True)
            elif op=='favorite':self.db.execute('UPDATE media SET favorite=? WHERE id=?',(int(a['value']),a['id']));self.db.commit();self.refresh()
            elif op=='remove':
                if int(a['id'])==self.current:self.request(json.dumps({'op':'stop'}))
                self.db.execute('DELETE FROM media WHERE id=?',(a['id'],));self.db.commit();self.refresh()
            elif op=='network':
                from urllib.parse import urlparse
                if urlparse(a['url']).scheme not in ('http','https','rtsp','smb','rtmp','udp','rtp','ftp'):raise ValueError('Use a supported media URL.')
                self.db.execute('INSERT OR IGNORE INTO media(uri,title,kind,size,added) VALUES(?,?,?,?,?)',(a['url'],a.get('title') or a['url'],'stream',0,int(time.time()*1000)));self.db.commit();id=self.db.execute('SELECT id FROM media WHERE uri=?',(a['url'],)).fetchone()[0];self.refresh();self.open(id,[id])
            elif op=='repeat':self.repeat=a['value']
            elif op=='shuffle':self.shuffle=bool(a['value'])
            elif op=='sleep':self.sleep_at=int(time.time()*1000)+a['minutes']*60000 if a['minutes'] else 0
            elif op=='ab':
                if self.ab_a<0:self.ab_a=self.player.get_time()
                elif self.ab_b<0 and self.player.get_time()>self.ab_a+500:self.ab_b=self.player.get_time()
                else:self.ab_a=self.ab_b=-1
            elif op=='bookmark':self.bookmarks.setdefault(str(self.current),[]).append({'id':time.time_ns(),'media':self.current,'position':self.player.get_time(),'label':a.get('label','Bookmark')})
            elif op=='audioTrack':self.player.audio_set_track(int(a['id']))
            elif op=='subtitleTrack':self.player.video_set_spu(int(a['id']))
            elif op=='videoTrack':self.player.video_set_track(int(a['id']))
            elif op=='audioDelay':self.player.audio_set_delay(int(a['ms'])*1000)
            elif op=='subtitleDelay':self.player.video_set_spu_delay(int(a['ms'])*1000)
            elif op=='chapter':
                # Unknown chapter offsets must never bypass protected seeking.
                chapters=self.details().get('chapters',[]);index=int(a['index'])
                if self.schedule:
                    if not 0<=index<len(chapters):raise ValueError('This chapter has no verified timestamp.')
                    self.seek(chapters[index]['time'])
                else:self.player.set_chapter(index)
            elif op=='aspect':self.player.video_set_aspect_ratio(None if a['value']=='auto' else a['value'])
            elif op=='zoom':self.player.video_set_scale(0 if a['value']==1 else float(a['value']))
            elif op=='equalizer':
                equalizer=vlc.AudioEqualizer();equalizer.set_preamp(float(a.get('preamp',0)))
                for i,v in enumerate(a.get('bands',[])):equalizer.set_amp_at_index(float(v),i)
                self.player.set_equalizer(equalizer if a.get('enabled',True) else None);self.equalizer=equalizer
            elif op=='fullscreen':self.fullscreen()
            elif op=='rotate':self.resize(self.height(),self.width())
            elif op=='pip':self.setWindowFlag(Qt.WindowType.WindowStaysOnTopHint,not bool(self.windowFlags()&Qt.WindowType.WindowStaysOnTopHint));self.resize(500,320);self.show()
            elif op=='share':
                from PySide6.QtGui import QDesktopServices
                if self.row():QDesktopServices.openUrl(QUrl.fromLocalFile(str(Path(self.row()['uri']).parent)))
            elif op=='createPlaylist':self.db.execute('INSERT INTO playlists(name) VALUES(?)',(a['name'][:80],));self.db.commit();self.refresh()
            elif op=='playlistDelete':self.db.execute('DELETE FROM playlists WHERE id=?',(a['id'],));self.db.commit();self.refresh()
            elif op in ('playlistAdd','playlistRemove'):
                row=self.db.execute('SELECT entries FROM playlists WHERE id=?',(a['playlist'],)).fetchone();ids=[x for x in json.loads(row[0]) if x!=a['id']]
                if op=='playlistAdd':ids.append(a['id'])
                self.db.execute('UPDATE playlists SET entries=? WHERE id=?',(json.dumps(ids),a['playlist']));self.db.commit();self.refresh()
            elif op=='queueRemove':
                index=int(a['index']);removed=self.queue.pop(index)
                if removed==self.current:self.open(self.queue[min(index,len(self.queue)-1)]) if self.queue else self.request(json.dumps({'op':'stop'}))
                self.cursor=self.queue.index(self.current) if self.current in self.queue else -1
            elif op=='queueMove':self.queue.insert(int(a['to']),self.queue.pop(int(a['from'])));self.cursor=self.queue.index(self.current)
            elif op=='clearHistory':self.db.execute('UPDATE media SET position=0,lastplayed=0');self.db.commit();self.refresh()
            elif op=='refresh':self.refresh()
            elif op=='analysisCredentials':
                if a.get('key'):
                    os.environ['GEMINI_API_KEY']=a['key'];self.searches.submit(self.sentry.credentials_changed)
                    try:
                        import keyring
                        keyring.set_password('VLCGlass','gemini',a['key'])
                    except Exception:self.send('notice',{'message':'The key is available for this session. Set GEMINI_API_KEY to reuse it when secure storage is unavailable.'})
            elif op in ('sceneIndex','sceneSearch'):self.analysis_request(op,a)
            elif op=='glassRects':return  # Android caption/refraction geometry has no desktop side effect.
            elif op=='scanCast':raise ValueError('Receiver discovery is not available in this desktop build.')
            elif op=='seekPreview':self.send('seekPreview',{'id':a['id'],'token':a['token'],'time':a.get('time',0)})
            elif op=='exit':self.close()
            self.send('state',self.state())
        except Exception as e:self.send('error',{'message':str(e)})
    def analysis_request(self,op,args):
        item=self.row()
        if not item:raise ValueError('Open a local video first.')
        if op=='sceneIndex' and not args.get('allowUpload'):raise ValueError('Allow indexing this selected video first.')
        media=self.current;generation=self.generation;backend=self.settings.get('searchBackend','gemini')
        def emit(type,data):self.worker_event.emit({'kind':type,'generation':generation,'data':{'media':media,**data}})
        def run():
            try:
                if op=='sceneIndex':emit('sceneIndex',self.sentry.index(media,item['uri'],backend,lambda p:emit('sceneIndex',p)))
                else:emit('sceneSearch',{**self.sentry.search(media,args.get('query',''),item['uri'],backend),'request':args.get('request')})
            except Exception as e:emit(op,{'error':str(e),'request':args.get('request')})
        self.searches.submit(run)
    def tick(self):
        try:
            now=self.player.get_time()
            if self.seek_target is not None:
                if time.monotonic()-self.seek_at>.08 and self.seek_target<=now<self.seek_target+3000:self.seek_target=None;self.protect(False)
                elif time.monotonic()-self.seek_at>10:self.player.set_pause(1);self.guard.update(blocked=True,error='The decoder did not reach the requested position.');self.seek_target=None
            elif self.schedule and self.player.is_playing():
                target=self.schedule.target(now,self.player.get_rate())
                if target is not None:self.seek(target)
            if self.ab_b>self.ab_a>=0 and now>=self.ab_b:self.seek(self.ab_a)
            if self.sleep_at and time.time()*1000>=self.sleep_at:self.player.set_pause(1);self.sleep_at=0
            if self.player.get_state()==vlc.State.Ended and not self.ended:self.ended=True;self.next(True)
            if time.monotonic()-self.last_ui>.4:self.send('state',self.state());self.last_ui=time.monotonic()
            if time.monotonic()-self.last_save>5:self.save_progress();self.last_save=time.monotonic()
        except Exception as e:
            if self.schedule:self.player.set_pause(1);self.protect(True);self.guard.update(blocked=True,error=str(e))
    def fullscreen(self):self.showNormal() if self.isFullScreen() else self.showFullScreen()
    def closeEvent(self,event):
        self.generation+=1;self.clock.stop();self.save_progress();self.player.stop();self.player.release();self.vlc.release();self.scans.shutdown(wait=False,cancel_futures=True);self.searches.shutdown(wait=False,cancel_futures=True);event.accept()

def main():
    app=QApplication(sys.argv);app.setOrganizationName('VLCGlass');app.setApplicationName('VLC Glass')
    try:window=Player();window.show();sys.exit(app.exec())
    except Exception as e:QMessageBox.critical(None,'VLC Glass could not start',str(e));raise

if __name__=='__main__':
    if '--ui-smoke-report' in sys.argv:
        from desktop.ui_smoke import run
        sys.exit(run(Player,QApplication,QTimer,sys.argv))
    elif '--smoke-report' in sys.argv:
        from intelligence.vision import FrameDetector,sha256_file
        import numpy as np
        model=ROOT/'assets/models/320n.onnx';detector=FrameDetector(model)
        instance=vlc.Instance('--no-plugins-cache','--no-audio','--no-video');player=instance.media_player_new();player.release();instance.release()
        report={'platform':sys.platform,'vlc':vlc.libvlc_get_version().decode(),'model_sha256':sha256_file(model),'synthetic_frame_score':detector.score(np.zeros((180,320,3),np.uint8)),'physical_playback_tested':False}
        Path(sys.argv[sys.argv.index('--smoke-report')+1]).write_text(json.dumps(report,indent=2))
    else:main()
