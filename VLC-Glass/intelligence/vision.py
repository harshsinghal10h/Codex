"""On-device ONNX frame detector and cached, precomputed skip maps."""
import hashlib
import json
import os
from pathlib import Path
import threading
import time
from .timeline import POLICY,from_samples

EXPLICIT=(2,3,4,6,14)
LABELS=('FEMALE_GENITALIA_COVERED','FACE_FEMALE','BUTTOCKS_EXPOSED',
 'FEMALE_BREAST_EXPOSED','FEMALE_GENITALIA_EXPOSED','MALE_BREAST_EXPOSED',
 'ANUS_EXPOSED','FEET_EXPOSED','BELLY_COVERED','FEET_COVERED','ARMPITS_COVERED',
 'ARMPITS_EXPOSED','FACE_MALE','BELLY_EXPOSED','MALE_GENITALIA_EXPOSED',
 'ANUS_COVERED','FEMALE_BREAST_COVERED','BUTTOCKS_COVERED')

def sha256_file(path,cancel=lambda:False):
    h=hashlib.sha256()
    with open(path,'rb') as f:
        while data:=f.read(1024*1024):
            if cancel():raise InterruptedError('Cancelled')
            h.update(data)
    return h.hexdigest()

class FrameDetector:
    def __init__(self,model):
        import onnxruntime as ort
        import numpy as np
        self.np=np
        options=ort.SessionOptions();options.intra_op_num_threads=2;options.inter_op_num_threads=1
        options.graph_optimization_level=ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        self.session=ort.InferenceSession(str(model),options,providers=['CPUExecutionProvider'])
        self.name=self.session.get_inputs()[0].name
        self.lock=threading.Lock()
        self.session.run(None,{self.name:np.zeros((1,3,320,320),dtype=np.float32)})
    def score(self,frame):
        import cv2
        np=self.np
        h,w=frame.shape[:2];side=max(h,w)
        # Match NudeNet preprocessing: top-left square, black right/bottom pad,
        # BGR, NCHW, normalized to [0,1], matching the pinned reference. No image paths leave this process.
        square=np.zeros((side,side,3),dtype=np.uint8);square[:h,:w]=frame
        small=cv2.resize(square,(320,320),interpolation=cv2.INTER_LINEAR)
        data=np.transpose(small,(2,0,1))[None].astype(np.float32)/255
        with self.lock:out=self.session.run(None,{self.name:data})[0]
        if out.ndim!=3 or out.shape[1]!=22:raise ValueError('Unexpected detector tensor')
        return float(np.max(out[0,4+np.array(EXPLICIT),:]))

class Scanner:
    def __init__(self,model,cache):
        self.model=Path(model);self.cache=Path(cache);self.cache.mkdir(parents=True,exist_ok=True)
        self.detector=None;self.lock=threading.Lock()
    def scan(self,path,progress=lambda p:None,cancel=lambda:False):
        import cv2
        identity=sha256_file(path,cancel)
        model_hash=sha256_file(self.model)
        key=hashlib.sha256((identity+POLICY+model_hash).encode()).hexdigest()
        cached=self.cache/(key+'.json')
        if cached.exists():return json.loads(cached.read_text())
        with self.lock:
            if self.detector is None:self.detector=FrameDetector(self.model)
        cap=cv2.VideoCapture(str(path))
        try:
            fps=cap.get(cv2.CAP_PROP_FPS);count=cap.get(cv2.CAP_PROP_FRAME_COUNT)
            if not cap.isOpened() or fps<=0 or count<=0:raise ValueError('This source cannot be scanned. Use a local seekable video.')
            duration=int(count/fps*1000);samples=[];start=time.perf_counter();last_report=-1
            for t in range(0,duration,500):
                if cancel():raise InterruptedError('Cancelled')
                cap.set(cv2.CAP_PROP_POS_MSEC,t);ok,frame=cap.read()
                if not ok:raise ValueError('Frame decoding failed at '+str(t)+' ms; protection is not ready.')
                score=self.detector.score(frame);samples.append((t,score))
                percent=int(min(99,t/duration*100))
                if percent!=last_report:progress({'phase':'scanning','progress':percent,'at':t,'duration':duration});last_report=percent
            spans=from_samples(samples,duration)
            # Refine detected transitions at 100 ms. Coarse sampling can still
            # miss brief events; the padding is deliberately retained.
            refined=[]
            for span in spans:
                lo=max(0,span.start-500);hi=min(duration,span.end+500)
                first=None;last=None
                for a,b in [(lo,min(hi,lo+2000)),(max(lo,hi-2000),hi)]:
                    for t in range(a,b,100):
                        if cancel():raise InterruptedError('Cancelled')
                        cap.set(cv2.CAP_PROP_POS_MSEC,t);ok,frame=cap.read()
                        if not ok:raise ValueError('Boundary frame decoding failed')
                        if self.detector.score(frame)>=.35:
                            first=t if first is None else min(first,t);last=t if last is None else max(last,t)
                refined.append({'start':min(span.start,max(0,(first or span.start)-750)),
                                'end':max(span.end,min(duration,(last or span.end)+600)), 'score':span.score})
            from .timeline import normalize
            result={'schema':1,'policy':POLICY,'media_sha256':identity,'model_sha256':model_hash,
                    'duration':duration,'complete':True,'sample_ms':500,'boundary_ms':100,
                    'intervals':[s.__dict__ for s in normalize(refined,duration)],
                    'scan_seconds':round(time.perf_counter()-start,3)}
            tmp=cached.with_suffix('.tmp');tmp.write_text(json.dumps(result));os.replace(tmp,cached)
            progress({'phase':'ready','progress':100,'map':result});return result
        finally:cap.release()
