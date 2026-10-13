package io.lively.android;

import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.*;

public final class LibraryStore {
  private final Context context;
  private final File root;

  public LibraryStore(Context c) {
    context = c.getApplicationContext();
    root = new File(context.getFilesDir(), "wallpapers");
    root.mkdirs();
  }

  public File root() {
    return root;
  }

  public synchronized ArrayList<Wallpaper> all() {
    ArrayList<Wallpaper> result = new ArrayList<>(Arrays.asList(Wallpaper.BUILTINS));
    File[] folders = root.listFiles();
    if (folders != null)
      for (File folder : folders) {
        File meta = new File(folder, "android.json");
        if (meta.exists())
          try {
            result.add(Wallpaper.from(new JSONObject(read(meta, 1024 * 1024))));
          } catch (Exception ignored) {
          }
      }
    return result;
  }

  public Wallpaper find(String id) {
    for (Wallpaper w : all()) if (w.id.equals(id)) return w;
    return Wallpaper.BUILTINS[0];
  }

  public File file(Wallpaper w) throws IOException {
    return ZipSandbox.resolve(new File(root, w.id), w.file);
  }

  public File folder(Wallpaper w) {
    return new File(root, w.id);
  }

  public synchronized Wallpaper importUri(Uri uri) throws Exception {
    String name = "wallpaper";
    String mime = context.getContentResolver().getType(uri);
    try (Cursor cur =
        context
            .getContentResolver()
            .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (cur != null && cur.moveToFirst()) name = cur.getString(0);
    }
    boolean zip = name.toLowerCase(Locale.ROOT).endsWith(".zip") || "application/zip".equals(mime);
    String kind = EnginePolicy.kind(name);
    if (!zip && kind.isEmpty()) {
      if (mime != null && mime.startsWith("video/")) {
        kind = "video";
        name += ".mp4";
      } else if (mime != null && mime.startsWith("image/")) {
        kind = "image";
        name += ".jpg";
      } else throw new IOException("Choose a video, image, GIF, HTML file or Lively ZIP package.");
    }
    String id = UUID.randomUUID().toString();
    File dir = new File(root, id);
    dir.mkdirs();
    try {
      Wallpaper w;
      InputStream in = context.getContentResolver().openInputStream(uri);
      if (in == null) throw new IOException("File cannot be opened");
      if (zip) {
        ZipSandbox.extract(in, dir);
        File meta = new File(dir, "LivelyInfo.json");
        if (!meta.exists()) meta = new File(dir, "livelyinfo.json");
        if (!meta.exists()) {
          File[] children = dir.listFiles();
          if (children != null && children.length == 1 && children[0].isDirectory()) {
            File child = children[0];
            for (File f : child.listFiles())
              if (!f.renameTo(new File(dir, f.getName())))
                throw new IOException("Cannot unpack folder");
            child.delete();
            meta = new File(dir, "LivelyInfo.json");
            if (!meta.exists()) meta = new File(dir, "livelyinfo.json");
          }
        }
        if (!meta.exists()) throw new IOException("This ZIP does not contain LivelyInfo.json");
        JSONObject j = new JSONObject(read(meta, 1024 * 1024));
        int type = j.optInt("Type", 1);
        if (type == 0 || type == 4 || type == 5 || type == 6 || type == 9)
          throw new IOException(
              "This package needs a Windows application or game runtime. Choose a web, video or"
                  + " image package.");
        String source = j.optString("FileName");
        kind = EnginePolicy.kind(source);
        if (type == 3 || type == 10) {
          if (!validUrl(source)) throw new IOException("Wallpaper links must use HTTPS");
          kind = type == 10 ? "video" : "web";
        } else {
          File f = ZipSandbox.resolve(dir, source);
          if (!f.isFile() || kind.isEmpty())
            throw new IOException("Unsupported or missing wallpaper media");
        }
        String thumb = j.optString("Thumbnail");
        if (!thumb.isEmpty() && !ZipSandbox.resolve(dir, thumb).isFile()) thumb = "";
        w =
            new Wallpaper(
                id,
                trim(j.optString("Title", "Imported wallpaper"), 120),
                trim(j.optString("Desc", "Lively wallpaper package"), 500),
                trim(j.optString("Author", "Unknown author"), 120),
                kind,
                source,
                thumb,
                0);
      } else {
        String safe = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safe.length() > 120) safe = safe.substring(safe.length() - 120);
        if (safe.isEmpty()) safe = "wallpaper";
        File output = new File(dir, safe);
        copyLimited(in, new FileOutputStream(output), 1024L * 1024 * 1024);
        if (kind.equals("web")) {
        } else if (kind.equals("video")) {
          MediaMetadataRetriever r = new MediaMetadataRetriever();
          try {
            r.setDataSource(output.getPath());
            if (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == null)
              throw new IOException("File contains no playable video");
          } finally {
            r.release();
          }
        } else {
          BitmapFactory.Options o = new BitmapFactory.Options();
          o.inJustDecodeBounds = true;
          BitmapFactory.decodeFile(output.getPath(), o);
          if (o.outWidth <= 0)
            throw new IOException("Image format is not supported by this Android version");
        }
        String title = name.lastIndexOf('.') > 0 ? name.substring(0, name.lastIndexOf('.')) : name;
        w =
            new Wallpaper(
                id,
                trim(title, 120),
                kind.equals("video")
                    ? "Original video • no transcoding"
                    : "Original media • stored locally",
                "You",
                kind,
                safe,
                "",
                0);
      }
      makeThumb(w);
      save(w);
      return w;
    } catch (Throwable e) {
      ZipSandbox.delete(dir);
      if (e instanceof OutOfMemoryError)
        throw new IOException("This file is too large to decode on this device");
      if (e instanceof Exception) throw (Exception) e;
      throw new IOException("Unable to import wallpaper", e);
    }
  }

  public Wallpaper addUrl(String url, String title) throws Exception {
    if (!validUrl(url)) throw new IOException("Enter a complete HTTPS URL");
    String id = UUID.randomUUID().toString();
    Wallpaper w =
        new Wallpaper(
            id,
            trim(title.isEmpty() ? Uri.parse(url).getHost() : title, 120),
            "Website • internet required",
            Uri.parse(url).getHost(),
            "web",
            url,
            "",
            0);
    new File(root, id).mkdirs();
    save(w);
    return w;
  }

  public static boolean validUrl(String u) {
    try {
      Uri uri = Uri.parse(u);
      return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
    } catch (Exception e) {
      return false;
    }
  }

  public synchronized void save(Wallpaper w) throws Exception {
    AtomicFile f = new AtomicFile(new File(folder(w), "android.json"));
    FileOutputStream out = f.startWrite();
    try {
      out.write(w.json().toString().getBytes(StandardCharsets.UTF_8));
      f.finishWrite(out);
    } catch (Exception e) {
      f.failWrite(out);
      throw e;
    }
  }

  public synchronized void delete(Wallpaper w) {
    if (!w.builtin()) ZipSandbox.delete(folder(w));
    Settings.of(context).edit().remove("fav." + w.id).apply();
  }

  private void makeThumb(Wallpaper w) {
    if (w.file.startsWith("https:") || !w.thumbnail.isEmpty() || w.kind.equals("web")) return;
    Bitmap b = null;
    try {
      File source = file(w);
      if (w.kind.equals("video")) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
          r.setDataSource(source.getPath());
          b = r.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 512, 288);
        } finally {
          r.release();
        }
      } else if (android.os.Build.VERSION.SDK_INT >= 28)
        b =
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(source),
                (decoder, info, s) -> {
                  int[] d =
                      EnginePolicy.dimensions(
                          info.getSize().getWidth(), info.getSize().getHeight(), 512);
                  decoder.setTargetSize(d[0], d[1]);
                  decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
      else {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getPath(), o);
        o.inSampleSize = Math.max(1, Math.max(o.outWidth, o.outHeight) / 512);
        o.inJustDecodeBounds = false;
        b = BitmapFactory.decodeFile(source.getPath(), o);
      }
      if (b != null) {
        try (OutputStream out = new FileOutputStream(new File(folder(w), "thumb.jpg"))) {
          b.compress(Bitmap.CompressFormat.JPEG, 85, out);
        }
      }
    } catch (Exception ignored) {
    } finally {
      if (b != null) b.recycle();
    }
  }

  public JSONObject properties(Wallpaper w) {
    if (w.builtin()) return new JSONObject();
    try {
      File f = new File(folder(w), "LivelyProperties.json");
      if (!f.exists()) f = new File(folder(w), "livelyproperties.json");
      return new JSONObject(read(f, 1024 * 1024));
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  public File export(Wallpaper w) throws Exception {
    File exports = new File(context.getCacheDir(), "exports");
    exports.mkdirs();
    File result = new File(exports, w.id + ".lively.zip");
    try (ZipOutputStream zip =
        new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(result)))) {
      JSONObject info =
          new JSONObject()
              .put("AppVersion", "2.0.0.0")
              .put("Title", w.title)
              .put("Desc", w.description)
              .put("Author", w.author)
              .put("License", "GPL-3.0")
              .put("IsAbsolutePath", false)
              .put("FileName", w.builtin() ? "wallpaper.html" : w.file)
              .put(
                  "Type",
                  w.builtin()
                      ? 1
                      : w.kind.equals("web")
                          ? (w.file.startsWith("https:") ? 3 : 1)
                          : w.kind.equals("video")
                              ? (w.file.startsWith("https:") ? 10 : 7)
                              : w.kind.equals("gif") ? 8 : 11);
      entry(zip, "LivelyInfo.json", info.toString(2).getBytes(StandardCharsets.UTF_8));
      if (w.builtin()) {
        String shader = asset("scene.frag");
        String html =
            "<!doctype html><html><meta name='viewport'"
                + " content='width=device-width,initial-scale=1'><style>html,body{margin:0;overflow:hidden;background:#111}canvas{width:100vw;height:100vh}</style><canvas"
                + " id='c'></canvas><script>const"
                + " g=c.getContext('webgl'),v=g.createShader(g.VERTEX_SHADER),f=g.createShader(g.FRAGMENT_SHADER);g.shaderSource(v,'attribute"
                + " vec2 p;void"
                + " main(){gl_Position=vec4(p,0.,1.);}');g.compileShader(v);g.shaderSource(f,"
                + JSONObject.quote(shader)
                + ");g.compileShader(f);let"
                + " p=g.createProgram();g.attachShader(p,v);g.attachShader(p,f);g.linkProgram(p);g.useProgram(p);let"
                + " b=g.createBuffer();g.bindBuffer(g.ARRAY_BUFFER,b);g.bufferData(g.ARRAY_BUFFER,new"
                + " Float32Array([-1,-1,1,-1,-1,1,1,1]),g.STATIC_DRAW);let"
                + " a=g.getAttribLocation(p,'p');g.enableVertexAttribArray(a);g.vertexAttribPointer(a,2,g.FLOAT,false,0,0);g.uniform1i(g.getUniformLocation(p,'uScene'),"
                + w.scene
                + ");g.uniform1f(g.getUniformLocation(p,'uIntensity'),1);g.uniform3f(g.getUniformLocation(p,'uAccent'),.35,.66,.98);let"
                + " t=performance.now();function"
                + " frame(){c.width=innerWidth;c.height=innerHeight;g.viewport(0,0,c.width,c.height);g.uniform2f(g.getUniformLocation(p,'uResolution'),c.width,c.height);g.uniform1f(g.getUniformLocation(p,'uTime'),(performance.now()-t)/1000);g.uniform2f(g.getUniformLocation(p,'uTouch'),.5,.5);g.drawArrays(g.TRIANGLE_STRIP,0,4);requestAnimationFrame(frame)}frame();</script></html>";
        entry(zip, "wallpaper.html", html.getBytes(StandardCharsets.UTF_8));
      } else {
        appendFolder(zip, folder(w), folder(w));
        JSONObject props = properties(w);
        if (props.length() > 0) {
          Iterator<String> keys = props.keys();
          while (keys.hasNext()) {
            String key = keys.next();
            String value = Settings.of(context).getString(w.id + ".property." + key, null);
            if (value != null && props.optJSONObject(key) != null)
              try {
                props.getJSONObject(key).put("value", new JSONTokener(value).nextValue());
              } catch (Exception ignored) {
              }
          }
          entry(zip, "LivelyProperties.json", props.toString(2).getBytes(StandardCharsets.UTF_8));
        }
      }
    }
    return result;
  }

  private void appendFolder(ZipOutputStream zip, File base, File at) throws Exception {
    File[] fs = at.listFiles();
    if (fs != null)
      for (File f : fs) {
        String path = base.toPath().relativize(f.toPath()).toString();
        if (f.isDirectory()) appendFolder(zip, base, f);
        else if (!path.equals("android.json")
            && !path.equalsIgnoreCase("LivelyInfo.json")
            && !path.equalsIgnoreCase("LivelyProperties.json")) {
          zip.putNextEntry(new ZipEntry(path));
          try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) zip.write(buf, 0, n);
          }
          zip.closeEntry();
        }
      }
  }

  private static void entry(ZipOutputStream z, String name, byte[] data) throws IOException {
    z.putNextEntry(new ZipEntry(name));
    z.write(data);
    z.closeEntry();
  }

  private String asset(String name) throws IOException {
    try (InputStream in = context.getAssets().open(name)) {
      ByteArrayOutputStream b = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) != -1) b.write(buf, 0, n);
      return b.toString("UTF-8");
    }
  }

  public static void copyLimited(InputStream in, OutputStream out, long limit) throws IOException {
    try (InputStream i = in;
        OutputStream o = out) {
      byte[] b = new byte[65536];
      long total = 0;
      int n;
      while ((n = i.read(b)) != -1) {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Operation cancelled");
        total += n;
        if (total > limit) throw new IOException("File exceeds the import limit");
        o.write(b, 0, n);
      }
    }
  }

  public static String read(File f, int limit) throws IOException {
    if (f.length() > limit) throw new IOException("Metadata too large");
    try (InputStream in = new FileInputStream(f)) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] b = new byte[4096];
      int n;
      while ((n = in.read(b)) != -1) {
        if (out.size() + n > limit) throw new IOException("Metadata too large");
        out.write(b, 0, n);
      }
      return out.toString("UTF-8");
    }
  }

  private static String trim(String s, int max) {
    return s == null ? "" : s.length() > max ? s.substring(0, max) : s;
  }
}
