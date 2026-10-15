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
 private void dismissTutorial(){try{android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root==null)return;for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("Got it")){android.view.accessibility.AccessibilityNodeInfo target=node;for(int i=0;i<3&&target!=null;i++){if(target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))break;target=target.getParent();}}root.recycle();}catch(Exception ignored){}}
 private void screenshot(String name)throws Exception{Bitmap bitmap=getUiAutomation().takeScreenshot();if(bitmap==null)throw new Exception("No screenshot");try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
 public void onStart(){Bundle result=new Bundle();boolean passed=false;
  try{
   report.put("webview",WebView.getCurrentWebViewPackage().versionName).put("api",Build.VERSION.SDK_INT).put("abi",Build.SUPPORTED_ABIS[0]).put("physical_device",false);
   Intent launch=getTargetContext().getPackageManager().getLaunchIntentForPackage("com.harsh.vlcglass");launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);activity=startActivitySync(launch);
   long until=SystemClock.uptimeMillis()+45000;
   while(SystemClock.uptimeMillis()<until){ui(()->{service=field(activity,"playback");web=(WebView)field(activity,"web");});if(service!=null&&"true".equals(js("!!window.GlassApp&&!!document.getElementById('momentQuery')&&document.body.classList.contains('native')")))break;Thread.sleep(100);}
   if(service==null||!"true".equals(js("!!window.GlassApp&&!!document.getElementById('momentQuery')")))throw new Exception("Native UI bridge did not become ready");report.put("ui_ready",true);
   File video=new File(getTargetContext().getCacheDir(),"safe-smoke.mp4");
   try(InputStream in=getContext().getAssets().open("safe-smoke.mp4");OutputStream out=new FileOutputStream(video)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
   AtomicReference<Long> id=new AtomicReference<>();ui(()->{Object store=field(service,"store");id.set((Long)store.getClass().getMethod("add",String.class,String.class,String.class,long.class).invoke(store,Uri.fromFile(video).toString(),"Safe playback test","video",video.length()));activity.getClass().getMethod("library").invoke(activity);});
   js("model.settings.sceneGuard=true;model.settings.resume=false;save();playMedia("+id.get()+",["+id.get()+"]);true");
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
   js("host('pause');showHUD();true");dismissTutorial();rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
   String fits="(()=>{if(!model.player)return false;showHUD();const r=document.getElementById('momentDock').getBoundingClientRect();return r.width>0&&r.left>=0&&r.right<=innerWidth+1&&r.top>=0&&r.bottom<=innerHeight+1&&document.documentElement.scrollWidth<=innerWidth+1;})()";
   if(!"true".equals(js(fits)))throw new Exception("Prompt overflowed the portrait viewport");report.put("responsive_prompt",true).put("portrait_layout",new JSONObject(js("(()=>{const r=document.getElementById(\"momentDock\").getBoundingClientRect();return {width:innerWidth,height:innerHeight,prompt_top:r.top,prompt_bottom:r.bottom};})()")));screenshot("android-portrait.png");
   rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
   if(!"true".equals(js(fits)))throw new Exception("Prompt overflowed the landscape viewport");report.put("landscape_prompt",true).put("landscape_layout",new JSONObject(js("(()=>{const r=document.getElementById(\"momentDock\").getBoundingClientRect();return {width:innerWidth,height:innerHeight,prompt_top:r.top,prompt_bottom:r.bottom};})()")));screenshot("android-landscape.png");passed=true;
  }catch(Throwable error){try{if(activity!=null)screenshot("android-error.png");}catch(Exception ignored){}try{report.put("error",error.toString());}catch(Exception ignored){}error.printStackTrace();}
  try{report.put("passed",passed);result.putString("stream",report.toString());}catch(Exception ignored){}
  finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
