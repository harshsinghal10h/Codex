/* Invoked only by native integration harnesses against real player state. */
(async()=>{
 const result={},sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
 const visible=el=>!!el&&!!el.offsetWidth&&!!el.offsetHeight&&getComputedStyle(el).visibility!=='hidden';
 const inside=r=>r.width>0&&r.height>0&&r.left>=-1&&r.top>=-1&&r.right<=innerWidth+1&&r.bottom<=innerHeight+1;
 const button=action=>action==='volumePanel'?$('volumeButton'):document.querySelector('#playerHUD [data-action="'+action+'"]');
 const wait=async(test,label)=>{for(let i=0;i<50;i++){if(test())return;await sleep(100);}throw Error(label+' did not reach native state: '+JSON.stringify({time:model.state.time,playing:model.state.playing,speed:model.state.speed,volume:model.state.volume}));};
 try{
  showHUD();
  const prompt=$('momentDock').getBoundingClientRect(),transport=document.querySelector('.transport').getBoundingClientRect(),seek=document.querySelector('.seek-wrap').getBoundingClientRect(),tools=document.querySelector('.player-tool-row').getBoundingClientRect();
  const actions=['previous','rewind','toggle','forward','next','speed','tracks','queue','volumePanel','screen','lock','playerOptions'];
  result.direct_controls=actions.every(name=>visible(button(name)))&&!visible($('miniPlayer'))&&!visible($('bottomNav'));
  const targets=actions.map(name=>button(name).getBoundingClientRect());
  result.touch_targets=targets.every(r=>r.width>=43&&r.height>=43);
  result.controls_fit=[prompt,transport,seek,tools,...targets].every(inside)&&prompt.bottom<=seek.top+1&&seek.bottom<=Math.min(transport.top,tools.top)+1;
  for(let i=0;i<targets.length;i++)for(let j=i+1;j<targets.length;j++){const a=targets[i],b=targets[j];if(Math.min(a.right,b.right)-Math.max(a.left,b.left)>1&&Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top)>1)result.controls_fit=false;}
  let mutations=0;const observer=new MutationObserver(ms=>{mutations+=ms.filter(v=>v.type==='childList').length});observer.observe($('playerHUD'),{childList:true,subtree:true});
  const started=performance.now();for(let i=0;i<100;i++)updatePlayer();result.update_100_ms=Math.round((performance.now()-started)*100)/100;
  await sleep(0);observer.disconnect();result.steady_child_mutations=mutations;result.steady_dom=mutations<=2;
  const target=4000;
  window.GlassIntelligence.receive('sceneSearch',{request:window.GlassIntelligence.requestId(),media:model.state.current,results:[{start:target,end:target+1000,similarity:.83,source:'SentrySearch'}]});
  const hit=document.querySelector('.moment-hit'),correct=hit&&+hit.dataset.time===target;if(hit)hit.click();
  await wait(()=>Math.abs(model.state.time-target)<=900,'Result seek');
  result.semantic_result_seek=!!correct&&$('momentResults').classList.contains('hidden');result.result_seek_position_ms=model.state.time;
  if(model.state.playing){$('playButton').click();await wait(()=>!model.state.playing,'Pause');}
  $('playButton').click();await wait(()=>model.state.playing,'Play');
  $('playButton').click();await wait(()=>!model.state.playing,'Pause');result.play_pause_control=true;
  const start=model.state.time;button('forward').click();await wait(()=>Math.abs(model.state.time-Math.min(start+10000,model.state.duration))<=900,'Forward button');
  button('rewind').click();await wait(()=>Math.abs(model.state.time-start)<=1100,'Rewind button');result.seek_buttons=true;
  button('speed').click();document.querySelector('[data-action="setSpeed"][data-value="1.5"]').click();await wait(()=>Math.abs(model.state.speed-1.5)<.01,'Speed selection');
  button('speed').click();document.querySelector('[data-action="setSpeed"][data-value="1"]').click();await wait(()=>Math.abs(model.state.speed-1)<.01,'Normal speed');result.speed_control=true;
  button('tracks').click();const tracks=model.dialog==='tracks'&&!!document.querySelector('[data-action="pickSubtitle"]');closeDialog();
  button('queue').click();const queue=model.dialog==='queue'&&document.querySelectorAll('.queue-row').length===model.state.queue.length;closeDialog();result.track_queue_access=tracks&&queue;
  button('volumePanel').click();const volume=$('volume');volume.value=37;volume.dispatchEvent(new Event('input',{bubbles:true}));volume.dispatchEvent(new Event('change',{bubbles:true}));await wait(()=>model.state.volume===37,'Volume slider');
  $('muteButton').click();await wait(()=>model.state.volume===0,'Mute');$('muteButton').click();await wait(()=>model.state.volume===37,'Unmute');
  result.volume_control=visible(volume)&&$('volumeButton').getAttribute('aria-expanded')==='true';button('volumePanel').click();
  const original=model.state.current;button('next').click();await wait(()=>model.state.current!==original&&model.state.playing,'Next media');$('playButton').click();await wait(()=>!model.state.playing,'Pause next media');button('previous').click();await wait(()=>model.state.current===original&&model.state.playing,'Previous media');$('playButton').click();await wait(()=>!model.state.playing,'Pause previous media');result.queue_navigation=true;
  const more=button('playerOptions');more.focus();more.click();document.querySelector('#dialogBody [data-action="quality"]').click();closeDialog();result.dialog_focus=document.activeElement===more;
  // A space key on a focused button must be left to the button's default click,
  // rather than toggling playback as well as opening that button's action.
  button('speed').focus();const space=new KeyboardEvent('keydown',{key:' ',code:'Space',bubbles:true,cancelable:true});button('speed').dispatchEvent(space);result.keyboard_button=!space.defaultPrevented;
  button('lock').click();const locked=model.locked&&visible($('unlockButton'))&&$('playerHUD').getAttribute('aria-hidden')==='true';
  $('unlockButton').click();result.lock_controls=locked&&!model.locked&&$('playerHUD').getAttribute('aria-hidden')==='false';
  document.activeElement.blur();showHUD();
 }catch(error){result.error=String(error);}
 window.__qaContract=result;return result;
})()
