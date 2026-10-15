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
    window=Player();window.show();started=time.monotonic();last_check=0
    required=['ui_ready','native_playback','video_output','scan_complete','synthetic_skip','protected_previous','protected_replay','visible_overlay','visible_video','responsive_prompt']
    evidence={k:False for k in required};evidence['physical_device']=False
    phase='ui';previous=0;closed=False
    def finish(error=None):
        nonlocal closed
        if closed:return
        closed=True;timer.stop()
        if error:evidence['error']=str(error)
        evidence['passed']=not error and all(evidence[k] for k in required)
        if sys.stdout is not None:print(json.dumps(evidence),flush=True)
        report.write_text(json.dumps(evidence,indent=2));window.close();app.exit(0 if evidence['passed'] else 1)
    def ready(value):
        nonlocal phase
        if not value or phase!='ui':return
        evidence['ui_ready']=True;phase='scan'
        window.settings['sceneGuard']=True;window.settings['resume']=False;window.import_files([str(video)])
        id=window.db.execute('SELECT id FROM media WHERE uri=?',(str(video),)).fetchone()[0]
        window.web.page().runJavaScript('model.settings.sceneGuard=true;playMedia('+str(id)+',['+str(id)+'])')
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
    def snapshot_desktop():
        nonlocal phase
        snapshot('ui-smoke.png');phase='phone';window.resize(360,760)
        QTimer.singleShot(1000,check_phone)
    def check_phone():
        window.web.page().runJavaScript("(()=>{showHUD();const r=document.getElementById('momentDock').getBoundingClientRect();return r.width>0&&r.left>=0&&r.right<=innerWidth+1&&r.top>=0&&r.bottom<=innerHeight+1&&document.documentElement.scrollWidth<=innerWidth+1;})()",phone_ready)
    def phone_ready(value):
        evidence['responsive_prompt']=bool(value);QTimer.singleShot(700,lambda:finish_phone(value))
    def finish_phone(value):
        snapshot('ui-phone.png');finish(None if value else 'The 360 px prompt layout overflowed')
    timer=QTimer();timer.timeout.connect(check);timer.start(50)
    return app.exec()
