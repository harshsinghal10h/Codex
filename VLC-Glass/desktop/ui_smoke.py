"""Exercise the packaged Qt page, native playback, scanning and skip scheduler.

Only safe colour bars are used. A synthetic skip interval tests scheduling;
this is not an evaluation of nudity-recognition accuracy.
"""
import json
from pathlib import Path
import time

def run(Player,QApplication,QTimer,argv):
    from intelligence.timeline import Schedule
    from PySide6.QtCore import QStandardPaths
    report=Path(argv[argv.index('--ui-smoke-report')+1]).resolve()
    video=Path(argv[argv.index('--smoke-video')+1]).resolve()
    QStandardPaths.setTestModeEnabled(True)
    app=QApplication(argv);app.setOrganizationName('VLCGlassTests');app.setApplicationName('VLC Glass tests')
    window=Player();window.show();started=time.monotonic();last_check=0
    evidence={'ui_ready':False,'native_playback':False,'scan_complete':False,'synthetic_skip':False,'responsive_prompt':False,'physical_device':False}
    phase='ui';previous=0;closed=False
    def finish(error=None):
        nonlocal closed
        if closed:return
        closed=True;timer.stop()
        if error:evidence['error']=str(error)
        evidence['passed']=not error and all(evidence[k] for k in ['ui_ready','native_playback','scan_complete','synthetic_skip','responsive_prompt'])
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
        if time.monotonic()-started>60:finish('Timed out during '+phase);return
        try:
            if phase=='ui':
                if time.monotonic()-last_check>.5:
                    last_check=time.monotonic();window.web.page().runJavaScript("!!window.GlassApp&&!!document.getElementById('momentQuery')&&document.body.classList.contains('native')",ready)
            elif phase=='scan':
                if window.guard.get('error'):finish(window.guard['error']);return
                if window.guard.get('complete') and window.player.is_playing() and window.player.get_time()>300:
                    evidence['scan_complete']=True;evidence['native_playback']=True
                    duration=window.player.get_length()
                    window.schedule=Schedule([{'start':1400,'end':3500}],duration)
                    window.guard['intervals']=[{'start':1400,'end':3500}];phase='skip';previous=window.player.get_time()
            elif phase=='skip':
                position=window.player.get_time()
                if previous<1800 and position>=3400:evidence['synthetic_skip']=True
                if evidence['synthetic_skip'] and window.seek_target is None and window.player.is_playing():
                    evidence['clear_position_ms']=position;window.player.set_pause(1)
                    window.web.page().runJavaScript('showHUD()')
                    window.screen().grabWindow(int(window.winId())).save(str(report.with_name('ui-smoke.png')))
                    phase='phone';window.resize(360,760)
                    QTimer.singleShot(600,check_phone)
                previous=position
        except Exception as e:finish(e)
    def check_phone():
        window.web.page().runJavaScript("(()=>{showHUD();const r=document.getElementById('momentDock').getBoundingClientRect();return r.width>0&&r.left>=0&&r.right<=innerWidth+1&&document.documentElement.scrollWidth<=innerWidth+1;})()",phone_ready)
    def phone_ready(value):
        evidence['responsive_prompt']=bool(value)
        window.screen().grabWindow(int(window.winId())).save(str(report.with_name('ui-phone.png')))
        finish(None if value else 'The 360 px prompt layout overflowed')
    timer=QTimer();timer.timeout.connect(check);timer.start(50)
    return app.exec()
