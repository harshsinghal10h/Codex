"""Exercise the real Qt window, native playback and guarded navigation.

Safe colour bars and synthetic intervals test playback, never model accuracy.
The screen probe checks actual composition above the native video window.
"""
import json
import sys
from pathlib import Path
import time

def run(Player,QApplication,QTimer,argv):
    from intelligence.timeline import Schedule
    from PySide6.QtCore import QStandardPaths,QPoint
    report=Path(argv[argv.index('--ui-smoke-report')+1]).resolve()
    video=Path(argv[argv.index('--smoke-video')+1]).resolve()
    QStandardPaths.setTestModeEnabled(True)
    app=QApplication(argv);app.setOrganizationName('VLCGlassTests');app.setApplicationName('VLC Glass tests')
    import faulthandler
    diagnostic=report.with_name('ui-thread-dump.log').open('w')
    faulthandler.dump_traceback_later(110,file=diagnostic,exit=True)
    window=Player();window.show();started=time.monotonic();last_check=0
    required=['ui_ready','native_playback','video_output','scan_complete','synthetic_skip','protected_previous','protected_replay','visible_overlay','visible_video','responsive_prompt','fullscreen_control','responsive_layouts','direct_controls','touch_targets','controls_fit','steady_dom','semantic_result_seek','play_pause_control','seek_buttons','speed_control','track_queue_access','volume_control','dialog_focus','keyboard_button','lock_controls','queue_navigation']
    evidence={k:False for k in required};evidence['physical_device']=False
    phase='ui';previous=0;closed=False
    def finish(error=None):
        nonlocal closed
        if closed:return
        if error:evidence['error']=str(error)
        evidence['passed']=not error and all(evidence[k] for k in required)
        if sys.stdout is not None:print(json.dumps(evidence),flush=True)
        report.write_text(json.dumps(evidence,indent=2));closed=True;timer.stop();window.close();faulthandler.cancel_dump_traceback_later();app.exit(0 if evidence['passed'] else 1)
    def ready(value):
        nonlocal phase
        if not value or phase!='ui':return
        evidence['ui_ready']=True;phase='scan'
        window.settings['sceneGuard']=True;window.settings['resume']=False
        import shutil
        second=video.with_name('safe-second.mp4');shutil.copyfile(video,second);window.import_files([str(video),str(second)])
        id=window.db.execute('SELECT id FROM media WHERE uri=?',(str(video),)).fetchone()[0]
        next_id=window.db.execute('SELECT id FROM media WHERE uri=?',(str(second),)).fetchone()[0]
        window.web.page().runJavaScript('model.settings.sceneGuard=true;playMedia('+str(id)+','+json.dumps([id,next_id])+')')
    def check():
        nonlocal phase,previous,last_check
        if closed:return
        if time.monotonic()-started>90:snapshot('ui-error.png');finish('Timed out during '+phase);return
        try:
            if phase=='ui':
                if time.monotonic()-last_check>.5:
                    last_check=time.monotonic();window.web.page().runJavaScript("!!window.GlassApp&&!!document.getElementById('momentQuery')&&document.body.classList.contains('native')",ready)
            elif phase=='scan':
                if window.guard.get('error'):finish(window.guard['error']);return
                if window.guard.get('complete') and window.player.is_playing() and window.player.get_time()>300:
                    evidence['scan_complete']=True;evidence['native_playback']=True
                    evidence['video_output']=window.surface.paint_count>0 if window.software_video else window.player.has_vout()>0
                    if not evidence['video_output']:return
                    window.schedule=Schedule([{'start':1400,'end':3500}],window.player.get_length())
                    window.guard['intervals']=[{'start':1400,'end':3500}];phase='skip';previous=window.player.get_time()
            elif phase=='skip':
                position=window.player.get_time()
                if previous<1800 and position>=3400:evidence['synthetic_skip']=True
                if evidence['synthetic_skip'] and window.seek_target is None and window.player.is_playing():
                    evidence['clear_position_ms']=position;window.player.set_pause(1)
                    window.schedule=Schedule([{'start':0,'end':2000}],window.player.get_length())
                    window.guard['intervals']=[{'start':0,'end':2000}]
                    window.request(json.dumps({'op':'previous'}))
                    if window.seek_target!=2000:raise RuntimeError('Previous did not protect the opening section')
                    phase='previous'
                previous=position
            elif phase=='previous' and window.seek_target is None:
                if window.player.get_time()<2000:raise RuntimeError('Previous exposed the opening section')
                evidence['protected_previous']=True;window.player.stop();window.request(json.dumps({'op':'play'}))
                if window.seek_target!=2000:raise RuntimeError('Replay did not mask before starting')
                phase='replay'
            elif phase=='replay' and window.seek_target is None and window.player.is_playing() and (not window.software_video or window.surface.frame is not None):
                if window.player.get_time()<2000:raise RuntimeError('Replay exposed the opening section')
                evidence['protected_replay']=True;window.player.set_pause(1)
                window.web.page().runJavaScript("showHUD();(()=>{const p=document.createElement('div');p.id='qa-composition-probe';p.style.cssText='position:fixed;left:6px;top:6px;width:24px;height:24px;background:#12ab34;z-index:2147483647;pointer-events:none';document.body.appendChild(p)})()")
                phase='composition';QTimer.singleShot(1000,check_composition)
        except Exception as e:finish(e)
    def screen():
        picture=window.screen().grabWindow(0)
        if picture.isNull():picture=window.screen().grabWindow(int(window.winId()))
        return picture
    def snapshot(name):screen().save(str(report.with_name(name)))
    def check_composition():
        nonlocal phase
        picture=screen();image=picture.toImage();ratio=picture.devicePixelRatio();origin=window.screen().geometry().topLeft()
        def pixel(x,y):
            p=window.web.mapToGlobal(QPoint(x,y))-origin
            return image.pixelColor(round(p.x()*ratio),round(p.y()*ratio))
        colour=pixel(18,18);evidence['probe_rgb']=[colour.red(),colour.green(),colour.blue()]
        evidence['visible_overlay']=all(abs(a-b)<14 for a,b in zip(evidence['probe_rgb'],[18,171,52]))
        coloured=0
        for x in (.15,.3,.45,.6,.75,.85):
            for y in (.3,.4,.5,.6):
                c=pixel(int(window.web.width()*x),int(window.web.height()*y));rgb=[c.red(),c.green(),c.blue()]
                if max(rgb)>70 and max(rgb)-min(rgb)>60:coloured+=1
        evidence['visible_video']=coloured>=4;evidence['video_colour_samples']=coloured
        if not evidence['visible_overlay'] or not evidence['visible_video']:
            snapshot('ui-error.png');finish('The actual screen did not show both web controls and video');return
        window.web.page().runJavaScript("document.getElementById('qa-composition-probe').remove();showHUD()")
        phase='snapshot';QTimer.singleShot(500,snapshot_desktop)
    fullscreen_started=0;fullscreen_stable=0;fullscreen_geometry=None
    def snapshot_desktop():
        nonlocal phase,fullscreen_started,fullscreen_stable,fullscreen_geometry
        snapshot('ui-smoke.png');phase='fullscreen-enter'
        fullscreen_started=time.monotonic();fullscreen_stable=0;fullscreen_geometry=None
        window.web.page().runJavaScript("document.getElementById('screenButton').click()")
        QTimer.singleShot(100,fullscreen_ready)
    def fullscreen_ready():
        nonlocal phase,fullscreen_started,fullscreen_stable,fullscreen_geometry
        entering=phase=='fullscreen-enter';now=time.monotonic()
        geometry=(window.web.width(),window.web.height());screen_size=window.screen().geometry().size()
        matched=window.isFullScreen()==entering and (not entering or geometry==(screen_size.width(),screen_size.height()))
        if matched and geometry==fullscreen_geometry:
            if not fullscreen_stable:fullscreen_stable=now
        else:fullscreen_stable=0
        fullscreen_geometry=geometry
        # Cocoa performs a native Space transition asynchronously. Observe the
        # actual state and settled geometry before issuing the reverse action.
        if matched and fullscreen_stable and now-fullscreen_stable>=.8:
            evidence.setdefault('fullscreen_transitions',[]).append({'entered':entering,'elapsed_ms':round((now-fullscreen_started)*1000),'width':geometry[0],'height':geometry[1]})
            if entering:
                phase='fullscreen-exit';fullscreen_started=now;fullscreen_stable=0;fullscreen_geometry=None
                window.web.page().runJavaScript("document.getElementById('screenButton').click()")
                QTimer.singleShot(100,fullscreen_ready)
            else:
                evidence['fullscreen_control']=True;phase='layouts';next_layout()
            return
        if now-fullscreen_started>10:
            snapshot('ui-fullscreen-error.png');finish('Fullscreen did not settle during '+phase+'; state='+str(window.isFullScreen())+' geometry='+str(geometry));return
        QTimer.singleShot(100,fullscreen_ready)
    layouts=iter([(320,640,False),(684,360,False),(360,700,True),(684,360,True),(800,600,False)])
    layout_results=[];layout_target=None;layout_started=0
    def next_layout():
        nonlocal layout_target,layout_started
        try:w,h,legacy=next(layouts)
        except StopIteration:
            evidence['responsive_layouts']=all(r.get('passed') for r in layout_results)
            evidence['layout_checks']=layout_results
            window.web.page().runJavaScript("document.documentElement.classList.remove('legacy-ui')")
            resize_for_check(360,700,check_phone);return
        window.web.page().runJavaScript("document.documentElement.classList.toggle('legacy-ui',"+str(legacy).lower()+")")
        resize_for_check(w,h,check_layout)
    def resize_for_check(w,h,callback):
        nonlocal layout_target,layout_started
        layout_target=(w,h,callback);layout_started=time.monotonic();window.resize(w,h)
        QTimer.singleShot(100,resize_ready)
    def resize_ready():
        w,h,callback=layout_target
        if window.web.width()==w and window.web.height()==h:
            QTimer.singleShot(150,callback);return
        if time.monotonic()-layout_started>6:
            finish('Resize did not reach '+str((w,h))+'; got '+str((window.web.width(),window.web.height())));return
        window.resize(w,h);QTimer.singleShot(100,resize_ready)
    def check_layout():
        window.web.page().runJavaScript("(()=>{showHUD();const nodes=[document.getElementById('momentDock'),...document.querySelectorAll('#playerHUD button,#playerHUD input')].filter(e=>e.offsetWidth&&e.offsetHeight&&!e.closest('.hidden'));return JSON.stringify({width:innerWidth,height:innerHeight,legacy:document.documentElement.classList.contains('legacy-ui'),passed:nodes.every(e=>{const r=e.getBoundingClientRect();return r.left>=-1&&r.top>=-1&&r.right<=innerWidth+1&&r.bottom<=innerHeight+1})});})()",layout_done)
    def layout_done(value):
        try:
            item=json.loads(value);item['expected_width'],item['expected_height']=layout_target[:2]
            item['passed']=item['passed'] and item['width']==item['expected_width'] and item['height']==item['expected_height'];layout_results.append(item)
        except Exception as error:finish(error);return
        next_layout()
    def check_phone():
        window.web.page().runJavaScript("(()=>{showHUD();const r=document.getElementById('momentDock').getBoundingClientRect();return r.width>0&&r.left>=0&&r.right<=innerWidth+1&&r.top>=0&&r.bottom<=innerHeight+1&&document.documentElement.scrollWidth<=innerWidth+1;})()",phone_ready)
    def phone_ready(value):
        evidence['responsive_prompt']=bool(value);QTimer.singleShot(700,lambda:finish_phone(value))
    def finish_phone(value):
        snapshot('ui-phone.png')
        if not value:finish('The 360 px prompt layout overflowed');return
        source=(Path(__file__).resolve().parents[1]/'assets/ui/qa-contract.js').read_text()
        window.web.page().runJavaScript(source)
        QTimer.singleShot(700,read_contract)
    def read_contract():
        window.web.page().runJavaScript('JSON.stringify(window.__qaContract||null)',contract_done)
    def contract_done(value):
        try:
            result=json.loads(value or 'null')
            if not result:QTimer.singleShot(100,read_contract);return
            evidence.update(result)
            finish(None if all(result.get(k) for k in ('direct_controls','touch_targets','controls_fit','steady_dom','semantic_result_seek','play_pause_control','seek_buttons','speed_control','track_queue_access','volume_control','dialog_focus','keyboard_button','lock_controls','queue_navigation')) else 'UI/search contract failed: '+str(result))
        except Exception as error:finish(error)
    timer=QTimer();timer.timeout.connect(check);timer.start(50)
    return app.exec()
