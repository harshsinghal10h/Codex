package com.harsh.vlcglass;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;

public final class LibraryStore extends SQLiteOpenHelper {
 public LibraryStore(Context c){super(c,"library.db",null,1);setWriteAheadLoggingEnabled(true);}
 public void onCreate(SQLiteDatabase d){
  d.execSQL("CREATE TABLE media(id INTEGER PRIMARY KEY,uri TEXT UNIQUE NOT NULL,title TEXT NOT NULL,kind TEXT NOT NULL,duration INTEGER DEFAULT 0,size INTEGER DEFAULT 0,position INTEGER DEFAULT 0,favorite INTEGER DEFAULT 0,added INTEGER NOT NULL,lastplayed INTEGER DEFAULT 0)");
  d.execSQL("CREATE TABLE playlists(id INTEGER PRIMARY KEY,name TEXT NOT NULL)");
  d.execSQL("CREATE TABLE entries(playlist INTEGER NOT NULL,media INTEGER NOT NULL,ordinal INTEGER NOT NULL,PRIMARY KEY(playlist,media))");
  d.execSQL("CREATE TABLE bookmarks(id INTEGER PRIMARY KEY,media INTEGER NOT NULL,position INTEGER NOT NULL,label TEXT NOT NULL)");
  d.execSQL("CREATE INDEX media_kind ON media(kind)");
 }
 public void onUpgrade(SQLiteDatabase d,int old,int n){}
 public synchronized long add(String uri,String title,String kind,long size){
  ContentValues v=new ContentValues();v.put("uri",uri);v.put("title",title);v.put("kind",kind);v.put("size",size);v.put("added",System.currentTimeMillis());
  long id=getWritableDatabase().insertWithOnConflict("media",null,v,SQLiteDatabase.CONFLICT_IGNORE);
  if(id<0)try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM media WHERE uri=?",new String[]{uri})){if(c.moveToFirst())return c.getLong(0);}
  return id;
 }
 public synchronized void metadata(long id,long duration){ContentValues v=new ContentValues();v.put("duration",duration);getWritableDatabase().update("media",v,"id=?",new String[]{""+id});}
 public synchronized JSONObject media(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM media WHERE id=?",new String[]{""+id})){return c.moveToFirst()?row(c):null;}}
 private JSONObject row(Cursor c){JSONObject r=new JSONObject();try{for(String k:c.getColumnNames()){int i=c.getColumnIndex(k);if(c.getType(i)==Cursor.FIELD_TYPE_INTEGER)r.put(k,c.getLong(i));else r.put(k,c.getString(i));}}catch(JSONException ignored){}return r;}
 public synchronized JSONArray all(){JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM media ORDER BY added DESC",null)){while(c.moveToNext())a.put(row(c));}return a;}
 public synchronized void progress(long id,long pos,long duration){ContentValues v=new ContentValues();v.put("position",Math.max(0,pos));if(duration>0)v.put("duration",duration);v.put("lastplayed",System.currentTimeMillis());getWritableDatabase().update("media",v,"id=?",new String[]{""+id});}
 public synchronized void favorite(long id,boolean on){ContentValues v=new ContentValues();v.put("favorite",on?1:0);getWritableDatabase().update("media",v,"id=?",new String[]{""+id});}
 public synchronized void remove(long id){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{String[] a={""+id};d.delete("entries","media=?",a);d.delete("bookmarks","media=?",a);d.delete("media","id=?",a);d.setTransactionSuccessful();}finally{d.endTransaction();}}
 public synchronized long createPlaylist(String name){ContentValues v=new ContentValues();v.put("name",name.trim());return getWritableDatabase().insertOrThrow("playlists",null,v);}
 public synchronized void playlistAdd(long p,long id){ContentValues v=new ContentValues();v.put("playlist",p);v.put("media",id);long ordinal=0;try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(MAX(ordinal),-1)+1 FROM entries WHERE playlist=?",new String[]{""+p})){if(c.moveToFirst())ordinal=c.getLong(0);}v.put("ordinal",ordinal);getWritableDatabase().insertWithOnConflict("entries",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
 public synchronized void playlistRemove(long p,long id){getWritableDatabase().delete("entries","playlist=? AND media=?",new String[]{""+p,""+id});}
 public synchronized void playlistDelete(long p){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{d.delete("entries","playlist=?",new String[]{""+p});d.delete("playlists","id=?",new String[]{""+p});d.setTransactionSuccessful();}finally{d.endTransaction();}}
 public synchronized JSONArray playlists(){JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM playlists ORDER BY id",null)){while(c.moveToNext()){JSONObject r=row(c);JSONArray entries=new JSONArray();try(Cursor e=getReadableDatabase().rawQuery("SELECT media FROM entries WHERE playlist=? ORDER BY ordinal",new String[]{""+r.optLong("id")})){while(e.moveToNext())entries.put(e.getLong(0));}try{r.put("entries",entries);}catch(JSONException ignored){}a.put(r);}}return a;}
 public synchronized void bookmark(long media,long pos,String label){ContentValues v=new ContentValues();v.put("media",media);v.put("position",pos);v.put("label",label);getWritableDatabase().insertOrThrow("bookmarks",null,v);}
 public synchronized JSONArray bookmarks(long media){JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM bookmarks WHERE media=? ORDER BY position",new String[]{""+media})){while(c.moveToNext())a.put(row(c));}return a;}
 public synchronized void clearHistory(){ContentValues v=new ContentValues();v.put("lastplayed",0);v.put("position",0);getWritableDatabase().update("media",v,null,null);}
}
