package com.harsh.vlcglass;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.view.TextureView;
import org.json.*;
import java.util.*;

public final class PlaybackService extends Service {
 public interface Listener{void state(JSONObject s);void library();void error(String m);void protection(boolean hidden);}
 public class LocalBinder extends Binder{public PlaybackService service(){return PlaybackService.this;}}
 private final LocalBinder binder=new LocalBinder();private final Handler handler=new Handler(Looper.getMainLooper());
 public LibraryStore store;public VlcEngine engine;public Listener listener;private MediaSession session;private AudioManager audio;private AudioFocusRequest focus;private PowerManager.WakeLock wake;
 private final ArrayList<Long> queue=new ArrayList<>();private int cursor=-1;public long current=-1;private long lastSave=0,pendingResume=-1,sleepAt=0,abA=-1,abB=-1;private boolean shuffle=false,ducked=false,focusPaused=false;private String repeat="off",fault="";private int lastState=0;private Random random=new Random();private JSONObject settings=new JSONObject();
 private final java.util.concurrent.ExecutorService analysis=java.util.concurrent.Executors.newSingleThreadExecutor();
 private SafetyScanner scanner;private SafetyTimeline schedule;private JSONObject guard=new JSONObject();private volatile int guardEpoch=0;private boolean guardBlocked=false,guardMasked=false;private long guardSeek=-1,guardSeekAt=0;
 private final Runnable guardClock=new Runnable(){public void run(){try{
  if(engine!=null&&current>0&&settings.optBoolean("sceneGuard",false)&&schedule!=null&&!guardBlocked){
   long now=engine.time();
   if(guardSeek>=0){if(SystemClock.uptimeMillis()-guardSeekAt>80&&now>=guardSeek&&now<guardSeek+3000){guardSeek=-1;protect(false);}else if(SystemClock.uptimeMillis()-guardSeekAt>10000){engine.command("pause",new JSONObject());guardBlocked=true;guard.put("blocked",true).put("error","The decoder did not reach the requested clear position. Playback is paused.");push();}}
   else if(engine.playing()){long target=schedule.target(now,((Number)engine.get("getRate",1f)).floatValue());if(target>=0){protect(true);if(target>=schedule.duration-80){engine.command("pause",new JSONObject());next(true);}else protectedSeek(target);}}
  }
 }catch(Exception e){try{if(engine!=null)engine.command("pause",new JSONObject());guardBlocked=true;guard.put("blocked",true).put("error","Scene guard paused playback: "+e.getMessage());protect(true);push();}catch(Exception ignored){}}
 handler.postDelayed(this,50);}};
 private void protect(boolean hidden){guardMasked=hidden;if(engine!=null)try{VlcEngine.call(engine.player,"setVolume",hidden?0:settings.optInt("volume",80));}catch(Exception ignored){}if(listener!=null)listener.protection(hidden);}
 private void protectedSeek(long target)throws Exception{protect(true);guardSeek=target;guardSeekAt=SystemClock.uptimeMillis();engine.seek(target);}
 private void guardedSeek(long at)throws Exception{
  at=Math.max(0,Math.min(schedule.duration,at));long target=schedule.target(at,1);if(target>=0)at=target;
  if(at>=schedule.duration-80){engine.command("pause",new JSONObject());next(true);}else protectedSeek(at);
 }
 public IBinder onBind(Intent i){return binder;}
 public void onCreate(){super.onCreate();store=new LibraryStore(this);try{settings=new JSONObject(getSharedPreferences("preferences",0).getString("settings","{}"));engine=new VlcEngine(this);}catch(Exception e){fault="VLC could not start: "+e.getMessage();}
  audio=(AudioManager)getSystemService(AUDIO_SERVICE);focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build()).setOnAudioFocusChangeListener(change->{if(engine==null)return;try{if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){focusPaused=engine.playing();engine.command("pause",new JSONObject());}else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){ducked=true;VlcEngine.call(engine.player,"setVolume",Math.max(0,settings.optInt("volume",80)/4));}else if(change==AudioManager.AUDIOFOCUS_GAIN){if(ducked){ducked=false;VlcEngine.call(engine.player,"setVolume",settings.optInt("volume",80));}if(focusPaused){focusPaused=false;command("play",new JSONObject());}}}catch(Exception e){fail(e.getMessage());}},handler).build();
  registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("playback","Playback",NotificationManager.IMPORTANCE_LOW));
  session=new MediaSession(this,"VLC Glass");session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);session.setCallback(new MediaSession.Callback(){public void onPlay(){command("play",new JSONObject());}public void onPause(){command("pause",new JSONObject());}public void onStop(){command("stop",new JSONObject());}public void onSkipToNext(){command("next",new JSONObject());}public void onSkipToPrevious(){command("previous",new JSONObject());}public void onSeekTo(long pos){JSONObject a=new JSONObject();try{a.put("time",pos);}catch(Exception ignored){}command("seek",a);}});session.setActive(true);
  wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"VLCGlass:playback");handler.post(tick);handler.post(guardClock);scanner=new SafetyScanner(this);
 }
 private final BroadcastReceiver noisy=new BroadcastReceiver(){public void onReceive(Context c,Intent i){command("pause",new JSONObject());}};
 public int onStartCommand(Intent i,int flags,int id){if(i!=null&&i.getAction()!=null)command(i.getAction(),new JSONObject());return START_NOT_STICKY;}
 public void settings(JSONObject s){boolean changed=settings.optBoolean("sceneGuard",false)!=s.optBoolean("sceneGuard",false);settings=s;if(changed&&current>0){if(settings.optBoolean("sceneGuard",false))open(current,null);else if(guardBlocked){guardEpoch++;guardBlocked=false;guard=new JSONObject();schedule=null;protect(false);beginCurrent();}else{schedule=null;guard=new JSONObject();guardSeek=-1;protect(false);push();}}}
 public void attach(TextureView view,TextureView subtitles,int w,int h){if(engine!=null)try{engine.attach(view,subtitles,w,h);}catch(Exception e){fail(e.getMessage());}}
 public void open(long id,JSONArray ids){
  if(engine==null){fail(fault);return;}save();guardEpoch++;guardSeek=-1;schedule=null;guardBlocked=false;guard=new JSONObject();protect(settings.optBoolean("sceneGuard",false));
  if(ids!=null){queue.clear();for(int i=0;i<ids.length();i++)if(store.media(ids.optLong(i))!=null)queue.add(ids.optLong(i));}
  if(!queue.contains(id))queue.add(id);cursor=queue.indexOf(id);current=id;JSONObject item=store.media(id);
  if(item==null){fail("This item was removed. Open the file again.");return;}
  fault="";lastState=0;pendingResume=settings.optBoolean("resume",true)?item.optLong("position",0):0;abA=abB=-1;
  if(settings.optBoolean("sceneGuard",false)&&!item.optString("kind").equals("audio")){prepareGuard(item);return;}beginCurrent();
 }
 private void prepareGuard(JSONObject item){
  engine.stop();guardBlocked=true;protect(true);final int generation=++guardEpoch;final long id=current;
  try{guard=new JSONObject().put("blocked",true).put("phase","hashing").put("progress",0);}catch(Exception ignored){}push();
  analysis.execute(()->{try{
   JSONObject map=scanner.scan(item.optString("uri"),(phase,percent)->handler.post(()->{if(generation!=guardEpoch||id!=current)return;try{guard.put("phase",phase).put("progress",percent);push();}catch(Exception ignored){}}),()->generation!=guardEpoch||Thread.currentThread().isInterrupted());
   handler.post(()->{if(generation!=guardEpoch||id!=current)return;try{schedule=SafetyScanner.timeline(map);guard=map;guard.put("blocked",false).put("phase","ready").put("progress",100);guardBlocked=false;beginCurrent();}catch(Exception e){guardFailure(e);}});
  }catch(InterruptedException ignored){}catch(Exception e){handler.post(()->{if(generation==guardEpoch&&id==current)guardFailure(e);});}});
 }
 private void guardFailure(Exception e){guardBlocked=true;protect(true);try{guard.put("blocked",true).put("error",e.getMessage()==null?"The local video scan failed.":e.getMessage());}catch(Exception ignored){}push();}
 private void beginCurrent(){
  JSONObject item=store.media(current);if(item==null||engine==null)return;
  try{if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)throw new Exception("Another app is using audio. Try playback again.");
   JSONObject options=new JSONObject(settings.toString());long start=Math.max(0,pendingResume);
   if(schedule!=null){long target=schedule.target(start,1);if(target>=0)start=target;if(start>=schedule.duration-80){guardBlocked=true;guard.put("blocked",true).put("error","All remaining video is inside detected sections. Choose another video or turn off Scene guard.");protect(true);push();return;}options.put("guardStart",start);options.put("volume",0);guardSeek=start;guardSeekAt=SystemClock.uptimeMillis();protect(true);}
   else protect(false);
   engine.play(this,item.getString("uri"),options);if(settings.optBoolean("eqEnabled",false))engine.setEqualizer(settings);store.progress(current,0,item.optLong("duration"));notifyMedia(true);push();
  }catch(Exception e){pendingResume=-1;if(schedule!=null)guardFailure(e);else fail(e.getMessage());}
 }
 public void command(String op,JSONObject a){if(engine==null){fail(fault);return;}try{
  if(op.equals("guardScan")){if(current>0)open(current,null);return;}
  if(guardBlocked&&!op.equals("stop")&&!op.equals("open")&&!op.equals("next")&&!op.equals("previous")){push();return;}
  if(op.equals("renderer")&&a.optInt("id",-1)>=0&&settings.optBoolean("sceneGuard",false))throw new Exception("Scene guard supports local playback. Turn it off before casting.");
  if(schedule!=null&&(op.equals("seek")||op.equals("skip"))){guardedSeek(op.equals("seek")?a.optLong("time"):engine.time()+a.optLong("seconds",10)*1000);push();return;}
  if(schedule!=null&&op.equals("chapter")){
   JSONArray chapters=engine.details().optJSONArray("chapters");int index=a.optInt("index",-1);
   if(chapters==null||index<0||index>=chapters.length()||!chapters.getJSONObject(index).has("time"))throw new Exception("This chapter has no verified timestamp.");
   guardedSeek(chapters.getJSONObject(index).getLong("time"));push();return;
  }
  if(schedule!=null&&guardSeek<0&&(op.equals("play")||op.equals("toggle"))&&!engine.playing()){
   int state=engine.state();
   if(state==0||state==5||state==6||engine.time()<0){pendingResume=0;beginCurrent();return;}
   long target=schedule.target(Math.max(0,engine.time()),1);if(target>=0)guardedSeek(target);
  }
  switch(op){case "open":open(a.optLong("id"),a.optJSONArray("queue"));return;
  case "next":next(false);return;case "previous":if(engine.time()>3000||cursor<=0){if(schedule!=null)guardedSeek(0);else engine.seek(0);}else open(queue.get(cursor-1),null);return;
  case "repeat":repeat=a.optString("value","off");break;case "shuffle":shuffle=a.optBoolean("value");break;
  case "scanCast":engine.discover(this,()->push());handler.postDelayed(()->engine.stopDiscovery(),12000);break;
  case "renderer":engine.renderer(a.optInt("id",-1));break;
  case "sleep":sleepAt=a.optInt("minutes")>0?System.currentTimeMillis()+a.optInt("minutes")*60000L:0;break;
  case "ab":if(abA<0)abA=engine.time();else if(abB<0&&engine.time()>abA+500)abB=engine.time();else abA=abB=-1;break;
  case "stop":guardEpoch++;guardBlocked=false;guard=new JSONObject();schedule=null;guardSeek=-1;protect(false);save();engine.stop();current=-1;queue.clear();cursor=-1;if(wake.isHeld())wake.release();session.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED,0,0).build());stopForeground(STOP_FOREGROUND_REMOVE);audio.abandonAudioFocusRequest(focus);break;
  case "queueRemove":{int i=a.optInt("index",-1);if(i>=0&&i<queue.size()){queue.remove(i);if(i<cursor)cursor--;else if(i==cursor){if(queue.isEmpty()){command("stop",a);return;}cursor=Math.min(cursor,queue.size()-1);open(queue.get(cursor),null);}}break;}
  case "queueMove":{int i=a.optInt("from",-1),j=a.optInt("to",-1);if(i>=0&&j>=0&&i<queue.size()&&j<queue.size()){long id=queue.remove(i);queue.add(j,id);cursor=queue.indexOf(current);}break;}
  default:engine.command(op,a);if(op.equals("volume")){settings.put("volume",Math.max(0,Math.min(100,a.optInt("value"))));getSharedPreferences("preferences",0).edit().putString("settings",settings.toString()).apply();}if(guardMasked&&op.equals("volume"))protect(true);if(op.equals("pause")||op.equals("toggle"))save();if(op.equals("play")||op.equals("toggle"))notifyMedia(true);
 }push();}catch(Exception e){fail(e.getMessage());}}
 private void next(boolean ended){if(queue.isEmpty())return;if(ended&&repeat.equals("one")){open(current,null);pendingResume=0;return;}int n=shuffle&&queue.size()>1?random.nextInt(queue.size()-1):cursor+1;if(shuffle&&queue.size()>1&&n>=cursor)n++;if(n>=queue.size()){if(repeat.equals("all"))n=0;else{save();engine.stop();push();return;}}open(queue.get(n),null);pendingResume=0;}
 private void save(){if(engine!=null&&current>0&&engine.length()>0)store.progress(current,engine.time(),engine.length());}
 public JSONObject state(){JSONObject s=new JSONObject();try{s.put("current",current);s.put("guard",guard);s.put("guardMasked",guardMasked);s.put("item",store.media(current));s.put("playing",engine!=null&&engine.playing());s.put("time",engine==null?0:engine.time());s.put("duration",engine==null?0:engine.length());s.put("seekable",engine!=null&&engine.seekable());s.put("speed",engine==null?1:engine.get("getRate",1f));s.put("volume",engine==null?80:engine.get("getVolume",80));s.put("status",engine==null?7:engine.state());s.put("error",fault);s.put("queue",new JSONArray(queue));s.put("cursor",cursor);s.put("repeat",repeat);s.put("shuffle",shuffle);s.put("sleepAt",sleepAt);s.put("abA",abA);s.put("abB",abB);if(engine!=null&&current>0)s.put("details",engine.details());s.put("bookmarks",store.bookmarks(current));}catch(Exception ignored){}return s;}
 public void push(){if(listener!=null){JSONObject s=state();try{if(engine!=null){s.put("renderers",engine.rendererList());s.put("renderer",engine.renderer);}}catch(Exception ignored){}listener.state(s);}}
 public void fail(String msg){fault=msg==null?"Playback failed. Try another file or decoder.":msg;if(listener!=null)listener.error(fault);push();}
 private final Runnable tick=new Runnable(){public void run(){try{if(engine!=null&&current>0&&!guardBlocked){int st=engine.state();if(pendingResume>=0&&engine.seekable()&&engine.length()>0){long pos=pendingResume;pendingResume=-1;if(pos<engine.length()-3000&&pos>0){long target=schedule==null?-1:schedule.target(pos,1);if(schedule!=null)protectedSeek(target>=0?target:pos);else engine.seek(pos);}}if(abB>abA&&abA>=0&&engine.time()>=abB){if(schedule!=null){long target=schedule.target(abA,1);protectedSeek(target>=0?target:abA);}else engine.seek(abA);}if(sleepAt>0&&System.currentTimeMillis()>=sleepAt){sleepAt=0;command("pause",new JSONObject());}if(st==6&&lastState!=6){save();next(true);}else if(st==7&&lastState!=7)fail("VLC could not read this media. Check the file, URL, or decoder setting.");lastState=st;
  if(System.currentTimeMillis()-lastSave>5000){save();lastSave=System.currentTimeMillis();}boolean play=engine.playing();if(play&&!wake.isHeld())wake.acquire(600000);if(!play&&wake.isHeld())wake.release();session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_STOP|PlaybackState.ACTION_SEEK_TO|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS).setState(play?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,engine.time(),((Number)engine.get("getRate",1f)).floatValue()).build());if(listener!=null)push();if(st!=lastNotifiedState){lastNotifiedState=st;notifyMedia(play);}}
 }catch(Exception e){fail(e.getMessage());}handler.postDelayed(this,current>0?400:1500);}};
 private int lastNotifiedState=-1;
 private PendingIntent action(String action,int request){return PendingIntent.getService(this,request,new Intent(this,PlaybackService.class).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 private void notifyMedia(boolean foreground){if(current<0)return;JSONObject item=store.media(current);String title=item==null?"VLC Glass":item.optString("title");session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title).putLong(MediaMetadata.METADATA_KEY_DURATION,engine.length()).build());Notification n=new Notification.Builder(this,"playback").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText("VLC Glass").setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).setOngoing(engine.playing()).setVisibility(Notification.VISIBILITY_PUBLIC).addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous,"Previous",action("previous",1)).build()).addAction(new Notification.Action.Builder(engine.playing()?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,engine.playing()?"Pause":"Play",action("toggle",2)).build()).addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next,"Next",action("next",3)).build()).setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1,2)).build();if(foreground)startForeground(1,n);else ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1,n);}
 public void onDestroy(){guardEpoch++;analysis.shutdownNow();if(scanner!=null)new Thread(()->scanner.close(),"guard-close").start();save();handler.removeCallbacksAndMessages(null);unregisterReceiver(noisy);if(engine!=null)engine.release();session.release();audio.abandonAudioFocusRequest(focus);if(wake.isHeld())wake.release();store.close();super.onDestroy();}
}
