/* Invoked by the native integration harness; never run by the product UI. */
(async()=>{
 const result={},visible=el=>!!el&&!!el.offsetWidth&&!!el.offsetHeight&&getComputedStyle(el).visibility!=='hidden';
 showHUD();
 const rect=id=>document.getElementById(id).getBoundingClientRect(),inside=r=>r.width>0&&r.height>0&&r.left>=-1&&r.top>=-1&&r.right<=innerWidth+1&&r.bottom<=innerHeight+1;
 const prompt=rect('momentDock'),transport=document.querySelector('.transport').getBoundingClientRect(),seek=document.querySelector('.seek-wrap').getBoundingClientRect();
 result.compact_controls=document.querySelectorAll('.transport button').length===5&&[...document.querySelectorAll('.transport button')].filter(visible).length===3&&!visible($('miniPlayer'))&&!visible($('bottomNav'))&&!visible(document.querySelector('.player-tool-row'));
 result.controls_fit=inside(prompt)&&inside(transport)&&inside(seek)&&prompt.bottom<=seek.top+1&&seek.bottom<=transport.top+1;
 const before={...model.state};let mutations=0;const observer=new MutationObserver(m=>{mutations+=m.filter(v=>v.type==='childList').length});observer.observe($('playerHUD'),{childList:true,subtree:true});
 const started=performance.now();for(let i=0;i<100;i++)updatePlayer();result.update_100_ms=Math.round((performance.now()-started)*100)/100;
 await new Promise(resolve=>setTimeout(resolve,0));observer.disconnect();result.steady_child_mutations=mutations;result.steady_dom=mutations<=2;
 const target=4000;
 window.GlassIntelligence.receive('sceneSearch',{request:window.GlassIntelligence.requestId(),media:model.state.current,results:[{start:target,end:target+1000,similarity:.83,source:'SentrySearch'}]});
 const hit=document.querySelector('.moment-hit'),correct=hit&&+hit.dataset.time===target;if(hit)hit.click();
 for(let i=0;i<30&&Math.abs(model.state.time-target)>700;i++)await new Promise(resolve=>setTimeout(resolve,100));
 result.semantic_result_seek=!!correct&&Math.abs(model.state.time-target)<=700&&$('momentResults').classList.contains('hidden');
 result.result_seek_position_ms=model.state.time;
 window.__qaContract=result;return result;
})()
