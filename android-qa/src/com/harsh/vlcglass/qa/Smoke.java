package com.harsh.vlcglass.qa;
import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.webkit.WebView;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Safe synthetic fixture; no recognition-accuracy or physical-device claim. */
public final class Smoke extends Instrumentation {
 private volatile Activity activity;private volatile Object service;private volatile WebView web;
 private JSONObject report=new JSONObject();
 public void callActivityOnResume(Activity next){super.callActivityOnResume(next);activity=next;try{web=(WebView)field(next,"web");Object current=field(next,"playback");if(current!=null)service=current;}catch(Exception ignored){}}
 private void rotate(int orientation)throws Exception{ui(()->activity.setRequestedOrientation(orientation));for(int i=0;i<5;i++){Thread.sleep(400);dismissTutorial();}}
 public void onCreate(Bundle args){super.onCreate(args);start();}
 private static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 private static void set(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
 private interface Work {void run()throws Exception;}
 private void ui(Work work)throws Exception{AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new Exception(error.get());}
 private String js(String script)throws Exception{AtomicReference<String> value=new AtomicReference<>();CountDownLatch latch=new CountDownLatch(1);ui(()->web.evaluateJavascript(script,v->{value.set(v);latch.countDown();}));if(!latch.await(10,TimeUnit.SECONDS))throw new Exception("JavaScript response timed out");return value.get();}
 private JSONObject state()throws Exception{AtomicReference<JSONObject> value=new AtomicReference<>();ui(()->value.set((JSONObject)service.getClass().getMethod("state").invoke(service)));return value.get();}
 private void verifyRotationPlayback(String label)throws Exception{
  JSONObject before=state();if(!before.optString("error").isEmpty())throw new Exception("Rotation reported "+before.optString("error"));long at=before.optLong("time");
  ui(()->service.getClass().getMethod("command",String.class,JSONObject.class).invoke(service,"play",new JSONObject()));long until=SystemClock.uptimeMillis()+8000;JSONObject now=before;
  while(SystemClock.uptimeMillis()<until){now=state();if(!now.optString("error").isEmpty())throw new Exception("Rotation reported "+now.optString("error"));if(now.optBoolean("playing")&&!now.optBoolean("guardMasked")&&now.optLong("time")>at+300)break;Thread.sleep(30);}
  if(!now.optBoolean("playing")||now.optLong("time")<=at+300)throw new Exception("Playback did not continue after "+label+" rotation");
  AtomicReference<int[]> bounds=new AtomicReference<>();
  ui(()->{service.getClass().getMethod("command",String.class,JSONObject.class).invoke(service,"pause",new JSONObject());android.view.TextureView surface=(android.view.TextureView)field(activity,"video");int[] where=new int[2];surface.getLocationOnScreen(where);bounds.set(new int[]{where[0],where[1],surface.getWidth(),surface.getHeight()});});
  Thread.sleep(400);Bitmap screen=getUiAutomation().takeScreenshot();if(screen==null)throw new Exception("No display capture after "+label+" rotation");int visible=0;int[] r=bounds.get();
  try{for(float fy:new float[]{.3f,.45f,.6f,.75f})for(float fx:new float[]{.2f,.4f,.6f,.8f}){int x=Math.max(0,Math.min(screen.getWidth()-1,r[0]+(int)(r[2]*fx))),y=Math.max(0,Math.min(screen.getHeight()-1,r[1]+(int)(r[3]*fy)));int c=screen.getPixel(x,y);if(Math.abs(android.graphics.Color.red(c)-48)<24&&Math.abs(android.graphics.Color.green(c)-64)<24&&Math.abs(android.graphics.Color.blue(c)-80)<24)visible++;}}finally{screen.recycle();}
  if(visible<4)throw new Exception("The actual display did not show the safe video after "+label+" rotation");
  report.put(label+"_playback",true).put(label+"_video_frame",true);
 }
 private void dismissTutorial(){try{android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root==null)return;for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("Got it")){android.view.accessibility.AccessibilityNodeInfo target=node;for(int i=0;i<3&&target!=null;i++){if(target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))break;target=target.getParent();}}root.recycle();}catch(Exception ignored){}}
 private boolean visibleProbe(Bitmap image,int[] rgb){
  int count=0;for(int y=0;y<image.getHeight();y+=3)for(int x=0;x<image.getWidth();x+=3){int c=image.getPixel(x,y);if(Math.abs(android.graphics.Color.red(c)-rgb[0])<14&&Math.abs(android.graphics.Color.green(c)-rgb[1])<14&&Math.abs(android.graphics.Color.blue(c)-rgb[2])<14&&++count>=8)return true;}return false;
 }
 private void screenshot(String name)throws Exception{
  if(!name.equals("android-error.png")){
   final int[] rgb=name.contains("portrait")?new int[]{18,171,52}:new int[]{162,59,209};
   js("showHUD();(()=>{const old=document.getElementById('qa-visible-probe');if(old)old.remove();const p=document.createElement('div');p.id='qa-visible-probe';p.style.cssText='position:fixed;left:6px;top:6px;width:24px;height:24px;background:rgb("+rgb[0]+","+rgb[1]+","+rgb[2]+");z-index:2147483647;pointer-events:none';document.body.appendChild(p)})();true");
   // Display rotation and WebView repaint are asynchronous. Check the actual
   // screen for this orientation's unique marker, independent of DPI/offsets.
   long until=SystemClock.uptimeMillis()+3000;boolean seen=false;
   while(SystemClock.uptimeMillis()<until){Thread.sleep(100);Bitmap proof=getUiAutomation().takeScreenshot();if(proof==null)continue;try{seen=visibleProbe(proof,rgb);}finally{proof.recycle();}if(seen)break;}
   if(!seen)throw new Exception("The glass UI is not visible on the actual display");
   js("document.getElementById('qa-visible-probe').remove();showHUD();true");Thread.sleep(500);report.put(name.contains("portrait")?"portrait_visible_ui":"landscape_visible_ui",true);
  }
  Bitmap bitmap=getUiAutomation().takeScreenshot();if(bitmap==null)throw new Exception("No screenshot");try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
 }
 private void contract(String orientation)throws Exception{
  String source;try(InputStream in=getTargetContext().getAssets().open("ui/qa-contract.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);source=out.toString("UTF-8");}
  js("window.__qaContract=null;"+source);long until=SystemClock.uptimeMillis()+45000;String response="null";while(SystemClock.uptimeMillis()<until){Thread.sleep(100);response=js("window.__qaContract");if(!"null".equals(response))break;}JSONObject value=new JSONObject(response);report.put(orientation+"_contract",value);
  for(String key:new String[]{"direct_controls","touch_targets","controls_fit","steady_dom","semantic_result_seek","play_pause_control","seek_buttons","speed_control","track_queue_access","volume_control","dialog_focus","keyboard_button","lock_controls","queue_navigation"})if(!value.optBoolean(key))throw new Exception(orientation+" UI contract failed: "+value);
 }
 private void nativeSearchContract(File video)throws Exception{
  Class<?> type=Class.forName("com.harsh.vlcglass.SemanticVideoSearch");Object search=type.getConstructor(Context.class).newInstance(getTargetContext());
  Method clip=type.getDeclaredMethod("clip",String.class,long.class,long.class,File.class,java.util.function.BooleanSupplier.class);clip.setAccessible(true);File part=new File(getTargetContext().getCacheDir(),"qa-search-clip.mp4");
  long[] range=(long[])clip.invoke(search,Uri.fromFile(video).toString(),5000L,13000L,part,(java.util.function.BooleanSupplier)()->false);
  android.media.MediaMetadataRetriever reader=new android.media.MediaMetadataRetriever();long length;try{reader.setDataSource(part.toString());length=Long.parseLong(reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION));if(reader.getFrameAtTime(1000000)==null)throw new Exception("Search clip did not decode");}finally{reader.release();part.delete();}
  if(range[0]>5000||range[1]<12000||length<7000||length>14000)throw new Exception("Search clip timestamps invalid");
  Method normalize=type.getDeclaredMethod("normalized",JSONArray.class);normalize.setAccessible(true);JSONArray values=new JSONArray();for(int i=0;i<768;i++)values.put(i==0?3:0);float[] query=(float[])normalize.invoke(null,values);
  java.nio.ByteBuffer buffer=java.nio.ByteBuffer.allocate(768*4).order(java.nio.ByteOrder.LITTLE_ENDIAN);for(float v:query)buffer.putFloat(v);Method cosine=type.getDeclaredMethod("similarity",float[].class,byte[].class);cosine.setAccessible(true);if(Math.abs((Double)cosine.invoke(null,query,buffer.array())-1)>.0001)throw new Exception("Cosine retrieval is invalid");
  Method body=type.getDeclaredMethod("requestBody",JSONObject.class);body.setAccessible(true);JSONObject request=(JSONObject)body.invoke(null,new JSONObject().put("text","task: search result | query: a cyclist"));if(request.has("taskType")||request.optInt("outputDimensionality")!=768)throw new Exception("Embedding 2 request contract invalid");
  report.put("native_video_chunk",true).put("native_vector_retrieval",true).put("embedding2_request_contract",true);
 }
 public void onStart(){Bundle result=new Bundle();boolean passed=false;
  try{
   report.put("webview",WebView.getCurrentWebViewPackage().versionName).put("api",Build.VERSION.SDK_INT).put("abi",Build.SUPPORTED_ABIS[0]).put("physical_device",false);
   Intent launch=getTargetContext().getPackageManager().getLaunchIntentForPackage("com.harsh.vlcglass");launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);activity=startActivitySync(launch);
   long until=SystemClock.uptimeMillis()+45000;
   while(SystemClock.uptimeMillis()<until){ui(()->{service=field(activity,"playback");web=(WebView)field(activity,"web");});if(service!=null&&"true".equals(js("!!window.GlassApp&&!!document.getElementById('momentQuery')&&document.body.classList.contains('native')")))break;Thread.sleep(100);}
   if(service==null||!"true".equals(js("!!window.GlassApp&&!!document.getElementById('momentQuery')")))throw new Exception("Native UI bridge did not become ready");report.put("ui_ready",true);
   File video=new File(getTargetContext().getCacheDir(),"safe-smoke.mp4");
   try(InputStream in=getContext().getAssets().open("safe-smoke.mp4");OutputStream out=new FileOutputStream(video)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
   nativeSearchContract(video);
   File second=new File(getTargetContext().getCacheDir(),"safe-second.mp4");try(InputStream in=new FileInputStream(video);OutputStream out=new FileOutputStream(second)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
   AtomicReference<Long> id=new AtomicReference<>(),nextId=new AtomicReference<>();ui(()->{Object store=field(service,"store");id.set((Long)store.getClass().getMethod("add",String.class,String.class,String.class,long.class).invoke(store,Uri.fromFile(video).toString(),"Safe playback test","video",video.length()));nextId.set((Long)store.getClass().getMethod("add",String.class,String.class,String.class,long.class).invoke(store,Uri.fromFile(second).toString(),"Second safe video","video",second.length()));activity.getClass().getMethod("library").invoke(activity);});
   js("model.settings.sceneGuard=true;model.settings.resume=false;save();playMedia("+id.get()+",["+id.get()+","+nextId.get()+"]);true");
   JSONObject s=null;until=SystemClock.uptimeMillis()+120000;
   while(SystemClock.uptimeMillis()<until){s=state();JSONObject guard=s.optJSONObject("guard");if(guard!=null&&guard.has("error"))throw new Exception(guard.optString("error"));if(!s.optString("error").isEmpty())throw new Exception(s.optString("error"));if(guard!=null&&guard.optBoolean("complete")&&s.optBoolean("playing")&&s.optLong("time")>300)break;Thread.sleep(50);}
   if(s==null||!s.optBoolean("playing")||!s.optJSONObject("guard").optBoolean("complete"))throw new Exception("Protected native playback did not start: "+s);
   report.put("scan_complete",true).put("native_playback",true);
   final long duration=s.optLong("duration");
   ui(()->{service.getClass().getMethod("command",String.class,JSONObject.class).invoke(service,"pause",new JSONObject());Class<?> timeline=Class.forName("com.harsh.vlcglass.SafetyTimeline");Object schedule=timeline.getConstructor(long[].class,long[].class,long.class).newInstance(new long[]{5000},new long[]{8000},duration);set(service,"schedule",schedule);((JSONObject)field(service,"guard")).put("intervals",new JSONArray().put(new JSONObject().put("start",5000).put("end",8000)));service.getClass().getMethod("command",String.class,JSONObject.class).invoke(service,"seek",new JSONObject().put("time",3500));});
   until=SystemClock.uptimeMillis()+10000;
   while(SystemClock.uptimeMillis()<until){s=state();if(!s.optBoolean("guardMasked")&&s.optLong("time")>=3400&&s.optLong("time")<5000)break;Thread.sleep(50);}
   if(s.optBoolean("guardMasked")||s.optLong("time")<3400||s.optLong("time")>=5000)throw new Exception("Cannot prepare the deterministic skip position: "+s);
   final java.util.concurrent.atomic.AtomicBoolean maskObserved=new java.util.concurrent.atomic.AtomicBoolean(false);
   final Object original=field(service,"listener");Class<?> listener=Class.forName("com.harsh.vlcglass.PlaybackService$Listener");
   Object observed=Proxy.newProxyInstance(listener.getClassLoader(),new Class<?>[]{listener},(proxy,method,args)->{if(method.getName().equals("protection")&&Boolean.TRUE.equals(args[0]))maskObserved.set(true);return method.invoke(original,args);});
   ui(()->{set(service,"listener",observed);service.getClass().getMethod("command",String.class,JSONObject.class).invoke(service,"play",new JSONObject());});
   boolean clear=false;until=SystemClock.uptimeMillis()+20000;
   while(SystemClock.uptimeMillis()<until){s=state();if(maskObserved.get()&&s.optLong("time")>=7900&&!s.optBoolean("guardMasked")){clear=true;break;}Thread.sleep(30);}
   ui(()->set(service,"listener",original));
   if(!clear)throw new Exception("The native skip/mask scheduler did not complete: "+s);report.put("synthetic_skip",true).put("mask_applied",maskObserved.get()).put("clear_position_ms",s.optLong("time"));
   // Exercise real command paths against a synthetic blocked opening.
   ui(()->{service.getClass().getMethod("command",String.class,JSONObject.class).invoke(service,"pause",new JSONObject());Class<?> timeline=Class.forName("com.harsh.vlcglass.SafetyTimeline");set(service,"schedule",timeline.getConstructor(long[].class,long[].class,long.class).newInstance(new long[]{0},new long[]{2000},duration));maskObserved.set(false);set(service,"listener",observed);service.getClass().getMethod("command",String.class,JSONObject.class).invoke(service,"previous",new JSONObject());});
   until=SystemClock.uptimeMillis()+10000;clear=false;
   while(SystemClock.uptimeMillis()<until){s=state();if(maskObserved.get()&&!s.optBoolean("guardMasked")&&s.optLong("time")>=2000){clear=true;break;}Thread.sleep(30);}
   if(!clear)throw new Exception("Previous exposed or failed to skip the protected opening: "+s);report.put("protected_previous",true);
   ui(()->{Object engine=field(service,"engine");engine.getClass().getMethod("stop").invoke(engine);maskObserved.set(false);service.getClass().getMethod("command",String.class,JSONObject.class).invoke(service,"play",new JSONObject());});
   until=SystemClock.uptimeMillis()+10000;clear=false;
   while(SystemClock.uptimeMillis()<until){s=state();if(maskObserved.get()&&!s.optBoolean("guardMasked")&&s.optBoolean("playing")&&s.optLong("time")>=2000){clear=true;break;}Thread.sleep(30);}
   ui(()->set(service,"listener",original));
   if(!clear)throw new Exception("Replay exposed or failed to skip the protected opening: "+s);report.put("protected_replay",true);
   js("host('pause');showHUD();true");dismissTutorial();rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);verifyRotationPlayback("portrait");
   String fits="(()=>{if(!model.player)return false;showHUD();const r=document.getElementById('momentDock').getBoundingClientRect();return r.width>0&&r.left>=0&&r.right<=innerWidth+1&&r.top>=0&&r.bottom<=innerHeight+1&&document.documentElement.scrollWidth<=innerWidth+1;})()";
   if(!"true".equals(js(fits)))throw new Exception("Prompt overflowed the portrait viewport");if(!"true".equals(js("innerHeight>innerWidth")))throw new Exception("The display did not enter portrait orientation");report.put("responsive_prompt",true).put("portrait_layout",new JSONObject(js("(()=>{const r=document.getElementById(\"momentDock\").getBoundingClientRect();return {width:innerWidth,height:innerHeight,prompt_top:r.top,prompt_bottom:r.bottom};})()")));contract("portrait");screenshot("android-portrait.png");
   js("document.getElementById('screenButton').click();true");for(int i=0;i<5;i++){Thread.sleep(400);dismissTutorial();}verifyRotationPlayback("landscape");
   if(!"true".equals(js(fits)))throw new Exception("Prompt overflowed the landscape viewport");if(!"true".equals(js("innerWidth>innerHeight")))throw new Exception("The display did not enter landscape orientation");report.put("landscape_prompt",true).put("landscape_layout",new JSONObject(js("(()=>{const r=document.getElementById(\"momentDock\").getBoundingClientRect();return {width:innerWidth,height:innerHeight,prompt_top:r.top,prompt_bottom:r.bottom};})()")));contract("landscape");screenshot("android-landscape.png");js("document.getElementById('screenButton').click();true");Thread.sleep(1600);if(!"true".equals(js("innerHeight>innerWidth")))throw new Exception("Rotate button did not return to portrait");verifyRotationPlayback("return_portrait");report.put("rotation_control",true);passed=true;
  }catch(Throwable error){try{if(activity!=null)screenshot("android-error.png");}catch(Exception ignored){}try{report.put("error",error.toString());}catch(Exception ignored){}error.printStackTrace();}
  try{report.put("passed",passed);result.putString("stream",report.toString());}catch(Exception ignored){}
  finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
