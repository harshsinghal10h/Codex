import fs from 'node:fs';
import path from 'node:path';
import * as k from '../vendor/anatomy/iso-kit.mjs';

// Anatomy Figure: a 79-part physical audio console. Ten faders represent
// the ten saved equalizer bands. The transport carriage follows media time.
const base={x:-150,y:-70,w:300,d:140,r:12},P=k.fitProjection(k.boxCorners(base,-18,33),600,310,{pad:32,azimuth:41,elevation:33});
const S=(plan,z,h,tone='mid')=>k.solidSvg(k.slabOf(plan,z,h,P,8,.8),{tone});
const L=(d,tone='lo')=>k.lineSvg(d,{tone});
let out=[],parts=0;
const add=x=>{out.push(x);parts++};
out.push(`<path class="iso-halo" d="${k.haloOf(base,-18,20,P)}"/>`);
for(const [x,y] of k.corners(base,22).sort((a,b)=>k.depthOf([a[0],a[1],0],P)-k.depthOf([b[0],b[1],0],P)))add(S({x:x-8,y:y-6,w:16,d:12,r:3},-18,6,'lo'));
add(S(base,-12,10,'lo'));add(S(k.insetPlan(base,1),-2,10,'mid'));add(S(k.insetPlan(base,4),8,2,'mid'));
out.push(L(k.planOutline(k.insetPlan(base,6),10,P),'faint'));
for(const x of [-138,138])for(const y of [-57,57]){add(S({x:x-1.2,y:y-1.2,w:2.4,d:2.4,r:.4},10,.7,'lo'));out.push(L(k.lineOnTop([x-1,y],[x+1,y],10.7,P),'mid'))}
const railY=-49,railH=71;
for(let i=0;i<10;i++){
 const x=-120+i*25.7;
 add(S({x:x-9,y:railY-5,w:18,d:railH+10,r:3},10,1,'lo'));
 add(S({x:x-1.5,y:railY,w:3,d:railH,r:.8},11,1,'lo'));
 for(let j=0;j<7;j++){const y=railY+5+j*9;out.push(L(k.lineOnTop([x-9,y],[x-5,y],11.2,P),j===3?'hi':'faint'))}
 const knob=[S({x:x-7,y:-20,w:14,d:10,r:2},12,3,'lo'),S({x:x-8,y:-21,w:16,d:12,r:2.2},15,3,'hi'),L(k.lineOnTop([x-8,-15],[x+8,-15],18.2,P),'lo')];
 out.push(`<g data-fader="${i}" data-x="${x}">${knob.join('')}</g>`);parts+=2;
 add(S({x:x-6,y:28,w:12,d:3,r:.8},10,.6,'lo'));
}
const timeline={x:-112,y:44,w:183,d:5,r:1.7};add(S(timeline,10,1,'lo'));
const carriage=[S({x:-116,y:40,w:9,d:13,r:2},11,2,'hi'),L(k.lineOnTop([-112,40],[-112,53],13.2,P),'lo')];
out.push(`<g data-carriage>${carriage.join('')}</g>`);parts+=1;
add(S({x:85,y:38,w:28,d:17,r:3},10,1,'lo'));add(S({x:89,y:41,w:20,d:11,r:2},11,2,'hi'));
out.push(L(k.lineOnTop([96,44],[96,49],13.2,P),'lo'),L(k.lineOnTop([101,44],[101,49],13.2,P),'lo'));
for(let i=0;i<8;i++)add(S({x:-139+i*5,y:64,w:2.5,d:2,r:.3},-4,3,'lo'));
add(S({x:124,y:-54,w:20,d:20,r:8},10,2,'lo'));add(S({x:126,y:-52,w:16,d:16,r:7},12,2,'mid'));
out.push(`<g transform="${k.topMatrix([134,-44],14.2,P,'x')}"><g data-flywheel><circle r="6" fill="var(--anatomy-hi)" stroke="var(--anatomy-lo)" stroke-width=".5"/>${Array.from({length:12},(_,i)=>{const a=i*Math.PI/6;return `<path d="M${Math.cos(a)*4},${Math.sin(a)*4} L${Math.cos(a)*5.5},${Math.sin(a)*5.5}" stroke="var(--anatomy-lo)" stroke-width=".55"/>`}).join('')}<circle r="1.3" fill="var(--anatomy-lo)"/><circle cx="2" cy="-2" r=".65" fill="var(--anatomy-lit)"/></g></g>`);
for(let i=0;i<3;i++){add(S({x:130,y:-15+i*10,w:8,d:5,r:1.5},10,.8,'lo'));out.push(`<g data-lamp="${i}">${S({x:132,y:-13.7+i*10,w:4,d:2.4,r:.7},10.8,.5,'hi')}</g>`)}
out.push(`<style>@keyframes deckTurn{to{transform:rotate(360deg)}}@keyframes deckLamp{0%,100%{opacity:.35}50%{opacity:.85}}[data-flywheel]{transform-origin:0px 0px;animation:deckTurn 32s linear infinite}[data-lamp]{animation:deckLamp 4.8s ease-in-out infinite}[data-lamp="1"]{animation-delay:-1.6s}[data-lamp="2"]{animation-delay:-3.2s}.deck-running [data-flywheel]{animation-duration:7s}.artwork-paused [data-flywheel],.artwork-paused [data-lamp]{animation-play-state:paused}.artwork-still [data-flywheel],.artwork-still [data-lamp]{animation:none!important}@media(prefers-reduced-motion:reduce){[data-flywheel],[data-lamp]{animation:none!important}}</style>`);
const label={x:116,y:38,w:21,d:17,r:1};add(S(label,10,.5,'lo'));
out.push(k.faceTextSvg(k.topMatrix([126,48],10.7,P,'x'),'AUDIO',{size:3,tone:'mid',anchor:'middle'}));
const svg=k.figureSvg({width:600,height:310,label:'A physical audio console with ten equalizer faders, precise rails, a turning flywheel, three breathing status lamps, transport keys and a timeline carriage. Faders follow saved band gains; the carriage follows the actual playback position.',body:out,standalone:'dark'});
const ui=new URL('../assets/ui/',import.meta.url);fs.writeFileSync(new URL('deck.svg',ui),svg);
fs.copyFileSync(new URL('../vendor/anatomy/iso.css',import.meta.url),new URL('anatomy.css',ui));
const py=JSON.stringify({zero:k.translateAlong(P,[0,0,0]),y:k.translateAlong(P,[0,1,0]),x:k.translateAlong(P,[1,0,0])});
fs.writeFileSync(new URL('deck.js',ui),`(()=>{const markup=${JSON.stringify(svg)};const P=${py};const y=P.y.match(/[-+.0-9]+/g).map(Number),x=P.x.match(/[-+.0-9]+/g).map(Number);let frames=[],raf=0,target={playing:false,time:0,duration:0,bands:Array(10).fill(0)},enabled=true,looping=true,last=0;const reduced=()=>!enabled||matchMedia('(prefers-reduced-motion: reduce)').matches;function mount(el){if(!el||el.dataset.mounted)return;el.dataset.mounted='true';el.innerHTML=markup;el.classList.add('iso');const f={el,faders:[...el.querySelectorAll('[data-fader]')],carriage:el.querySelector('[data-carriage]'),values:Array(10).fill(0),progress:0};frames.push(f);visibility.observe(el);stillness();}function stillness(){for(const f of frames){f.el.classList.toggle('artwork-still',reduced());f.el.classList.toggle('artwork-paused',!looping||document.hidden||!f.visible||!!(document.body.classList.contains('player-open')&&f.el.closest('#appShell')));f.el.classList.toggle('deck-running',target.playing)}}const visibility=new IntersectionObserver(entries=>{for(const entry of entries){const f=frames.find(f=>f.el===entry.target);if(f)f.visible=entry.isIntersecting}stillness();start()});function tick(t){const dt=Math.min((t-last)/1000||.016,.033);last=t;let moving=false;frames=frames.filter(f=>{if(!f.el.isConnected){visibility.unobserve(f.el);return false}return true});for(const f of frames){const r=f.el.getBoundingClientRect();if(r.bottom<0||r.top>innerHeight||!f.el.offsetWidth)continue;for(let i=0;i<10;i++){const end=-(target.bands[i]||0)*1.7;f.values[i]=reduced()?end:f.values[i]+(end-f.values[i])*(1-Math.exp(-dt/.16));if(Math.abs(end-f.values[i])>.01)moving=true;f.faders[i].setAttribute('transform','translate('+(y[0]*f.values[i])+','+(y[1]*f.values[i])+')');}const p=target.duration?Math.min(1,target.time/target.duration)*176:0;f.progress=reduced()?p:f.progress+(p-f.progress)*(1-Math.exp(-dt/.1));f.carriage.setAttribute('transform','translate('+(x[0]*f.progress)+','+(x[1]*f.progress)+')');if(Math.abs(p-f.progress)>.03)moving=true;}raf=moving?requestAnimationFrame(tick):0;}function start(){if(!raf){last=0;raf=requestAnimationFrame(tick);}}window.AudioDeck={mount,mountAll(){document.querySelectorAll('[data-deck]').forEach(mount);start()},state(s){target={...target,...s};stillness();start()},bands(b){target.bands=b;start()},motion(on){enabled=on;stillness();start()},loop(on){looping=on;stillness()}};document.addEventListener('visibilitychange',()=>{stillness();if(document.hidden){cancelAnimationFrame(raf);raf=0;}else start();});})();`);
console.log('Anatomy audio console:',parts,'parts; asset',svg.length,'bytes');
