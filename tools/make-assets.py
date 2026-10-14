#!/usr/bin/env python3
"""Deterministic still frames of the bundled shader designs. No downloaded artwork."""
from pathlib import Path
import numpy as np
from PIL import Image
root=Path(__file__).resolve().parents[1]/'app/src/main/assets/thumbs';root.mkdir(parents=True,exist_ok=True)
w,h=512,288;y,x=np.mgrid[0:h,0:w];uvx=x/w;uvy=1-y/h;px=(x-w/2)/h;py=(h/2-y)/h;t=1.3
def mix(a,b,f):return np.array(a)[None,None,:]*(1-f[...,None])+np.array(b)[None,None,:]*f[...,None]
def smooth(a,b,v):q=np.clip((v-a)/(b-a),0,1);return q*q*(3-2*q)
def hashed(a,b):return np.mod(np.sin(a*127.1+b*311.7)*43758.5453,1)
def noise(a,b):ix=np.floor(a);iy=np.floor(b);fx=np.mod(a,1);fy=np.mod(b,1);fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);return (hashed(ix,iy)*(1-fx)+hashed(ix+1,iy)*fx)*(1-fy)+(hashed(ix,iy+1)*(1-fx)+hashed(ix+1,iy+1)*fx)*fy
for name in ['fluids','ribbons','rain','waves','aurora','depth','stars','gradient']:
 if name=='fluids':
  a=np.sin(px*3*1.3+np.cos(py*3*1.8+t)+t*.7);b=np.sin(py*3*1.4+np.sin(px*3*2.2-t)*1.4-t*.6);f=np.sin(a*2.4+b*2+t*.3)*.5+.5
  col=mix([.04,.08,.25],[.18,.62,.81],smooth(0,.55,f));v=smooth(.5,.9,f)[...,None];col=col*(1-v)+np.array([.91,.31,.48])*v;v=smooth(.88,1,f)[...,None];col=col*(1-v)+np.array([.96,.63,.32])*v
 elif name=='ribbons':
  col=np.zeros((h,w,3))+[.025,.025,.05]
  for i in range(5):curve=np.sin(px*3.5+t+i*.4)*.15+np.sin(px*1.2-t)*.15+(i-2)*.055;c=np.array([.2,.44,.91])*(1-i/4)+np.array([.99,.48,.34])*i/4;col+=c*np.exp(-np.abs(py-curve)*55)[...,None]*.7
 elif name=='rain':
  col=mix([.025,.09,.13],[.12,.3,.37],uvy);cellx=np.floor(uvx*25);celly=np.floor(uvy*38);hv=hashed(cellx,celly);rain=np.mod(uvy*38+t*(1.8+hv*2),1);line=smooth(.035,0,np.abs(np.mod(uvx*25,1)-.5));col+=np.array([.35,.58,.65])*line[...,None]*smooth(.7,1,rain)[...,None]*(hv>.37)[...,None]*.5
 elif name=='waves':
  col=mix([.008,.08,.15],[.04,.4,.55],uvy)
  for k in range(1,6):wave=np.sin(px*(4+k*1.7)+t*k*.7+np.sin(py*5-t))*.018;edge=np.abs(np.mod((py+wave)*7+t*.15,1)-.5);col+=np.array([.1,.35,.4])*np.exp(-edge*30)[...,None]/(k*.9)
 elif name=='aurora':
  col=mix([.003,.012,.04],[.035,.06,.15],uvy);band=np.sin(px*3+t)*.16+np.sin(px*7-t*.3)*.05+.1;a=np.exp(-np.abs(py-band)*9)*smooth(-.1,.3,py);curt=np.sin(px*36+np.sin(px*5+t)*3)*.5+.5;col+=mix([.08,.65,.36],[.15,.33,.78],uvx)*a[...,None]*(.35+curt*.5)[...,None]
 elif name=='depth':
  v=noise(px*3+t*.1,py*3)*.7+noise(px*6,py*6)*.3;band=np.floor(v*10)/10;col=mix([.05,.09,.12],[.6,.72,.63],band);edge=smooth(.015,0,np.abs(np.mod(v*10,1)-.04));col+=np.array([.12,.19,.16])*edge[...,None]
 elif name=='stars':
  col=np.zeros((h,w,3))+[.006,.009,.024];qx=px*28;qy=py*28;cx=np.floor(qx);cy=np.floor(qy);dx=np.mod(qx,1)-hashed(cx,cy);dy=np.mod(qy,1)-hashed(cx+2,cy+2);star=np.exp(-np.hypot(dx,dy)*75)*(hashed(cx+3,cy+3)>.94);col+=np.array([.7,.82,1])*star[...,None]*(.65+np.sin(t+hashed(cx,cy)*6)*.35)[...,None]
 else:
  f=np.sin(px*1.8+t*.4)+np.sin(py*2-t*.3);col=mix([.06,.08,.16],np.array([.35,.66,.98])*.65,f*.22+.5);col+=np.array([.12,.035,.09])*smooth(.6,0,np.hypot(px-.2,py-.2))[...,None]
 col*= (1-.18*np.hypot(px,py))[...,None];Image.fromarray(np.uint8(np.clip(col,0,1)*255)).save(root/(name+'.jpg'),quality=90)
print('Generated eight shader thumbnails')
