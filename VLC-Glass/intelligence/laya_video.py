"""Bounded LAYA adapter for structured scene evidence, never raw video.

This adapter does not claim to fine-tune a checkpoint. Domain accuracy requires
labelled video decisions and a separate held-out evaluation.
"""
from collections import OrderedDict
import hashlib
import json
import threading

QUESTIONS={
 'explicit_evidence': {'type':'noul','instructions':'Do the supplied observations explicitly describe visible nudity or sexual activity? Do not infer this from clothing, identity, or a title.'},
 'needs_visual_review': {'type':'noul','instructions':'Is the visual evidence missing, contradictory, or insufficient to determine the scene content?'},
}

class VideoDecisionAgent:
    def __init__(self,path,device='cpu',cache_size=128):
        from pathlib import Path
        if not Path(path).is_dir():raise ValueError("LAYA_MODEL_DIR must point to a local checkpoint directory")
        import torch
        import laya
        torch.set_num_threads(2)
        # A local path avoids network/model reloads and keeps one checkpoint hot.
        self.torch=torch;self.agent=laya.load(str(path),device=device)
        self.cache=OrderedDict();self.limit=cache_size;self.lock=threading.Lock()
        self.agent.predict({'observations':'No observations supplied.'},QUESTIONS)
    def decide(self,evidence):
        state={'observations':str(evidence.get('observations',''))[:3000],
               'detector_labels':evidence.get('detector_labels',[])[:16]}
        key=hashlib.sha256(json.dumps(state,sort_keys=True).encode()).hexdigest()
        with self.lock:
            if key in self.cache:
                self.cache.move_to_end(key);return self.cache[key]
            with self.torch.inference_mode():answer=self.agent.predict(state,QUESTIONS)
            # Results are advisory. LAYA cannot override positive visual evidence
            # or declare an unscanned part of the timeline clear.
            answer['advisory_only']=True
            self.cache[key]=answer
            while len(self.cache)>self.limit:self.cache.popitem(last=False)
            return answer
