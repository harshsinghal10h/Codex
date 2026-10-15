/** Lower shipped Android UI syntax for the Android 9 stock WebView. */
import {readdir,readFile,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {transform} from 'esbuild';
const folder=resolve(process.argv[2]||'build/packed-assets/ui');
for(const name of await readdir(folder)){
 if(!/\.(js|css)$/.test(name))continue;
 let source=await readFile(resolve(folder,name),'utf8');
 if(name.endsWith('.css'))source=source.replace(/([\w-]+):([^;{}]*(?:dvh|dvw)[^;{}]*)(?=[;}])/g,(_,key,value)=>`${key}:${value.replace(/dvh/g,'vh').replace(/dvw/g,'vw')};${key}:${value}`);
 const result=await transform(source,{loader:name.endsWith('.css')?'css':'js',target:'chrome69',charset:'utf8',legalComments:'inline',sourcefile:name});
 await writeFile(resolve(folder,name),result.code);
}
console.log('Prepared Android UI for Chrome/WebView 69 and newer');
