package com.harsh.vlcglass;
import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Native client for the bundled, authenticated SentrySearch companion. */
public final class SceneSearchClient {
 public interface Callback{void send(String type,JSONObject data);}
 private final Context context;private final CredentialStore secrets;private SemanticVideoSearch direct;
 public SceneSearchClient(Context c){context=c.getApplicationContext();secrets=new CredentialStore(c);}
 public void credentials(JSONObject a)throws Exception{secrets.put("gemini",a.optString("key"));secrets.put("companion",a.optString("token"));}
 private String base(JSONObject settings)throws Exception{
  String value=settings.optString("analysisEndpoint").trim().replaceAll("/+$","");
  if(value.isEmpty())throw new IOException("Set up the SentrySearch companion connection, then index this video.");
  URI uri=new URI(value);if(uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||uri.getHost()==null)throw new IOException("Use a valid companion URL without credentials or query parameters.");
  String host=uri.getHost();boolean local=host.equals("localhost")||host.equals("127.0.0.1")||host.matches("192\\.168\\.[0-9]{1,3}\\.[0-9]{1,3}")||host.matches("10\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}")||host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\.[0-9]{1,3}\\.[0-9]{1,3}");
  if(!uri.getScheme().equals("https")&&!(uri.getScheme().equals("http")&&local))throw new IOException("Use HTTPS for the companion connection. HTTP is allowed only on a private local network.");
  if(secrets.get("companion").isEmpty())throw new IOException("Enter the companion access token first.");return value;
 }
 private JSONObject call(String url,JSONObject data,InputStream video,String filename)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
  c.setConnectTimeout(15000);c.setReadTimeout(60000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Authorization","Bearer "+secrets.get("companion"));
  if(data!=null||video!=null){c.setRequestMethod("POST");c.setDoOutput(true);
   if(video!=null){c.setRequestProperty("Content-Type","application/octet-stream");c.setRequestProperty("X-Filename",URLEncoder.encode(filename,"UTF-8"));c.setChunkedStreamingMode(65536);}
   else c.setRequestProperty("Content-Type","application/json");
   try(OutputStream out=c.getOutputStream()){if(video!=null){byte[] b=new byte[65536];int n;while((n=video.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedException();out.write(b,0,n);}}else out.write(data.toString().getBytes("UTF-8"));}
  }
  try{int status=c.getResponseCode();if(status<200||status>=300)throw new IOException("The companion returned HTTP "+status+". Check the connection and token.");
   try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>1000000)throw new IOException("The companion response is too large");out.write(b,0,n);}JSONObject result=new JSONObject(out.toString("UTF-8"));if(result.has("error"))throw new IOException(result.getString("error"));return result;}
  }finally{c.disconnect();}
 }
 private SemanticVideoSearch direct(){if(direct==null)direct=new SemanticVideoSearch(context);return direct;}
 private boolean nativeSearch(JSONObject settings){return !settings.optString("searchConnection","direct").equals("companion")&&!settings.optString("searchBackend","gemini").equals("local");}
 public void index(long media,JSONObject item,JSONObject settings,boolean consent,Callback callback,BooleanSupplier cancel)throws Exception{
  if(nativeSearch(settings)){direct().index(media,item,consent,callback,cancel);return;}
  if(!consent)throw new IOException("Indexing needs permission to send this selected video to the companion.");String endpoint=base(settings),uri=item.optString("uri");if(!uri.startsWith("content:"))throw new IOException("Index a local video selected through Open media.");
  JSONObject upload;
  try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(uri))){if(in==null)throw new IOException("Cannot open this video");upload=call(endpoint+"/v1/media",null,in,item.optString("title","video.mp4"));}
  if(cancel.getAsBoolean())return;
  String remote=upload.getString("media_id");
  context.getSharedPreferences("scene-indices",0).edit().putString(endpoint+":"+media,remote).apply();
  // API keys remain on the desktop companion; the mobile key is deliberately
  // never forwarded to an arbitrary endpoint.
  JSONObject job=call(endpoint+"/v1/index",new JSONObject().put("media_id",remote).put("backend",settings.optString("searchBackend","gemini")),null,null);
  String id=job.getString("job_id");
  while(!cancel.getAsBoolean()){
   JSONObject state=call(endpoint+"/v1/jobs/"+id,null,null,null);state.put("media",media);callback.send("sceneIndex",state);
   if(state.optString("phase").equals("indexed"))return;if(state.has("error"))throw new IOException(state.getString("error"));Thread.sleep(1000);
  }
 }
 public JSONObject search(long media,JSONObject item,JSONObject settings,JSONObject args,BooleanSupplier cancel)throws Exception{
  if(nativeSearch(settings))return direct().search(media,item,args,cancel);
  String endpoint=base(settings),remote=context.getSharedPreferences("scene-indices",0).getString(endpoint+":"+media,"");if(remote.isEmpty())throw new IOException("Index this video with SentrySearch before searching.");
  JSONObject result=call(endpoint+"/v1/search",new JSONObject().put("media_id",remote).put("query",args.optString("query")),null,null);
  result.put("media",media).put("request",args.optInt("request"));return result;
 }
}
