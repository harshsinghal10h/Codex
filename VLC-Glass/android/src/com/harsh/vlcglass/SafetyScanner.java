package com.harsh.vlcglass;

import android.content.Context;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import ai.onnxruntime.*;
import java.io.*;
import java.nio.FloatBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Scans on one worker before playback. Raw frames never leave the device. */
public final class SafetyScanner implements AutoCloseable {
 public interface Progress {void update(String phase,int percent);}
 public static final String POLICY="nudenet-320n-v1/500ms/100ms/0.60-0.35/pad750-500";
 private static final String MODEL_SHA="c15d8273adad2d0a92f014cc69ab2d6c311a06777a55545f2c4eb46f51911f0f";
 private static final int[] EXPLICIT={2,3,4,6,14};
 private final Context context;private OrtEnvironment env;private OrtSession session;private String input;
 private final int[] pixels=new int[320*320];private final float[] values=new float[3*320*320];
 public SafetyScanner(Context c){context=c.getApplicationContext();}
 private static String hex(byte[] b){StringBuilder out=new StringBuilder();for(byte x:b)out.append(String.format(Locale.ROOT,"%02x",x&255));return out.toString();}
 private static String digest(InputStream in,BooleanSupplier cancel)throws Exception{
  MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] block=new byte[1048576];int n;
  while((n=in.read(block))!=-1){if(cancel.getAsBoolean())throw new InterruptedException();d.update(block,0,n);}return hex(d.digest());
 }
 private void load()throws Exception{
  if(session!=null)return;
  File model=new File(context.getFilesDir(),"320n-"+MODEL_SHA.substring(0,12)+".onnx");
  if(!model.exists()){
   File tmp=new File(model+".tmp");
   try(InputStream in=context.getAssets().open("models/320n.onnx");OutputStream out=new FileOutputStream(tmp)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
   if(!tmp.renameTo(model))throw new IOException("Cannot prepare the bundled detector");
  }
  try(InputStream in=new FileInputStream(model)){if(!MODEL_SHA.equals(digest(in,()->false)))throw new IOException("The detector integrity check failed");}
  env=OrtEnvironment.getEnvironment();
  try(OrtSession.SessionOptions o=new OrtSession.SessionOptions()){
   o.setIntraOpNumThreads(2);o.setInterOpNumThreads(1);o.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
   session=env.createSession(model.getAbsolutePath(),o);input=session.getInputNames().iterator().next();
  }
  try(OnnxTensor tensor=OnnxTensor.createTensor(env,FloatBuffer.wrap(values),new long[]{1,3,320,320});OrtSession.Result out=session.run(Collections.singletonMap(input,tensor))){}
 }
 private float score(MediaMetadataRetriever video,long ms)throws Exception{
  Bitmap frame=video.getScaledFrameAtTime(ms*1000,MediaMetadataRetriever.OPTION_CLOSEST,320,320);
  if(frame==null)throw new IOException("A frame could not be read at "+ms+" ms. The scan is incomplete.");
  Bitmap square=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888);
  try{
   Canvas canvas=new Canvas(square);canvas.drawColor(Color.BLACK);
   float scale=320f/Math.max(frame.getWidth(),frame.getHeight());
   canvas.drawBitmap(frame,null,new RectF(0,0,frame.getWidth()*scale,frame.getHeight()*scale),new Paint(Paint.FILTER_BITMAP_FLAG));
   square.getPixels(pixels,0,320,0,0,320,320);
   for(int i=0;i<pixels.length;i++){int p=pixels[i];values[i]=(p&255)/255f;values[pixels.length+i]=((p>>8)&255)/255f;values[2*pixels.length+i]=((p>>16)&255)/255f;}
   try(OnnxTensor tensor=OnnxTensor.createTensor(env,FloatBuffer.wrap(values),new long[]{1,3,320,320});OrtSession.Result result=session.run(Collections.singletonMap(input,tensor))){
    float[][][] out=(float[][][])result.get(0).getValue();
    if(out.length!=1||out[0].length!=22)throw new IOException("Unexpected detector output");
    float max=0;for(int label:EXPLICIT)for(float v:out[0][4+label]){if(Float.isNaN(v)||Float.isInfinite(v))throw new IOException("Invalid detector score");max=Math.max(max,v);}return max;
   }
  }finally{frame.recycle();square.recycle();}
 }
 public synchronized JSONObject scan(String uri,Progress progress,BooleanSupplier cancel)throws Exception{
  if(!uri.startsWith("content:")&&!uri.startsWith("file:"))throw new IOException("Scene guard needs a local seekable video. Network streams are not protected.");
  progress.update("hashing",0);String hash;
  try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(uri))){if(in==null)throw new IOException("Cannot open this video");hash=digest(in,cancel);}
  String key=hex(MessageDigest.getInstance("SHA-256").digest((hash+POLICY+MODEL_SHA).getBytes("UTF-8")));
  File dir=new File(context.getFilesDir(),"scene-guard");dir.mkdirs();File cached=new File(dir,key+".json");
  if(cached.exists()&&cached.length()<8000000){
   try{JSONObject map=new JSONObject(read(cached));if(hash.equals(map.optString("media_sha256"))&&POLICY.equals(map.optString("policy"))&&MODEL_SHA.equals(map.optString("model_sha256"))){timeline(map);progress.update("ready",100);return map;}}catch(Exception ignored){}
  }
  load();MediaMetadataRetriever video=new MediaMetadataRetriever();
  try{
   video.setDataSource(context,Uri.parse(uri));long duration=Long.parseLong(video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
   if(duration<=0)throw new IOException("This video has no finite duration");
   ArrayList<long[]> spans=new ArrayList<>();ArrayList<Float> scores=new ArrayList<>();long start=-1,last=0;int low=0,reported=-1;float peak=0;
   for(long t=0;t<duration;t+=500){
    if(cancel.getAsBoolean())throw new InterruptedException();float s=score(video,t);
    if(s>=.60f||(start>=0&&s>=.35f)){if(start<0)start=Math.max(0,t-500);last=t;low=0;peak=Math.max(peak,s);}
    else if(start>=0&&++low>=2){spans.add(new long[]{Math.max(0,start-750),Math.min(duration,last+1000)});scores.add(peak);start=-1;peak=0;}
    int percent=(int)Math.min(99,t*100/duration);if(percent!=reported){reported=percent;progress.update("scanning",percent);}
   }
   if(start>=0){spans.add(new long[]{Math.max(0,start-750),duration});scores.add(peak);}
   for(long[] span:spans){long lo=Math.max(0,span[0]-500),hi=Math.min(duration,span[1]+500),first=-1,lastFound=-1;
    for(long[] window:new long[][]{{lo,Math.min(hi,lo+2000)},{Math.max(lo,hi-2000),hi}})for(long t=window[0];t<window[1];t+=100){
     if(cancel.getAsBoolean())throw new InterruptedException();if(score(video,t)>=.35f){if(first<0||t<first)first=t;lastFound=Math.max(lastFound,t);}}
    if(first>=0){span[0]=Math.min(span[0],Math.max(0,first-750));span[1]=Math.max(span[1],Math.min(duration,lastFound+600));}
   }
   JSONArray intervals=new JSONArray();JSONObject previous=null;
   for(int i=0;i<spans.size();i++){long[] span=spans.get(i);if(previous!=null&&span[0]<=previous.getLong("end")+250){previous.put("end",Math.max(span[1],previous.getLong("end")));previous.put("score",Math.max(scores.get(i),previous.getDouble("score")));}else{previous=new JSONObject().put("start",span[0]).put("end",span[1]).put("score",scores.get(i));intervals.put(previous);}}
   JSONObject map=new JSONObject().put("schema",1).put("policy",POLICY).put("complete",true).put("duration",duration).put("media_sha256",hash).put("model_sha256",MODEL_SHA).put("sample_ms",500).put("boundary_ms",100).put("intervals",intervals);
   timeline(map);File tmp=new File(cached+".tmp");try(OutputStream out=new FileOutputStream(tmp)){out.write(map.toString().getBytes("UTF-8"));}if(!tmp.renameTo(cached))throw new IOException("Cannot save the scan map");progress.update("ready",100);return map;
  }finally{try{video.release();}catch(Exception ignored){}}
 }
 public static SafetyTimeline timeline(JSONObject map)throws Exception{
  if(!map.optBoolean("complete"))throw new IOException("Incomplete scan map");JSONArray spans=map.getJSONArray("intervals");long[] starts=new long[spans.length()],ends=new long[spans.length()];
  for(int i=0;i<spans.length();i++){starts[i]=spans.getJSONObject(i).getLong("start");ends[i]=spans.getJSONObject(i).getLong("end");}
  return new SafetyTimeline(starts,ends,map.getLong("duration"));
 }
 private static String read(File f)throws Exception{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
 public synchronized void close(){if(session!=null)try{session.close();}catch(Exception ignored){}session=null;}
}
