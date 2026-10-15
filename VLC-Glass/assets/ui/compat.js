/* Small API fallbacks; syntax lowering is performed by the Android build. */
(()=>{
 if(!Array.prototype.at)Object.defineProperty(Array.prototype,'at',{value:function(index){let n=Math.trunc(index)||0;if(n<0)n+=this.length;return this[n]},configurable:true,writable:true});
 if(!Object.fromEntries)Object.fromEntries=entries=>{const out={};for(const pair of entries)Object.defineProperty(out,pair[0],{value:pair[1],enumerable:true,configurable:true,writable:true});return out};
 if(!CSS.supports('width','min(1px, 2px)'))document.documentElement.classList.add('legacy-ui');
})();
