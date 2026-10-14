precision highp float;
uniform vec2 uResolution;
uniform float uTime;
uniform int uScene;
uniform vec2 uTouch;
uniform float uIntensity;
uniform vec3 uAccent;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1.,0.)),f.x),mix(hash(i+vec2(0.,1.)),hash(i+1.),f.x),f.y);}
void main(){
 vec2 uv=gl_FragCoord.xy/uResolution, p=(gl_FragCoord.xy-.5*uResolution)/uResolution.y;
 float t=uTime*.3; p+=(uTouch-.5)*.05; vec3 col=vec3(0.);
 if(uScene==0){
  vec2 q=p*3.0;float a=sin(q.x*1.3+cos(q.y*1.8+t)+t*.7),b=sin(q.y*1.4+sin(q.x*2.2-t)*1.4-t*.6);
  float f=sin(a*2.4+b*2.0+t*.3)*.5+.5;
  col=mix(vec3(.04,.08,.25),vec3(.18,.62,.81),smoothstep(.0,.55,f));col=mix(col,vec3(.91,.31,.48),smoothstep(.5,.9,f));col=mix(col,vec3(.96,.63,.32),smoothstep(.88,1.,f));
 }else if(uScene==1){
  col=vec3(.025,.025,.05);for(int i=0;i<5;i++){float fi=float(i);float curve=sin(p.x*3.5+t+fi*.4)*.15+sin(p.x*1.2-t)*.15+(fi-2.)*.055;float d=abs(p.y-curve);float ribbon=exp(-d*55.);vec3 c=mix(vec3(.2,.44,.91),vec3(.99,.48,.34),fi/4.);col+=c*ribbon*.7;}
 }else if(uScene==2){
  col=mix(vec3(.025,.09,.13),vec3(.12,.3,.37),uv.y);vec2 cell=floor(uv*vec2(25.,38.));float h=hash(cell);float rain=fract(uv.y*38.+t*(1.8+h*2.));float line=smoothstep(.035,.0,abs(fract(uv.x*25.)-.5));col+=vec3(.35,.58,.65)*line*smoothstep(.7,1.,rain)*step(.37,h)*.5;
  col+=vec3(.13,.22,.18)*exp(-length(p-vec2(.15,.22))*6.);
 }else if(uScene==3){
  col=mix(vec3(.008,.08,.15),vec3(.04,.4,.55),uv.y);for(int i=0;i<5;i++){float k=float(i)+1.;float w=sin(p.x*(4.+k*1.7)+t*k*.7+sin(p.y*5.-t))*.018;float edge=abs(fract((p.y+w)*7.+t*.15)-.5);col+=vec3(.1,.35,.4)*exp(-edge*30.)/(k*.9);}
 }else if(uScene==4){
  col=mix(vec3(.003,.012,.04),vec3(.035,.06,.15),uv.y);float band=sin(p.x*3.+t)*.16+sin(p.x*7.-t*.3)*.05+.1;float a=exp(-abs(p.y-band)*9.)*smoothstep(-.1,.3,p.y);float curtains=(sin(p.x*36.+sin(p.x*5.+t)*3.)*.5+.5);col+=mix(vec3(.08,.65,.36),vec3(.15,.33,.78),uv.x)*a*(.35+curtains*.5);
 }else if(uScene==5){
  float v=noise(p*3.+vec2(t*.1,0.))*.7+noise(p*6.)*.3;float band=floor(v*10.)/10.;col=mix(vec3(.05,.09,.12),vec3(.6,.72,.63),band);float edge=smoothstep(.015,.0,abs(fract(v*10.)-.04));col+=vec3(.12,.19,.16)*edge;
 }else if(uScene==6){
  col=vec3(.006,.009,.024);vec2 q=p*28.;vec2 cell=floor(q);vec2 point=vec2(hash(cell),hash(cell+2.));float star=exp(-length(fract(q)-point)*75.)*step(.94,hash(cell+3.));col+=vec3(.7,.82,1.)*star*(.65+sin(t+hash(cell)*6.)*.35);col+=uAccent*.04*exp(-length(p)*2.);
 }else{
  float f=sin(p.x*1.8+t*.4)+sin(p.y*2.-t*.3);col=mix(vec3(.06,.08,.16),uAccent*.65,f*.22+.5);col+=vec3(.12,.035,.09)*smoothstep(.6,0.,length(p-vec2(.2,.2)));
 }
 float vignette=1.-.18*length(p);col*=vignette*clamp(uIntensity,.2,1.6);col+=(hash(gl_FragCoord.xy)-.5)/255.;gl_FragColor=vec4(col,1.);
}
