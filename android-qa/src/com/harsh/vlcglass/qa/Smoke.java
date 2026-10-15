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
 private Activity activity;private Object service;private WebView web;
 private JSONObject report=new JSONObject();
 public void onCreate(Bundle args){super.onCreate(args);start();}
 private static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 private static void set(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
 private interface Work {void run()throws Exception;}
 private void ui(Work work)throws Exception{AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new Exception(error.get());}
 private String js(String script)throws Exception{AtomicReference<String> value=new AtomicReference<>();CountDownLatch latch=new CountDownLatch(1);ui(()->web.evaluateJavascript(script,v->{value.set(v);latch.countDown();}));if(!latch.await(10,TimeUnit.SECONDS))throw new Exception("JavaScript response timed out");return value.get();}
 private JSONObject state()throws Exception{AtomicReference<JSONObject> value=new AtomicReference<>();ui(()->value.set((JSONObject)service.getClass().getMethod("state").invoke(service)));return value.get();}
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
   final long duration=s.optLong("duration");ui(()->{Class<?> timeline=Class.forName("com.harsh.vlcglass.SafetyTimeline");Object schedule=timeline.getConstructor(long[].class,long[].class,long.class).newInstance(new long[]{1400},new long[]{3500},duration);set(service,"schedule",schedule);((JSONObject)field(service,"guard")).put("intervals",new JSONArray().put(new JSONObject().put("start",1400).put("end",3500)));});
   boolean skipped=false,clear=false;long previous=s.optLong("time");until=SystemClock.uptimeMillis()+18000;
   while(SystemClock.uptimeMillis()<until){s=state();long now=s.optLong("time");if(previous<1800&&now>=3400)skipped=true;if(skipped&&!s.optBoolean("guardMasked")){clear=true;break;}previous=now;Thread.sleep(30);}
   if(!clear)throw new Exception("The native skip/mask scheduler did not complete: "+s);report.put("synthetic_skip",true).put("clear_position_ms",s.optLong("time"));
   js("host('pause');showHUD();true");Thread.sleep(500);
   String fits="(()=>{showHUD();const r=document.getElementById('momentDock').getBoundingClientRect();return r.width>0&&r.left>=0&&r.right<=innerWidth+1&&document.documentElement.scrollWidth<=innerWidth+1;})()";
   if(!"true".equals(js(fits)))throw new Exception("Prompt overflowed the portrait viewport");report.put("responsive_prompt",true);screenshot("android-portrait.png");
   ui(()->activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));Thread.sleep(1500);
   if(!"true".equals(js(fits)))throw new Exception("Prompt overflowed the landscape viewport");report.put("landscape_prompt",true);screenshot("android-landscape.png");passed=true;
  }catch(Throwable error){try{report.put("error",error.toString());}catch(Exception ignored){}error.printStackTrace();}
  try{report.put("passed",passed);result.putString("stream",report.toString());}catch(Exception ignored){}
  finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
