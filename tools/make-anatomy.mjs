import * as k from './vendor/iso-kit.mjs';
import {writeFileSync} from 'node:fs';
const width=640,height=400,parts=[];
const base={x:-105,y:-74,w:210,d:148,r:7};
const P=k.fitProjection([...k.boxCorners(base,-12,0),[100,70,30]],width,height,{pad:34,azimuth:42});
function slab(name,plan,z,h,tone='mid'){parts.push({name,z,key:k.depthOf([plan.x+plan.w/2,plan.y+plan.d/2,z+h/2],P),svg:k.solidSvg(k.slabOf(plan,z,h,P,5,.6),{tone,lit:name==='decoder cap'})});}
for(const [x,y] of k.corners(base,16))slab('rubber foot',{x:x-6,y:y-6,w:12,d:12,r:2},-12,5,'lo');
slab('bench plate',base,-7,7,'lo');
// A phone-shaped display assembly, with a bezel, pixel plane and visible corner mounts.
slab('display back',{x:1,y:-60,w:88,d:121,r:10},0,9,'mid');
slab('display bezel',{x:4,y:-57,w:82,d:115,r:8},9,2,'hi');
slab('display panel',{x:9,y:-48,w:72,d:96,r:4},11,1,'lo');
for(let i=0;i<9;i++)slab('pixel band '+i,{x:13,y:-43+i*9.7,w:64,d:7,r:2},12,.35,i<4?'hi':'mid');
slab('speaker slot',{x:30,y:-54,w:28,d:2,r:1},11,.35,'lo');
slab('home gesture bar',{x:30,y:53,w:27,d:2,r:1},11,.35,'lo');
// Decoder board: one IC package mounted on a PCB, pins terminating on pads.
slab('decoder PCB',{x:-91,y:-54,w:77,d:71,r:4},0,2,'mid');
slab('decoder package',{x:-73,y:-35,w:40,d:35,r:2},2,6,'hi');
slab('decoder cap',{x:-71,y:-33,w:36,d:31,r:2},8,1,'hi');
for(let i=0;i<8;i++){slab('left pin '+i,{x:-81,y:-31+i*3.6,w:8,d:1.6,r:.3},2,1,'mid');slab('right pin '+i,{x:-33,y:-31+i*3.6,w:8,d:1.6,r:.3},2,1,'mid');}
// Two memory modules: four physical memory packages and seated connectors each.
for(let j=0;j<2;j++){
 slab('memory PCB '+j,{x:-91,y:28+j*15,w:77,d:11,r:1},0,2,'mid');
 for(let i=0;i<4;i++)slab('memory IC '+j+'-'+i,{x:-86+i*17,y:30+j*15,w:12,d:7,r:.7},2,2,'lo');
}
parts.sort((a,b)=>a.z-b.z||a.key-b.key);
const body=`<path class="iso-halo" d="${k.haloOf(base,-12,7,P)}"/>`+parts.map(p=>p.svg).join('');
let svg=k.figureSvg({width,height,label:'Display, decoder and memory modules',body,standalone:'dark'});const roles=Object.fromEntries([...svg.split('.iso[data-theme="light"]')[0].matchAll(/(--anatomy-[\w-]+):([^;]+);/g)].map(m=>[m[1],m[2]]));svg=svg.replace(/var\((--anatomy-[\w-]+)\)/g,(_,v)=>roles[v]||'#777').replace(/\.iso-svg\{[^}]*\}/,'').replace('<svg',`<svg width="${width}" height="${height}"`);writeFileSync(new URL('../app/src/main/assets/display-engine.svg',import.meta.url),svg);
console.log(`Anatomy: ${parts.length} seated solid parts, standalone SVG`);
