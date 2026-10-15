package com.harsh.vlcglass;

import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.view.TextureView;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Public LibVLC calls; implementation classes remain unmodified in vendor DEX. */
public final class VlcEngine {
 public Object lib,player;private AssetFileDescriptor descriptor;private Object eq;private JSONObject cachedDetails;private long detailsAt;private volatile int videoWidth=0,videoHeight=0;
 private final ArrayList<Object> discoverers=new ArrayList<>();private final LinkedHashMap<Integer,Object> renderers=new LinkedHashMap<>();private int rendererSequence=0;public int renderer=-1;private android.net.wifi.WifiManager.MulticastLock multicast;
 public VlcEngine(Context c)throws Exception{
  Class<?> lc=Class.forName("org.videolan.libvlc.LibVLC");
  lib=lc.getConstructor(Context.class,List.class).newInstance(c,new ArrayList<>(Arrays.asList("--no-video-title-show","--audio-time-stretch","--no-sout-keep")));
  player=construct("org.videolan.libvlc.MediaPlayer",lib);
 }
 static Object construct(String name,Object...args)throws Exception{for(Constructor<?> c:Class.forName(name).getConstructors())if(matches(c.getParameterTypes(),args))return c.newInstance(args);throw new NoSuchMethodException(name+" constructor");}
 static boolean matches(Class<?>[] types,Object[] args){if(types.length!=args.length)return false;for(int i=0;i<types.length;i++){Class<?> t=types[i];if(args[i]==null){if(t.isPrimitive())return false;continue;}if(t.isPrimitive())t=t==int.class?Integer.class:t==long.class?Long.class:t==float.class?Float.class:t==boolean.class?Boolean.class:t==double.class?Double.class:t;if(!t.isInstance(args[i]))return false;}return true;}
 static Object call(Object o,String name,Object...args)throws Exception{
  Class<?> k=o instanceof Class?(Class<?>)o:o.getClass();Class<?>[] types=new Class<?>[args.length];
  for(int i=0;i<args.length;i++){Object a=args[i];types[i]=a instanceof Integer?int.class:a instanceof Long?long.class:a instanceof Float?float.class:a instanceof Boolean?boolean.class:a instanceof Double?double.class:a==null?String.class:a.getClass();}
  if(name.equals("setMedia"))types[0]=Class.forName("org.videolan.libvlc.interfaces.IMedia");
  if(name.equals("setEqualizer"))types[0]=Class.forName("org.videolan.libvlc.MediaPlayer$Equalizer");
  if(name.equals("setVideoView")||name.equals("setSubtitlesView"))types[0]=TextureView.class;
  if(name.equals("attachViews")&&args.length==1)types[0]=Class.forName("org.videolan.libvlc.interfaces.IVLCVout$OnNewVideoLayoutListener");
  if(name.equals("list")&&args.length==1)types[0]=Class.forName("org.videolan.libvlc.interfaces.ILibVLC");
  if(name.equals("setRenderer"))types[0]=Class.forName("org.videolan.libvlc.RendererItem");
  if(name.equals("setEventListener"))types[0]=Class.forName("org.videolan.libvlc.RendererDiscoverer$EventListener");
  for(Class<?> c=k;c!=null;c=c.getSuperclass())try{Method m=c.getDeclaredMethod(name,types);m.setAccessible(true);try{return m.invoke(o instanceof Class?null:o,args);}catch(InvocationTargetException e){Throwable t=e.getCause();if(t instanceof Exception)throw (Exception)t;throw e;}}catch(NoSuchMethodException ignored){}
  throw new NoSuchMethodException(k.getName()+"."+name);
 }
 public Object get(String name,Object fallback,Object...args){try{return call(player,name,args);}catch(Exception e){return fallback;}}
 public long time(){return ((Number)get("getTime",0L)).longValue();}
 public long length(){return ((Number)get("getLength",0L)).longValue();}
 public int state(){return ((Number)get("getPlayerState",0)).intValue();}
 public boolean playing(){return (Boolean)get("isPlaying",false);}
 public boolean seekable(){return (Boolean)get("isSeekable",false);}
 public void play(Context c,String uri,JSONObject settings)throws Exception{
  stop();cachedDetails=null;detailsAt=0;videoWidth=videoHeight=0;Object media;
  Uri u=Uri.parse(uri);
  if("content".equals(u.getScheme())){descriptor=c.getContentResolver().openAssetFileDescriptor(u,"r");if(descriptor==null)throw new Exception("The document is no longer available. Open it again.");media=construct("org.videolan.libvlc.Media",lib,descriptor);}else media=construct("org.videolan.libvlc.Media",lib,u);
  try{call(media,"setHWDecoderEnabled",!"software".equals(settings.optString("decoder")),false);call(media,"addOption",":network-caching="+settings.optInt("networkCache",1000));call(media,"addOption",":freetype-rel-fontsize="+Math.max(10,Math.min(32,(int)Math.round(1600/settings.optDouble("captionSize",100)))));call(player,"setMedia",media);}finally{call(media,"release");}
  call(player,"setRate",(float)settings.optDouble("speed",1));call(player,"setVolume",settings.optInt("volume",80));call(player,"play");
 }
 public void seek(long ms)throws Exception{if(!seekable())throw new Exception("This stream does not support seeking.");long end=length();call(player,"setTime",Math.max(0,end>0?Math.min(ms,end):ms));}
 public void attach(TextureView view,TextureView subtitles,int width,int height)throws Exception{Object out=call(player,"getVLCVout");call(out,"detachViews");call(out,"setVideoView",view);call(out,"setSubtitlesView",subtitles);call(out,"setWindowSize",Math.max(1,width),Math.max(1,height));Class<?> listener=Class.forName("org.videolan.libvlc.interfaces.IVLCVout$OnNewVideoLayoutListener");Object callback=Proxy.newProxyInstance(listener.getClassLoader(),new Class<?>[]{listener},(p,m,args)->{if(m.getName().equals("onNewVideoLayout")&&args.length>=7){int vw=((Number)args[3]).intValue(),vh=((Number)args[4]).intValue(),sn=((Number)args[5]).intValue(),sd=((Number)args[6]).intValue();if(vw>0&&vh>0){videoWidth=vw;videoHeight=vh;detailsAt=0;float ratio=vw*(sn>0&&sd>0?(float)sn/sd:1f)/vh;view.post(()->{android.view.View parent=(android.view.View)view.getParent();int pw=parent.getWidth(),ph=parent.getHeight();int w=pw,h=(int)(pw/ratio);if(h>ph){h=ph;w=(int)(ph*ratio);}android.widget.FrameLayout.LayoutParams lp=new android.widget.FrameLayout.LayoutParams(w,h,android.view.Gravity.CENTER);view.setLayoutParams(lp);subtitles.setLayoutParams(new android.widget.FrameLayout.LayoutParams(w,h,android.view.Gravity.CENTER));window(w,h);});}}return null;});call(out,"attachViews",callback);}
 public void window(int w,int h){try{call(call(player,"getVLCVout"),"setWindowSize",Math.max(1,w),Math.max(1,h));}catch(Exception ignored){}}
 public void detach(){try{call(call(player,"getVLCVout"),"detachViews");}catch(Exception ignored){}}
 public void stop(){try{call(player,"stop");}catch(Exception ignored){}if(descriptor!=null){try{descriptor.close();}catch(Exception ignored){}descriptor=null;}}
 public void release(){stop();detach();stopDiscovery();for(Object item:renderers.values())try{call(item,"release");}catch(Exception ignored){}renderers.clear();try{call(player,"release");call(lib,"release");}catch(Exception ignored){}}
 public void discover(Context context,Runnable changed)throws Exception{
  stopDiscovery();android.net.wifi.WifiManager wifi=(android.net.wifi.WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);if(wifi!=null){multicast=wifi.createMulticastLock("VLCGlass:cast");multicast.setReferenceCounted(false);multicast.acquire();}
  Class<?> cls=Class.forName("org.videolan.libvlc.RendererDiscoverer"),listener=Class.forName("org.videolan.libvlc.RendererDiscoverer$EventListener");Object descriptions=call(cls,"list",lib);
  if(descriptions==null||Array.getLength(descriptions)==0){stopDiscovery();throw new Exception("This VLC engine has no available casting discoverer.");}
  for(int i=0;i<Array.getLength(descriptions);i++){String name=String.valueOf(field(Array.get(descriptions,i),"name"));Object d=construct(cls.getName(),lib,name);Object callback=Proxy.newProxyInstance(listener.getClassLoader(),new Class<?>[]{listener},(proxy,method,args)->{
   if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);if(method.getName().equals("equals"))return proxy==args[0];if(method.getName().equals("toString"))return "VLC Glass renderer listener";
   if(method.getName().equals("onEvent")&&args!=null&&args.length==1){Object event=args[0],item=call(event,"getItem");int type=((Number)field(event,"type")).intValue();if(type==0x502&&item!=null){boolean exists=false;for(Object saved:renderers.values())if(saved.equals(item))exists=true;if(!exists){call(item,"retain");renderers.put(++rendererSequence,item);changed.run();}}else if(type==0x503&&item!=null){Iterator<Map.Entry<Integer,Object>> it=renderers.entrySet().iterator();while(it.hasNext()){Map.Entry<Integer,Object> entry=it.next();if(entry.getValue().equals(item)){if(renderer==entry.getKey()){call(player,"setRenderer",(Object)null);renderer=-1;}call(entry.getValue(),"release");it.remove();}}changed.run();}}return null;});
   call(d,"setEventListener",callback);if(Boolean.TRUE.equals(call(d,"start")))discoverers.add(d);else call(d,"release");
  }
 }
 public void stopDiscovery(){for(Object d:discoverers)try{call(d,"stop");call(d,"release");}catch(Exception ignored){}discoverers.clear();if(multicast!=null&&multicast.isHeld())multicast.release();multicast=null;}
 public JSONArray rendererList(){JSONArray list=new JSONArray();for(Map.Entry<Integer,Object> e:renderers.entrySet())try{list.put(new JSONObject().put("id",e.getKey()).put("name",field(e.getValue(),"name")).put("type",field(e.getValue(),"type")));}catch(Exception ignored){}return list;}
 public void renderer(int id)throws Exception{if(id>=0&&!renderers.containsKey(id))throw new Exception("This receiver is no longer available. Scan again.");call(player,"setRenderer",id<0?null:renderers.get(id));renderer=id;}
 public void command(String op,JSONObject a)throws Exception{
  detailsAt=0;
  switch(op){case "toggle":call(player,playing()?"pause":"play");break;case "pause":call(player,"pause");break;case "play":call(player,"play");break;
   case "seek":seek(a.optLong("time"));break;case "skip":seek(time()+a.optLong("seconds",10)*1000);break;
   case "speed":call(player,"setRate",(float)a.optDouble("value",1));break;case "volume":call(player,"setVolume",Math.max(0,Math.min(100,a.optInt("value"))));break;
   case "videoTrack":call(player,"setVideoTrack",a.optInt("id"));break;case "audioTrack":call(player,"setAudioTrack",a.optInt("id"));break;case "subtitleTrack":call(player,"setSpuTrack",a.optInt("id",-1));break;
   case "audioDelay":call(player,"setAudioDelay",a.optLong("ms")*1000);break;case "subtitleDelay":call(player,"setSpuDelay",a.optLong("ms")*1000);break;
   case "chapter":call(player,"setChapter",a.optInt("index"));break;case "aspect":call(player,"setAspectRatio",a.optString("value").equals("auto")?null:a.optString("value"));break;
   case "subtitleFile":call(player,"addSlave",0,Uri.parse(a.getString("uri")),true);break;
   case "equalizer":setEqualizer(a);break;
  }
 }
 public void setEqualizer(JSONObject a)throws Exception{
  if(!a.optBoolean("enabled",true)){call(player,"setEqualizer",(Object)null);return;}
  Class<?> cls=Class.forName("org.videolan.libvlc.MediaPlayer$Equalizer");
  if(eq==null)eq=call(cls,"create");call(eq,"setPreAmp",(float)a.optDouble("preamp",0));JSONArray bands=a.optJSONArray("bands");if(bands!=null)for(int i=0;i<Math.min(bands.length(),10);i++)call(eq,"setAmp",i,(float)bands.optDouble(i));
  call(player,"setEqualizer",eq);
 }
 private JSONArray tracks(String name){JSONArray a=new JSONArray();Object ts=get(name,null);if(ts==null)return a;for(int i=0;i<Array.getLength(ts);i++)try{Object t=Array.get(ts,i);JSONObject j=new JSONObject();j.put("id",field(t,"id"));j.put("name",field(t,"name"));a.put(j);}catch(Exception ignored){}return a;}
 static Object field(Object o,String key)throws Exception{Field f=o.getClass().getField(key);f.setAccessible(true);return f.get(o);}
 public JSONObject details(){if(cachedDetails!=null&&android.os.SystemClock.uptimeMillis()-detailsAt<2000)return cachedDetails;JSONObject d=new JSONObject();try{d.put("videoWidth",videoWidth);d.put("videoHeight",videoHeight);d.put("videoTracks",tracks("getVideoTracks"));d.put("videoTrack",get("getVideoTrack",-1));d.put("audioTracks",tracks("getAudioTracks"));d.put("subtitleTracks",tracks("getSpuTracks"));d.put("audioTrack",get("getAudioTrack",-1));d.put("subtitleTrack",get("getSpuTrack",-1));d.put("audioDelay",((Number)get("getAudioDelay",0L)).longValue()/1000);d.put("subtitleDelay",((Number)get("getSpuDelay",0L)).longValue()/1000);JSONArray chapters=new JSONArray();Object ch=get("getChapters",null,((Number)get("getTitle",0)).intValue());if(ch!=null)for(int i=0;i<Array.getLength(ch);i++){Object v=Array.get(ch,i);JSONObject j=new JSONObject();j.put("index",i);j.put("name",field(v,"name"));j.put("time",field(v,"timeOffset"));chapters.put(j);}d.put("chapters",chapters);d.put("version",call(Class.forName("org.videolan.libvlc.LibVLC"),"version"));}catch(Exception e){try{d.put("detailError",e.getMessage());}catch(Exception ignored){}}cachedDetails=d;detailsAt=android.os.SystemClock.uptimeMillis();return d;}
}
