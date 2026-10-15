"""Deterministic content-skip scheduling. Scores are model evidence, not guarantees."""
from bisect import bisect_right
from dataclasses import dataclass
from math import isfinite

POLICY = 'nudenet-320n-v1/500ms/100ms/0.60-0.35/pad750-500'

@dataclass(frozen=True)
class Span:
    start: int
    end: int
    score: float = 1.0

def normalize(spans, duration, gap=250):
    """Clamp, sort, and merge intervals once, away from the playback loop."""
    if not isfinite(duration) or duration <= 0:
        raise ValueError('A finite positive duration is required')
    clean=[]
    for value in spans:
        a,b=float(value['start']),float(value['end'])
        score=float(value.get('score',1))
        if not all(map(isfinite,(a,b,score))) or a < 0 or b <= a:
            raise ValueError('Malformed interval')
        if a >= duration:continue
        clean.append(Span(int(a),min(int(b),int(duration)),max(0,min(1,score))))
    merged=[]
    for span in sorted(clean,key=lambda s:s.start):
        if merged and span.start <= merged[-1].end+gap:
            last=merged[-1];merged[-1]=Span(last.start,max(last.end,span.end),max(last.score,span.score))
        else:merged.append(span)
    return merged

def from_samples(samples, duration, step=500, enter=.60, leave=.35, before=750, after=500):
    """Hysteresis: one positive opens a span; two low samples close it.

    Every sample must cover the next step; missing frames must fail the scan,
    rather than silently being labelled clear.
    """
    spans=[];start=None;last_positive=0;low=0;peak=0;previous=-step
    for t,score in samples:
        if not isfinite(score) or not 0 <= score <= 1 or t<0 or t>duration or t<=previous or t-previous>step+1:
            raise ValueError('Invalid or incomplete frame coverage')
        previous=t
        if score>=enter or (start is not None and score>=leave):
            if start is None:start=max(0,t-step)
            last_positive=t;low=0;peak=max(peak,score)
        elif start is not None:
            low+=1
            if low>=2:
                spans.append({'start':max(0,start-before),'end':min(duration,last_positive+step+after),'score':peak})
                start=None;peak=0
    if previous < duration-step:raise ValueError('The scan does not cover the complete video')
    if start is not None:spans.append({'start':max(0,start-before),'end':duration,'score':peak})
    return normalize(spans,duration)

class Schedule:
    """O(log n) lookup. No model, file access, or network call occurs here."""
    def __init__(self,spans,duration,lead=200):
        self.spans=normalize(spans,duration);self.starts=[s.start for s in self.spans]
        self.duration=int(duration);self.lead=max(0,min(1000,int(lead)))
    def target(self,position,speed=1):
        guard=self.lead*max(.25,min(4,float(speed)))
        i=bisect_right(self.starts,position+guard)-1
        if i<0:return None
        span=self.spans[i]
        return span.end if position < span.end else None
