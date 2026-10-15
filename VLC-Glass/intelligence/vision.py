"""On-device ONNX frame detector and cached, precomputed skip maps."""
import hashlib
import json
import os
from pathlib import Path
import threading
import time
import subprocess
import re
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
        from PIL import Image
        np=self.np
        h,w=frame.shape[:2];side=max(h,w)
        # Match NudeNet preprocessing: top-left square, black right/bottom pad,
        # BGR, NCHW, normalized to [0,1], matching the pinned reference. No image paths leave this process.
        square=np.zeros((side,side,3),dtype=np.uint8);square[:h,:w]=frame
        small=np.asarray(Image.fromarray(square).resize((320,320),Image.Resampling.BILINEAR))
        data=np.transpose(small,(2,0,1))[None].astype(np.float32)/255
        with self.lock:out=self.session.run(None,{self.name:data})[0]
        if out.ndim!=3 or out.shape[1]!=22:raise ValueError('Unexpected detector tensor')
        return float(np.max(out[0,4+np.array(EXPLICIT),:]))

def _ffmpeg():
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()

def _duration(path):
    p=subprocess.run([_ffmpeg(),'-hide_banner','-i',str(path)],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,timeout=30)
    m=re.search(r'Duration: (\d+):(\d+):(\d+(?:\.\d+)?)',p.stderr.decode(errors='replace'))
    if not m:raise ValueError('The video duration could not be read. Use a local seekable video.')
    h,minute,seconds=map(float,m.groups());return round((h*3600+minute*60+seconds)*1000)

def _frames(path,fps,start=0,length=None):
    import numpy as np
    command=[_ffmpeg(),'-hide_banner','-nostdin','-loglevel','error']
    if start:command+=['-ss',str(start/1000)]
    command+=['-i',str(path)]
    if length is not None:command+=['-t',str(length/1000)]
    command+=['-an','-vf',f'fps={fps}:start_time=0:round=up,scale=320:320:force_original_aspect_ratio=decrease:flags=bilinear,pad=320:320:0:0:black','-f','rawvideo','-pix_fmt','bgr24','-']
    process=subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    index=0
    try:
        while True:
            data=process.stdout.read(320*320*3)
            if not data:break
            if len(data)!=320*320*3:raise ValueError('The decoder returned an incomplete frame.')
            yield start+round(index*1000/fps),np.frombuffer(data,dtype=np.uint8).reshape(320,320,3)
            index+=1
        if process.wait(timeout=10)!=0:raise ValueError('Video decoding failed. Protection is not ready.')
    finally:
        if process.poll() is None:process.kill()
        process.communicate(timeout=10)

class Scanner:
    def __init__(self,model,cache):
        self.model=Path(model);self.cache=Path(cache);self.cache.mkdir(parents=True,exist_ok=True)
        self.detector=None;self.decision_agent=None;self.lock=threading.Lock()
    def scan(self,path,progress=lambda p:None,cancel=lambda:False):
        identity=sha256_file(path,cancel);model_hash=sha256_file(self.model)
        key=hashlib.sha256((identity+POLICY+model_hash).encode()).hexdigest()
        cached=self.cache/(key+'.json')
        if cached.exists():return json.loads(cached.read_text())
        with self.lock:
            if self.detector is None:self.detector=FrameDetector(self.model)
        duration=_duration(path)
        if duration<=0:raise ValueError('The video has no finite duration.')
        samples=[];started=time.perf_counter();last_report=-1
        for t,frame in _frames(path,2):
            if t>=duration:break
            if cancel():raise InterruptedError('Cancelled')
            samples.append((t,self.detector.score(frame)))
            percent=int(min(99,t/duration*100))
            if percent!=last_report:progress({'phase':'scanning','progress':percent,'at':t,'duration':duration});last_report=percent
        spans=from_samples(samples,duration)
        refined=[]
        for span in spans:
            lo=max(0,span.start-500);hi=min(duration,span.end+500);first=None;last=None
            for a,b in [(lo,min(hi,lo+2000)),(max(lo,hi-2000),hi)]:
                for t,frame in _frames(path,10,a,b-a):
                    if t>=b:break
                    if cancel():raise InterruptedError('Cancelled')
                    if self.detector.score(frame)>=.35:
                        first=t if first is None else min(first,t);last=t if last is None else max(last,t)
            refined.append({'start':min(span.start,max(0,(first if first is not None else span.start)-750)),
                            'end':max(span.end,min(duration,(last if last is not None else span.end)+600)),'score':span.score})
        from .timeline import normalize
        result={'schema':1,'policy':POLICY,'media_sha256':identity,'model_sha256':model_hash,
                'duration':duration,'complete':True,'sample_ms':500,'boundary_ms':100,
                'intervals':[s.__dict__ for s in normalize(refined,duration)],
                'scan_seconds':round(time.perf_counter()-started,3)}
        laya_path=os.environ.get('LAYA_MODEL_DIR')
        if laya_path:
            import sys
            sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'upstream/laya'))
            from .laya_video import VideoDecisionAgent
            if self.decision_agent is None:self.decision_agent=VideoDecisionAgent(laya_path,os.environ.get('LAYA_DEVICE','cpu'))
            result['laya_advisory']=[self.decision_agent.decide({'observations':'Visual detector observations require review.','detector_labels':[LABELS[i] for i in EXPLICIT]}) for span in result['intervals']]
        tmp=cached.with_suffix('.tmp');tmp.write_text(json.dumps(result));os.replace(tmp,cached)
        progress({'phase':'ready','progress':100,'map':result});return result
