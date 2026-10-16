package com.harsh.vlcglass;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.media.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;

/** SentrySearch's video-to-vector / text-to-vector cosine retrieval on Android.
 * Native remuxing keeps actual video input (no captions or filename matching).
 * Requests and hashing run only on the analysis worker; vectors stay on disk.
 */
public final class SemanticVideoSearch {
 static final String MODEL="gemini-embedding-2",POLICY="native-video-8s-overlap2-prefix-v1";
 static final int DIMS=768,MAX_CLIP=12*1024*1024;
 private final Context context;private final CredentialStore secrets;private final SQLiteDatabase db;
 private final Map<Long,String> active=new HashMap<>();private long lastRequest=0;
 public SemanticVideoSearch(Context c){context=c.getApplicationContext();secrets=new CredentialStore(c);
  db=new SQLiteOpenHelper(context,"semantic-search.db",null,1){public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE videos (identity TEXT PRIMARY KEY,duration INTEGER,complete INTEGER DEFAULT 0)");d.execSQL("CREATE TABLE chunks (identity TEXT,window INTEGER,start INTEGER,end INTEGER,vector BLOB,PRIMARY KEY(identity,window))");}public void onUpgrade(SQLiteDatabase d,int a,int b){}}.getWritableDatabase();
 }
 private static void cancelled(BooleanSupplier cancel)throws InterruptedException{if(cancel.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedException("Search preparation cancelled.");}
 private String identity(String uri,BooleanSupplier cancel)throws Exception{MessageDigest h=MessageDigest.getInstance("SHA-256");try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(uri))){if(in==null)throw new IOException("Cannot read this video.");byte[] block=new byte[1024*1024];int n;while((n=in.read(block))!=-1){cancelled(cancel);h.update(block,0,n);}}StringBuilder s=new StringBuilder();for(byte b:h.digest())s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s+":"+MODEL+":"+POLICY;}
 private static byte[] vectorBytes(float[] v){ByteBuffer b=ByteBuffer.allocate(v.length*4).order(ByteOrder.LITTLE_ENDIAN);for(float f:v)b.putFloat(f);return b.array();}
 static float[] normalized(JSONArray values)throws Exception{if(values.length()!=DIMS)throw new IOException("The embedding model returned incompatible dimensions.");float[] out=new float[DIMS];double sum=0;for(int i=0;i<DIMS;i++){double v=values.getDouble(i);if(!Double.isFinite(v))throw new IOException("Invalid embedding vector.");out[i]=(float)v;sum+=v*v;}if(sum<1e-20)throw new IOException("Empty embedding vector.");double norm=Math.sqrt(sum);for(int i=0;i<DIMS;i++)out[i]/=norm;return out;}
 static double similarity(float[] query,byte[] blob)throws Exception{if(blob.length!=DIMS*4)throw new IOException("The saved search index is invalid. Prepare the video again.");ByteBuffer b=ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);double score=0;for(float v:query)score+=v*b.getFloat();return Math.max(-1,Math.min(1,score));}
 static JSONObject requestBody(JSONObject part)throws Exception{return new JSONObject().put("model","models/"+MODEL).put("outputDimensionality",DIMS).put("content",new JSONObject().put("parts",new JSONArray().put(part)));}
 private float[] embed(JSONObject part,boolean query,BooleanSupplier cancel)throws Exception{
  String key=secrets.get("gemini");if(key.isEmpty())throw new IOException("Add your Gemini API key in video search settings first.");
  JSONObject body=requestBody(part);
  byte[] data=body.toString().getBytes("UTF-8");
  for(int attempt=0;attempt<4;attempt++){
   cancelled(cancel);long wait=1200-(android.os.SystemClock.elapsedRealtime()-lastRequest);if(wait>0)Thread.sleep(wait);lastRequest=android.os.SystemClock.elapsedRealtime();
   HttpURLConnection c=(HttpURLConnection)new URL("https://generativelanguage.googleapis.com/v1beta/models/"+MODEL+":embedContent").openConnection();
   c.setConnectTimeout(15000);c.setReadTimeout(60000);c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setDoOutput(true);c.setFixedLengthStreamingMode(data.length);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("x-goog-api-key",key);
   try{try(OutputStream out=c.getOutputStream()){out.write(data);}int status=c.getResponseCode();
    if((status==429||status>=500)&&attempt<3){for(int second=0;second<(2<<attempt);second++){cancelled(cancel);Thread.sleep(1000);}continue;}
    if(status<200||status>=300)throw new IOException(status==401||status==403?"Gemini rejected the API key. Check access to Gemini Embedding 2.":status==429?"Gemini quota reached. Your completed chunks are saved; retry later.":"Gemini video embedding failed (HTTP "+status+"). Check model access or use the companion for this video.");
    try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1){if(out.size()+n>1000000)throw new IOException("Embedding response too large.");out.write(block,0,n);}return normalized(new JSONObject(out.toString("UTF-8")).getJSONObject("embedding").getJSONArray("values"));}
   }finally{c.disconnect();}
  }throw new IOException("Video embedding could not finish.");
 }
 /** Remux a decodable bounded clip and return its actual keyframe start/end. */
 long[] clip(String uri,long start,long end,File target,BooleanSupplier cancel)throws Exception{
  MediaExtractor extractor=new MediaExtractor();MediaMuxer muxer=null;boolean started=false;
  try{extractor.setDataSource(context,Uri.parse(uri),null);int track=-1;MediaFormat format=null;for(int i=0;i<extractor.getTrackCount();i++){MediaFormat f=extractor.getTrackFormat(i);if(f.getString(MediaFormat.KEY_MIME).startsWith("video/")){track=i;format=f;break;}}if(track<0)throw new IOException("This file has no supported video track.");
   extractor.selectTrack(track);extractor.seekTo(start*1000,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);long first=extractor.getSampleTime();if(first<0)throw new IOException("Video chunk could not be decoded.");
   if(start*1000-first>10000000)throw new IOException("This video has very sparse keyframes. Use companion search for accurate chunking.");
   muxer=new MediaMuxer(target.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);int output=muxer.addTrack(format);muxer.start();started=true;
   ByteBuffer buffer=ByteBuffer.allocateDirect(4*1024*1024);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();int total=0,count=0;long last=first;
   while(extractor.getSampleTime()>=0&&extractor.getSampleTime()<end*1000){cancelled(cancel);long sampleSize=extractor.getSampleSize();if(sampleSize>buffer.capacity())throw new IOException("Video frames are too large. Use companion search to preprocess this video.");buffer.clear();int n=extractor.readSampleData(buffer,0);if(n<0)break;total+=n;if(total>MAX_CLIP)throw new IOException("This video needs compression for search. Choose desktop companion in search settings.");
    last=extractor.getSampleTime();info.set(0,n,last-first,(extractor.getSampleFlags()&MediaExtractor.SAMPLE_FLAG_SYNC)!=0?MediaCodec.BUFFER_FLAG_KEY_FRAME:0);muxer.writeSampleData(output,buffer,info);count++;extractor.advance();
   }
   if(count==0)throw new IOException("Empty video chunk.");return new long[]{first/1000,Math.max(first/1000+1,Math.min(end,last/1000+40))};
  }finally{extractor.release();if(muxer!=null){try{if(started)muxer.stop();}finally{muxer.release();}}}
 }
 public void index(long media,JSONObject item,boolean consent,SceneSearchClient.Callback callback,BooleanSupplier cancel)throws Exception{
  if(!consent)throw new IOException("Preparing search requires consent to send this selected video's chunks to Google.");
  String uri=item.optString("uri");if(!uri.startsWith("content:"))throw new IOException("Choose a local video through Open media first.");
  callback.send("sceneIndex",new JSONObject().put("media",media).put("phase","hashing").put("progress",0));String id=identity(uri,cancel);
  try(Cursor c=db.rawQuery("SELECT complete FROM videos WHERE identity=?",new String[]{id})){if(c.moveToFirst()&&c.getInt(0)==1){active.put(media,id);callback.send("sceneIndex",new JSONObject().put("media",media).put("phase","indexed").put("progress",100).put("cached",true));return;}}
  if(secrets.get("gemini").isEmpty())throw new IOException("Add your Gemini API key in video search settings first.");
  MediaMetadataRetriever retriever=new MediaMetadataRetriever();long duration;try{retriever.setDataSource(context,Uri.parse(uri));duration=Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));}finally{retriever.release();}
  if(duration<=0)throw new IOException("Choose a finite, seekable local video.");ContentValues video=new ContentValues();video.put("identity",id);video.put("duration",duration);video.put("complete",0);db.insertWithOnConflict("videos",null,video,SQLiteDatabase.CONFLICT_IGNORE);
  File temporary=File.createTempFile("search-chunk-",".mp4",context.getCacheDir());
  try{for(long start=0;start<duration;start+=6000){cancelled(cancel);boolean saved;try(Cursor c=db.rawQuery("SELECT 1 FROM chunks WHERE identity=? AND window=?",new String[]{id,String.valueOf(start)})){saved=c.moveToFirst();}
    if(!saved){long end=Math.min(duration,start+8000);long[] range=clip(uri,start,end,temporary,cancel);byte[] bytes=java.nio.file.Files.readAllBytes(temporary.toPath());if(bytes.length>MAX_CLIP+1024*1024)throw new IOException("Video chunk too large; use companion search.");
     JSONObject part=new JSONObject().put("inlineData",new JSONObject().put("mimeType","video/mp4").put("data",Base64.encodeToString(bytes,Base64.NO_WRAP)));
     float[] vector=embed(part,false,cancel);ContentValues chunk=new ContentValues();chunk.put("identity",id);chunk.put("window",start);chunk.put("start",range[0]);chunk.put("end",range[1]);chunk.put("vector",vectorBytes(vector));db.insertWithOnConflict("chunks",null,chunk,SQLiteDatabase.CONFLICT_REPLACE);
    }
    callback.send("sceneIndex",new JSONObject().put("media",media).put("phase","indexing").put("progress",Math.min(99,Math.round((start+6000)*100f/duration))));
   }
   cancelled(cancel);ContentValues complete=new ContentValues();complete.put("complete",1);db.update("videos",complete,"identity=?",new String[]{id});active.put(media,id);
   callback.send("sceneIndex",new JSONObject().put("media",media).put("phase","indexed").put("progress",100));
  }finally{temporary.delete();}
 }
 public JSONObject search(long media,JSONObject item,JSONObject args,BooleanSupplier cancel)throws Exception{
  String query=args.optString("query").trim();if(query.isEmpty()||query.length()>1000)throw new IOException("Describe a scene in 1–1000 characters.");String id=active.get(media);if(id==null){id=identity(item.optString("uri"),cancel);try(Cursor c=db.rawQuery("SELECT complete FROM videos WHERE identity=?",new String[]{id})){if(!c.moveToFirst()||c.getInt(0)!=1)throw new IOException("Prepare this video for semantic search first.");}active.put(media,id);}
  float[] vector=embed(new JSONObject().put("text","task: search result | query: "+query),true,cancel);ArrayList<JSONObject> best=new ArrayList<>();ArrayList<byte[]> selected=new ArrayList<>();
  // Stream vectors one at a time; keep only the best candidate pool in memory.
  ArrayList<byte[]> vectors=new ArrayList<>();
  try(Cursor c=db.rawQuery("SELECT start,end,vector FROM chunks WHERE identity=?",new String[]{id})){while(c.moveToNext()){cancelled(cancel);byte[] blob=c.getBlob(2);double score=similarity(vector,blob);int at=0;while(at<best.size()&&best.get(at).getDouble("similarity")>=score)at++;if(at<32){best.add(at,new JSONObject().put("start",c.getLong(0)).put("end",c.getLong(1)).put("similarity",score).put("source","SentrySearch"));vectors.add(at,blob);if(best.size()>32){best.remove(32);vectors.remove(32);}}}}
  JSONArray hits=new JSONArray();for(int i=0;i<best.size()&&hits.length()<5;i++){boolean duplicate=false;float[] candidate=new float[DIMS];ByteBuffer b=ByteBuffer.wrap(vectors.get(i)).order(ByteOrder.LITTLE_ENDIAN);for(int j=0;j<DIMS;j++)candidate[j]=b.getFloat();for(byte[] kept:selected)if(similarity(candidate,kept)>.9){duplicate=true;break;}if(!duplicate){hits.put(best.get(i));selected.add(vectors.get(i));}}
  return new JSONObject().put("media",media).put("request",args.optInt("request")).put("results",hits);
 }
}
