package io.lively.android;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class MainActivity extends Activity {
  private LibraryStore library;
  private FrameLayout root, content;
  private LinearLayout header;
  private GlassDrawable glass;
  private final ExecutorService work = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private final android.util.LruCache<String, Bitmap> thumbs =
      new android.util.LruCache<String, Bitmap>(4 * 1024 * 1024) {
        protected int sizeOf(String key, Bitmap b) {
          return b.getAllocationByteCount();
        }
      };
  private final Set<String> thumbPending = new HashSet<>();
  private String page = "Library", settingCategory = "General", query = "";
  private boolean favoriteOnly, light;
  private int fg, muted, bg, panel;
  private ImageView imagePreview;
  private Wallpaper detail;
  private AlertDialog detailDialog;
  private RendererController preview;
  private Surface previewSurface;
  private String pendingApply;
  private MemoryChart chart;
  private TextView memoryText;
  private Bitmap backdrop;
  private long lastGlass;
  private Future<?> operation;
  private AlertDialog progress;
  private static final int IMPORT = 10, APPLY = 11;

  public void onCreate(Bundle state) {
    super.onCreate(state);
    library = new LibraryStore(this);
    if (state != null) {
      page = state.getString("page", "Library");
      query = state.getString("query", "");
      pendingApply = state.getString("pendingApply");
    } else if (Settings.of(this).contains("pending_apply"))
      pendingApply = Settings.of(this).getString("pending_apply", null);
    build();
    if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
      Uri u = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
      if (u != null) importFile(u);
    }
  }

  public void onSaveInstanceState(Bundle state) {
    state.putString("page", page);
    state.putString("query", query);
    state.putString("pendingApply", pendingApply);
    super.onSaveInstanceState(state);
  }

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private TextView text(String value, int sp, int color, boolean bold) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(sp);
    t.setTextColor(color);
    t.setFontFeatureSettings("tnum");
    if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    t.setGravity(Gravity.CENTER_VERTICAL);
    return t;
  }

  private GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private android.graphics.drawable.Drawable clickable(int color, int radius) {
    return new RippleDrawable(
        android.content.res.ColorStateList.valueOf(light ? 0x18000000 : 0x30ffffff),
        shape(color, radius),
        shape(Color.WHITE, radius));
  }

  private LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  private void add(LinearLayout l, View v, int width, int height) {
    l.addView(
        v,
        new LinearLayout.LayoutParams(
            width < 0 ? width : dp(width), height < 0 ? height : dp(height)));
  }

  private View iconButton(String name, Runnable action) {
    FrameLayout box = new FrameLayout(this);
    box.setContentDescription(name.substring(0, 1).toUpperCase() + name.substring(1));
    box.setFocusable(true);
    box.setBackground(clickable(Color.TRANSPARENT, 10));
    IconView icon = new IconView(this, name, fg);
    box.addView(icon, new FrameLayout.LayoutParams(-1, -1));
    box.setOnClickListener(
        v -> {
          v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
          action.run();
        });
    return box;
  }

  private Button button(String label, Runnable run, boolean primary) {
    Button b = new Button(this);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextSize(14);
    b.setTextColor(primary ? 0xff071421 : fg);
    b.setTypeface(Typeface.create("sans-serif-medium", 0));
    b.setMinHeight(dp(48));
    b.setPadding(dp(16), 0, dp(16), 0);
    b.setBackground(clickable(primary ? 0xff83c3ff : panel, 10));
    b.setOnClickListener(
        v -> {
          v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
          run.run();
        });
    return b;
  }

  private void build() {
    String theme = Settings.of(this).getString("theme", "system");
    light =
        theme.equals("light")
            || (theme.equals("system")
                && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_NO);
    setTheme(
        light
            ? android.R.style.Theme_Material_Light_NoActionBar
            : io.lively.android.R.style.AppTheme);
    bg = light ? 0xfff2f3f5 : 0xff191b20;
    panel = light ? 0xffffffff : 0xff25282f;
    fg = light ? 0xff151920 : 0xfff3f5f8;
    muted = light ? 0xff525b69 : 0xffabb1bd;
    getWindow().setStatusBarColor(Color.TRANSPARENT);
    getWindow().setNavigationBarColor(Color.TRANSPARENT);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | (light
                    ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    : 0));
    root = new FrameLayout(this);
    root.setBackgroundColor(bg);
    root.setOnApplyWindowInsetsListener(
        (v, i) -> {
          v.setPadding(
              i.getSystemWindowInsetLeft(),
              i.getSystemWindowInsetTop(),
              i.getSystemWindowInsetRight(),
              i.getSystemWindowInsetBottom());
          return i.consumeSystemWindowInsets();
        });
    setContentView(root);
    content = new FrameLayout(this);
    root.addView(content, new FrameLayout.LayoutParams(-1, -1));
    header = column();
    glass = new GlassDrawable(light, dp(0), Settings.of(this).getBoolean("glass", true));
    header.setBackground(glass);
    root.addView(
        header,
        new FrameLayout.LayoutParams(-1, dp(page.equals("Settings") ? 116 : 112), Gravity.TOP));
    LinearLayout top = row();
    top.setPadding(dp(12), 0, dp(10), 0);
    add(header, top, -1, 60);
    if (page.equals("Settings")) add(top, iconButton("back", () -> navigate("Library")), 48, 48);
    else {
      ImageView logo = new ImageView(this);
      logo.setImageResource(io.lively.android.R.drawable.ic_lively);
      logo.setPadding(dp(4), dp(4), dp(4), dp(4));
      add(top, logo, 40, 40);
    }
    TextView title = text(page.equals("Settings") ? "Settings" : "Lively Wallpaper", 20, fg, true);
    LinearLayout.LayoutParams titleLP = new LinearLayout.LayoutParams(0, -1, 1);
    titleLP.setMarginStart(dp(8));
    top.addView(title, titleLP);
    if (!page.equals("Settings")) {
      add(top, iconButton("search", this::search), 48, 48);
      add(top, iconButton("add", this::addWallpaper), 48, 48);
    }
    add(
        top,
        iconButton(
            page.equals("Settings") ? "more" : "settings",
            page.equals("Settings") ? this::about : () -> navigate("Settings")),
        48,
        48);
    if (page.equals("Settings")) {
      HorizontalScrollView h = new HorizontalScrollView(this);
      h.setHorizontalScrollBarEnabled(false);
      LinearLayout categories = row();
      categories.setPadding(dp(14), 0, dp(14), 0);
      for (String name :
          new String[] {"General", "Performance", "Wallpaper", "Screensaver", "System"}) {
        TextView t =
            text(
                name,
                14,
                name.equals(settingCategory) ? 0xff83bfff : muted,
                name.equals(settingCategory));
        t.setPadding(dp(14), 0, dp(14), 0);
        t.setBackground(
            clickable(
                name.equals(settingCategory)
                    ? (light ? 0xffe2efff : 0xff344150)
                    : Color.TRANSPARENT,
                9));
        t.setContentDescription(name + " settings");
        t.setOnClickListener(
            v -> {
              settingCategory = name;
              build();
            });
        add(categories, t, -2, 44);
      }
      h.addView(categories);
      add(header, h, -1, 52);
      settings();
    } else {
      LinearLayout tabs = row();
      tabs.setPadding(dp(18), 0, dp(12), 0);
      for (String name : new String[] {"Library", "Gallery"}) {
        TextView t = text(name, 16, page.equals(name) ? fg : muted, page.equals(name));
        t.setPadding(dp(12), 0, dp(18), 0);
        t.setBackground(
            clickable(
                page.equals(name) ? (light ? 0xffe0e6ed : 0xff353943) : Color.TRANSPARENT, 9));
        t.setContentDescription(name + " tab");
        t.setOnClickListener(v -> navigate(name));
        add(tabs, t, -2, 40);
      }
      View spacer = new View(this);
      tabs.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
      add(tabs, iconButton("display", this::controlPanel), 48, 48);
      add(header, tabs, -1, 52);
      wallpaperGrid();
      footer();
    }
    root.post(
        () -> {
          captureGlass();
          root.requestApplyInsets();
        });
  }

  private void navigate(String target) {
    page = target;
    query = "";
    favoriteOnly = false;
    build();
  }

  private void search() {
    EditText input = new EditText(this);
    input.setSingleLine(true);
    input.setText(query);
    input.setHint("Search wallpapers");
    input.setTextColor(fg);
    input.setHintTextColor(muted);
    input.setPadding(dp(18), dp(10), dp(18), dp(10));
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Search library")
            .setView(input)
            .setPositiveButton(
                "Search",
                (d, w) -> {
                  query = input.getText().toString().trim();
                  wallpaperGrid();
                })
            .setNeutralButton(
                favoriteOnly ? "All wallpapers" : "Favourites",
                (d, w) -> {
                  favoriteOnly = !favoriteOnly;
                  query = "";
                  wallpaperGrid();
                })
            .setNegativeButton("Cancel", null)
            .create();
    dialog.show();
    input.requestFocus();
    input.setOnEditorActionListener(
        (v, action, event) -> {
          query = input.getText().toString().trim();
          wallpaperGrid();
          dialog.dismiss();
          return true;
        });
    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
  }

  private void wallpaperGrid() {
    content.removeAllViews();
    ArrayList<Wallpaper> items = new ArrayList<>();
    for (Wallpaper w : library.all())
      if ((!page.equals("Gallery") || w.builtin())
          && (!favoriteOnly || Settings.of(this).getBoolean("fav." + w.id, false))
          && (w.title + " " + w.description)
              .toLowerCase(Locale.ROOT)
              .contains(query.toLowerCase(Locale.ROOT))) items.add(w);
    GridView grid = new GridView(this);
    grid.setNumColumns(
        getResources().getConfiguration().screenWidthDp >= 800
            ? 4
            : getResources().getConfiguration().screenWidthDp >= 560 ? 3 : 2);
    grid.setHorizontalSpacing(dp(12));
    grid.setVerticalSpacing(dp(14));
    grid.setPadding(dp(18), dp(page.equals("Gallery") ? 156 : 128), dp(18), dp(98));
    grid.setClipToPadding(false);
    grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
    grid.setSelector(android.R.color.transparent);
    content.addView(grid, new FrameLayout.LayoutParams(-1, -1));
    grid.setAdapter(
        new BaseAdapter() {
          public int getCount() {
            return items.size();
          }

          public Object getItem(int pos) {
            return items.get(pos);
          }

          public long getItemId(int pos) {
            return items.get(pos).id.hashCode();
          }

          public View getView(int pos, View old, android.view.ViewGroup parent) {
            Wallpaper w = items.get(pos);
            Card card = old instanceof Card ? (Card) old : new Card();
            card.bind(w);
            return card;
          }
        });
    grid.setOnScrollListener(
        new AbsListView.OnScrollListener() {
          public void onScrollStateChanged(AbsListView v, int state) {}

          public void onScroll(AbsListView v, int first, int count, int total) {
            captureGlass();
          }
        });
    if (page.equals("Gallery")) {
      TextView subtitle = text("Included scenes • ready offline", 12, muted, false);
      subtitle.setPadding(dp(24), 0, 0, 0);
      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(34));
      lp.topMargin = dp(114);
      content.addView(subtitle, lp);
    }
    if (items.isEmpty()) {
      LinearLayout empty = column();
      empty.setPadding(dp(28), dp(165), dp(28), 0);
      add(
          empty,
          text(favoriteOnly ? "No favourites yet" : "No wallpapers found", 22, fg, true),
          -1,
          44);
      add(
          empty,
          text(
              favoriteOnly
                  ? "Open a wallpaper and tap the star to keep it here."
                  : "Try another search, or add a video, image or Lively package.",
              14,
              muted,
              false),
          -1,
          80);
      add(
          empty,
          button(
              "Show all wallpapers",
              () -> {
                favoriteOnly = false;
                query = "";
                wallpaperGrid();
              },
              true),
          -1,
          48);
      content.addView(empty);
    }
  }

  private final class Card extends LinearLayout {
    final ImageView image;
    final TextView title, description;
    final FrameLayout art;
    final TextView badge;
    Wallpaper wallpaper;

    Card() {
      super(MainActivity.this);
      setOrientation(VERTICAL);
      setBackground(clickable(panel, 8));
      setFocusable(true);
      art = new FrameLayout(MainActivity.this);
      image = new ImageView(MainActivity.this);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      image.setBackgroundColor(0xff273340);
      art.addView(image, new FrameLayout.LayoutParams(-1, -1));
      art.setClipToOutline(true);
      art.setBackground(shape(panel, 8));
      badge = text("", 10, Color.WHITE, true);
      badge.setPadding(dp(7), dp(2), dp(7), dp(2));
      badge.setBackground(shape(0xbe18212b, 5));
      FrameLayout.LayoutParams blp =
          new FrameLayout.LayoutParams(-2, dp(23), Gravity.BOTTOM | Gravity.LEFT);
      blp.setMargins(dp(8), 0, 0, dp(8));
      art.addView(badge, blp);
      addView(art, new LinearLayout.LayoutParams(-1, dp(96)));
      title = text("", 14, fg, true);
      title.setSingleLine(true);
      title.setEllipsize(TextUtils.TruncateAt.END);
      title.setPadding(dp(10), dp(6), dp(8), 0);
      addView(title, new LinearLayout.LayoutParams(-1, dp(31)));
      description = text("", 11, muted, false);
      description.setSingleLine(true);
      description.setEllipsize(TextUtils.TruncateAt.END);
      description.setPadding(dp(10), 0, dp(8), dp(8));
      addView(description, new LinearLayout.LayoutParams(-1, dp(27)));
      setOnClickListener(v -> showDetail(wallpaper));
      setOnLongClickListener(
          v -> {
            wallpaperMenu(wallpaper, this);
            return true;
          });
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
      int width = View.MeasureSpec.getSize(widthSpec);
      if (width > 0) art.getLayoutParams().height = Math.round(width * 9f / 16f);
      super.onMeasure(widthSpec, heightSpec);
    }

    void bind(Wallpaper w) {
      wallpaper = w;
      image.setTag(w.id);
      title.setText(w.title);
      description.setText(w.description);
      String active = Settings.active(MainActivity.this);
      badge.setText(isLivelyActive() && active.equals(w.id) ? "Active" : w.typeLabel());
      setContentDescription(
          w.title
              + ", "
              + w.typeLabel()
              + (active.equals(w.id) ? ", active" : "")
              + ". Long press for options.");
      Bitmap cached = thumbs.get(w.id);
      image.setImageBitmap(cached);
      if (cached == null) thumbnail(w, image);
    }
  }

  private void thumbnail(Wallpaper w, ImageView image) {
    image.setTag(w.id);
    if (thumbPending.contains(w.id)) return;
    thumbPending.add(w.id);
    work.execute(
        () -> {
          Bitmap b = null;
          try {
            if (w.builtin()) {
              try (InputStream in = getAssets().open("thumbs/" + w.id + ".jpg")) {
                b = BitmapFactory.decodeStream(in);
              }
            } else {
              File f =
                  new File(library.folder(w), w.thumbnail.isEmpty() ? "thumb.jpg" : w.thumbnail);
              if (f.isFile()) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getPath(), o);
                o.inSampleSize = Math.max(1, Math.max(o.outWidth, o.outHeight) / 512);
                o.inJustDecodeBounds = false;
                b = BitmapFactory.decodeFile(f.getPath(), o);
              }
            }
          } catch (Exception ignored) {
          }
          final Bitmap result = b;
          main.post(
              () -> {
                thumbPending.remove(w.id);
                if (result != null) {
                  thumbs.put(w.id, result);
                  if (w.id.equals(image.getTag())) image.setImageBitmap(result);
                }
                captureGlass();
              });
        });
  }

  private void captureGlass() {
    if (!Settings.of(this).getBoolean("glass", true)
        || header == null
        || content == null
        || content.getWidth() == 0
        || android.os.SystemClock.uptimeMillis() - lastGlass < 80) return;
    lastGlass = android.os.SystemClock.uptimeMillis();
    if (backdrop == null) backdrop = Bitmap.createBitmap(192, 64, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(backdrop);
    c.drawColor(bg);
    c.save();
    c.scale(192f / content.getWidth(), 64f / dp(112));
    try {
      content.draw(c);
    } catch (Exception ignored) {
    }
    c.restore();
    glass.backdrop(backdrop);
  }

  private void footer() {
    Wallpaper w = library.find(Settings.active(this));
    LinearLayout status = row();
    status.setPadding(dp(14), 0, dp(10), 0);
    status.setBackground(
        new GlassDrawable(light, dp(14), Settings.of(this).getBoolean("glass", true)));
    FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, dp(66), Gravity.BOTTOM);
    p.setMargins(dp(14), 0, dp(14), dp(12));
    root.addView(status, p);
    IconView monitor = new IconView(this, "display", 0xff83bfff);
    add(status, monitor, 42, 48);
    LinearLayout labels = column();
    add(labels, text(w.title, 14, fg, true), -1, 25);
    TextView hint =
        text(
            isLivelyActive()
                ? (Settings.of(this).getBoolean("paused", false)
                    ? "Playback paused"
                    : "Home screen wallpaper")
                : "Preview a scene to set your wallpaper",
            11,
            muted,
            false);
    hint.setSingleLine(true);
    hint.setEllipsize(TextUtils.TruncateAt.END);
    add(labels, hint, -1, 22);
    status.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    labels.setOnClickListener(v -> controlPanel());
    boolean paused = Settings.of(this).getBoolean("paused", false);
    View pause =
        iconButton(
            paused ? "play" : "pause",
            () -> {
              Settings.of(this).edit().putBoolean("paused", !paused).apply();
              build();
            });
    pause.setContentDescription(paused ? "Resume wallpaper" : "Pause wallpaper");
    add(status, pause, 48, 48);
  }

  private boolean isLivelyActive() {
    WallpaperInfo info = WallpaperManager.getInstance(this).getWallpaperInfo();
    return info != null && info.getPackageName().equals(getPackageName());
  }

  private void wallpaperMenu(Wallpaper w, View anchor) {
    PopupMenu menu = new PopupMenu(this, anchor);
    for (String name :
        new String[] {
          "Preview",
          "Customise",
          "Share package",
          Settings.of(this).getBoolean("fav." + w.id, false) ? "Remove favourite" : "Add favourite",
          "Rename",
          w.builtin() ? "About scene" : "Delete"
        }) menu.getMenu().add(name);
    menu.setOnMenuItemClickListener(
        item -> {
          String action = item.getTitle().toString();
          if (action.equals("Preview")) showDetail(w);
          else if (action.equals("Customise")) customise(w);
          else if (action.equals("Share package")) share(w);
          else if (action.contains("favourite")) {
            Settings.of(this)
                .edit()
                .putBoolean("fav." + w.id, !Settings.of(this).getBoolean("fav." + w.id, false))
                .apply();
            wallpaperGrid();
          } else if (action.equals("Rename")) rename(w);
          else if (action.equals("Delete")) delete(w);
          else
            message(
                w.title,
                w.description
                    + "\n\n"
                    + "This is an original Android scene inspired by Lively's wallpaper types.");
          return true;
        });
    menu.show();
  }

  private void rename(Wallpaper w) {
    if (w.builtin()) {
      message(
          "Included scene", "Included scene names are fixed. Imported wallpapers can be renamed.");
      return;
    }
    EditText input = new EditText(this);
    input.setText(w.title);
    new AlertDialog.Builder(this)
        .setTitle("Rename wallpaper")
        .setView(input)
        .setPositiveButton(
            "Save",
            (d, n) -> {
              try {
                library.save(
                    new Wallpaper(
                        w.id,
                        input.getText().toString().trim(),
                        w.description,
                        w.author,
                        w.kind,
                        w.file,
                        w.thumbnail,
                        w.scene));
                wallpaperGrid();
              } catch (Exception e) {
                error(e);
              }
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void delete(Wallpaper w) {
    new AlertDialog.Builder(this)
        .setTitle("Delete " + w.title + "?")
        .setMessage(
            "This removes its local copy and customisation. Your original imported file is kept.")
        .setPositiveButton(
            "Delete",
            (d, n) -> {
              if (Settings.active(this).equals(w.id))
                Settings.of(this).edit().putString("active", "fluids").apply();
              library.delete(w);
              build();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void showDetail(Wallpaper w) {
    if (detailDialog != null) detailDialog.dismiss();
    detail = w;
    LinearLayout body = column();
    body.setPadding(dp(20), dp(14), dp(20), dp(18));
    LinearLayout heading = row();
    TextView title = text(w.title, 24, fg, true);
    heading.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
    View star =
        iconButton(
            "star",
            () -> {
              boolean value = !Settings.of(this).getBoolean("fav." + w.id, false);
              Settings.of(this).edit().putBoolean("fav." + w.id, value).apply();
              toast(value ? "Added to favourites" : "Removed from favourites");
            });
    star.setContentDescription("Toggle favourite for " + w.title);
    add(heading, star, 48, 48);
    add(body, heading, -1, 52);
    TextureView texture = new TextureView(this);
    texture.setOpaque(true);
    texture.setContentDescription("Live preview of " + w.title);
    add(body, texture, -1, getResources().getConfiguration().screenHeightDp < 700 ? 200 : 260);
    if (w.kind.equals("image")) {
      texture.setVisibility(View.GONE);
      imagePreview = new ImageView(this);
      imagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
      imagePreview.setContentDescription("Original image preview");
      add(
          body,
          imagePreview,
          -1,
          getResources().getConfiguration().screenHeightDp < 700 ? 200 : 260);
      work.execute(
          () -> {
            try {
              Bitmap bitmap;
              if (Build.VERSION.SDK_INT >= 28)
                bitmap =
                    ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(library.file(w)),
                        (decoder, info, source) -> {
                          int[] dims =
                              EnginePolicy.dimensions(
                                  info.getSize().getWidth(),
                                  info.getSize().getHeight(),
                                  Settings.cap(MainActivity.this));
                          decoder.setTargetSize(dims[0], dims[1]);
                        });
              else {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(library.file(w).getPath(), options);
                options.inSampleSize = 1;
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize
                    > Settings.cap(MainActivity.this)) options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                bitmap = BitmapFactory.decodeFile(library.file(w).getPath(), options);
              }
              main.post(
                  () -> {
                    if (detail == w && imagePreview != null) {
                      imagePreview.setImageBitmap(bitmap);
                      if (Build.VERSION.SDK_INT >= 34
                          && bitmap.hasGainmap()
                          && detailDialog != null)
                        detailDialog
                            .getWindow()
                            .setColorMode(android.content.pm.ActivityInfo.COLOR_MODE_HDR);
                    }
                  });
            } catch (Exception e) {
              main.post(() -> error(e));
            }
          });
    }
    texture.setSurfaceTextureListener(
        new TextureView.SurfaceTextureListener() {
          private RendererController owner;
          private Surface ownerSurface;

          public void onSurfaceTextureAvailable(SurfaceTexture s, int width, int height) {
            int[] target = EnginePolicy.dimensions(width, height, Settings.cap(MainActivity.this));
            s.setDefaultBufferSize(target[0], target[1]);
            previewSurface = new Surface(s);
            preview =
                new RendererController(
                    MainActivity.this, w, previewSurface, target[0], target[1], true);
            owner = preview;
            ownerSurface = previewSurface;
            preview.visible(true);
          }

          public void onSurfaceTextureSizeChanged(SurfaceTexture s, int width, int height) {}

          public boolean onSurfaceTextureDestroyed(SurfaceTexture s) {
            if (owner != null) owner.close();
            if (ownerSurface != null) ownerSurface.release();
            if (preview == owner) preview = null;
            if (previewSurface == ownerSurface) previewSurface = null;
            return true;
          }

          public void onSurfaceTextureUpdated(SurfaceTexture s) {}
        });
    texture.setOnTouchListener(
        (v, event) -> {
          if (preview != null)
            preview.touch(
                event.getX() / Math.max(1, v.getWidth()),
                event.getY() / Math.max(1, v.getHeight()),
                event.getActionMasked());
          return true;
        });
    TextView info = text(w.typeLabel() + " • " + w.author + "\n" + w.description, 13, muted, false);
    info.setPadding(0, dp(12), 0, dp(12));
    add(body, info, -1, -2);
    add(body, button("Set wallpaper", () -> apply(w), true), -1, 48);
    LinearLayout actions = row();
    Button custom = button("Customise", () -> customise(w), false),
        more = button("More options", () -> wallpaperMenu(w, actions), false);
    LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(48), 1);
    a.setMargins(0, dp(10), dp(5), 0);
    actions.addView(custom, a);
    LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, dp(48), 1);
    b.setMargins(dp(5), dp(10), 0, 0);
    actions.addView(more, b);
    add(body, actions, -1, 58);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(body);
    detailDialog = new AlertDialog.Builder(this).setView(scroll).create();
    detailDialog.setOnDismissListener(
        d -> {
          closePreview();
          detail = null;
          detailDialog = null;
        });
    detailDialog.show();
    detailDialog.getWindow().setBackgroundDrawable(shape(panel, 18));
    detailDialog
        .getWindow()
        .setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(560)), -2);
    if (Settings.of(this).getBoolean("reduce_motion", false))
      detailDialog.getWindow().setWindowAnimations(0);
  }

  private void closePreview() {
    if (imagePreview != null) {
      imagePreview.setImageDrawable(null);
      imagePreview = null;
    }
    if (preview != null) {
      preview.close();
      preview = null;
    }
    if (previewSurface != null) {
      previewSurface.release();
      previewSurface = null;
    }
  }

  private void apply(Wallpaper w) {
    if (w.kind.equals("image")) {
      new AlertDialog.Builder(this)
          .setTitle("Set image wallpaper")
          .setItems(
              new String[] {
                "Live wallpaper with fit and parallax controls",
                "Original image on home screen",
                "Original image on lock screen",
                "Original image on both screens"
              },
              (dialog, index) -> {
                if (index == 0) applyLive(w);
                else
                  runJob(
                      "Setting original image",
                      () -> {
                        try (InputStream original = new FileInputStream(library.file(w))) {
                          WallpaperManager.getInstance(this)
                              .setStream(
                                  original,
                                  null,
                                  true,
                                  index == 1
                                      ? WallpaperManager.FLAG_SYSTEM
                                      : index == 2
                                          ? WallpaperManager.FLAG_LOCK
                                          : WallpaperManager.FLAG_SYSTEM
                                              | WallpaperManager.FLAG_LOCK);
                        }
                        return w;
                      },
                      result -> {
                        toast("Original image applied");
                        if (detailDialog != null) detailDialog.dismiss();
                        build();
                      });
              })
          .show();
      return;
    }
    applyLive(w);
  }

  private void applyLive(Wallpaper w) {
    pendingApply = w.id;
    Settings.of(this).edit().putString("candidate", w.id).putString("pending_apply", w.id).apply();
    Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
    intent.putExtra(
        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
        new ComponentName(this, LivelyWallpaperService.class));
    try {
      startActivityForResult(intent, APPLY);
    } catch (ActivityNotFoundException e) {
      pendingApply = null;
      Settings.of(this).edit().remove("pending_apply").apply();
      message(
          "Wallpaper picker unavailable",
          "This Android device has no live wallpaper picker. Enable live wallpaper support in the"
              + " system or launcher.");
    }
  }

  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == IMPORT && result == RESULT_OK && data != null && data.getData() != null)
      importFile(data.getData());
    if (request == APPLY) {
      if (result == RESULT_OK && pendingApply != null) {
        Settings.of(this)
            .edit()
            .putString("active", pendingApply)
            .putBoolean("paused", false)
            .remove("pending_apply")
            .apply();
        toast("Wallpaper set");
      } else {
        Settings.of(this).edit().remove("pending_apply").apply();
      }
      pendingApply = null;
      if (detailDialog != null) detailDialog.dismiss();
      build();
    }
  }

  private void addWallpaper() {
    new AlertDialog.Builder(this)
        .setTitle("Add wallpaper")
        .setItems(
            new String[] {
              "Choose video, image, GIF or Lively ZIP",
              "Enter a website URL",
              "Browse wallpaper documentation"
            },
            (d, n) -> {
              if (n == 0) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.setType("*/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                  startActivityForResult(i, IMPORT);
                } catch (Exception e) {
                  error(e);
                }
              } else if (n == 1) {
                LinearLayout form = column();
                form.setPadding(dp(20), 0, dp(20), 0);
                EditText url = new EditText(this);
                url.setHint("https://example.com/wallpaper");
                url.setInputType(17);
                EditText title = new EditText(this);
                title.setHint("Wallpaper title");
                add(form, url, -1, 56);
                add(form, title, -1, 56);
                AlertDialog dialog =
                    new AlertDialog.Builder(this)
                        .setTitle("Website wallpaper")
                        .setMessage(
                            "Web wallpapers use the Android WebView and may access the internet.")
                        .setView(form)
                        .setPositiveButton("Add", null)
                        .setNegativeButton("Cancel", null)
                        .create();
                dialog.show();
                dialog
                    .getButton(-1)
                    .setOnClickListener(
                        v -> {
                          try {
                            Wallpaper w =
                                library.addUrl(
                                    url.getText().toString().trim(),
                                    title.getText().toString().trim());
                            dialog.dismiss();
                            build();
                            showDetail(w);
                          } catch (Exception e) {
                            error(e);
                          }
                        });
              } else open("https://github.com/lively-community/lively/wiki/Creating-Wallpapers");
            })
        .show();
  }

  private void importFile(Uri uri) {
    runJob(
        "Importing wallpaper",
        () -> library.importUri(uri),
        value -> {
          build();
          showDetail((Wallpaper) value);
        });
  }

  private interface Job {
    Object run() throws Exception;
  }

  private interface Finished {
    void done(Object result);
  }

  private void runJob(String label, Job job, Finished callback) {
    if (operation != null && !operation.isDone()) {
      toast("Finish the current operation first");
      return;
    }
    LinearLayout body = row();
    body.setPadding(dp(22), dp(20), dp(22), dp(20));
    add(body, new ProgressBar(this), 40, 40);
    TextView hint = text(label, 15, fg, false);
    hint.setPadding(dp(16), 0, 0, 0);
    add(body, hint, -1, 48);
    progress =
        new AlertDialog.Builder(this)
            .setView(body)
            .setCancelable(false)
            .setNegativeButton(
                "Cancel",
                (d, w) -> {
                  if (operation != null) operation.cancel(true);
                })
            .create();
    progress.show();
    operation =
        work.submit(
            () -> {
              try {
                Object value = job.run();
                if (Thread.currentThread().isInterrupted()) return;
                main.post(
                    () -> {
                      if (progress != null) progress.dismiss();
                      if (!isFinishing() && !isDestroyed()) callback.done(value);
                    });
              } catch (Exception e) {
                main.post(
                    () -> {
                      if (progress != null) progress.dismiss();
                      if (!isFinishing() && !isDestroyed()) error(e);
                    });
              }
            });
  }

  private void share(Wallpaper w) {
    runJob(
        "Preparing Lively package",
        () -> library.export(w),
        result -> {
          File file = (File) result;
          Uri uri = Uri.parse("content://" + getPackageName() + ".share/" + file.getName());
          Intent intent =
              new Intent(Intent.ACTION_SEND)
                  .setType("application/zip")
                  .putExtra(Intent.EXTRA_STREAM, uri)
                  .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
          intent.setClipData(ClipData.newRawUri("Lively wallpaper", uri));
          try {
            startActivity(Intent.createChooser(intent, "Share wallpaper package"));
          } catch (Exception e) {
            error(e);
          }
        });
  }

  private void customise(Wallpaper w) {
    LinearLayout options = column();
    options.setPadding(dp(20), dp(6), dp(20), dp(20));
    if (w.builtin() || w.kind.equals("video")) {
      floatSlider(
          options,
          "Playback speed",
          .25f,
          2f,
          Settings.value(this, w, "speed", 1),
          value -> {
            Settings.of(this).edit().putFloat(w.id + ".speed", value).apply();
          });
    }
    if (w.builtin()) {
      floatSlider(
          options,
          "Brightness",
          .2f,
          1.6f,
          Settings.value(this, w, "intensity", 1),
          value -> Settings.of(this).edit().putFloat(w.id + ".intensity", value).apply());
      if (w.scene >= 6)
        add(
            options,
            button(
                "Accent colour",
                () ->
                    choose(
                        "Accent colour",
                        new String[] {"Blue", "Teal", "Rose", "Amber", "White"},
                        0,
                        index ->
                            Settings.of(this)
                                .edit()
                                .putInt(
                                    w.id + ".accent",
                                    new int[] {
                                          0xff59a8fa, 0xff61c8b1, 0xfff19ab0, 0xffefbd7c, 0xffe3e7ec
                                        }
                                        [index])
                                .apply()),
                false),
            -1,
            48);
    }
    JSONObject props = library.properties(w);
    Iterator<String> keys = props.keys();
    int rendered = 0;
    while (keys.hasNext() && rendered < 60) {
      String key = keys.next();
      JSONObject p = props.optJSONObject(key);
      if (p == null) continue;
      String type = p.optString("type"), label = p.optString("text", key);
      String pref = w.id + ".property." + key;
      Object original = p.opt("value");
      Object value = original;
      try {
        String saved = Settings.of(this).getString(pref, null);
        if (saved != null) value = new JSONTokener(saved).nextValue();
      } catch (Exception ignored) {
      }
      if (type.equals("slider")) {
        float current = value instanceof Number ? ((Number) value).floatValue() : 0;
        floatSlider(
            options,
            label,
            (float) p.optDouble("min", 0),
            (float) p.optDouble("max", 100),
            current,
            v -> {
              Settings.of(this).edit().putString(pref, String.valueOf(v)).apply();
            });
        rendered++;
      } else if (type.equals("checkbox")) {
        boolean checked = Boolean.TRUE.equals(value);
        Switch toggle = new Switch(this);
        toggle.setText(label);
        toggle.setTextColor(fg);
        toggle.setChecked(checked);
        toggle.setPadding(0, dp(10), 0, dp(10));
        toggle.setOnCheckedChangeListener(
            (b, on) -> Settings.of(this).edit().putString(pref, String.valueOf(on)).apply());
        add(options, toggle, -1, 60);
        rendered++;
      } else if (type.equals("dropdown")) {
        JSONArray choices = p.optJSONArray("items");
        if (choices != null) {
          String[] labels = new String[choices.length()];
          for (int i = 0; i < labels.length; i++) labels[i] = choices.optString(i);
          int selected = value instanceof Number ? ((Number) value).intValue() : 0;
          add(
              options,
              button(
                  label,
                  () ->
                      choose(
                          label,
                          labels,
                          selected,
                          v -> Settings.of(this).edit().putString(pref, String.valueOf(v)).apply()),
                  false),
              -1,
              56);
          rendered++;
        }
      } else if (type.equals("textbox") || type.equals("color")) {
        EditText input = new EditText(this);
        input.setText(value == null ? "" : String.valueOf(value));
        input.setHint(label);
        input.setTextColor(fg);
        input.setContentDescription(label);
        add(options, input, -1, 56);
        input.addTextChangedListener(
            new TextWatcher() {
              public void beforeTextChanged(CharSequence s, int st, int n, int a) {}

              public void onTextChanged(CharSequence s, int st, int before, int count) {
                Settings.of(MainActivity.this)
                    .edit()
                    .putString(pref, JSONObject.quote(s.toString()))
                    .apply();
              }

              public void afterTextChanged(Editable e) {}
            });
        rendered++;
      }
    }
    if (!w.builtin() && rendered == 0 && !w.kind.equals("video"))
      add(
          options,
          text(
              w.kind.equals("web")
                  ? "This wallpaper has no LivelyProperties controls. Display and web settings are"
                      + " in Settings."
                  : "Use Settings → Wallpaper to change the image fit.",
              14,
              muted,
              false),
          -1,
          90);
    add(
        options,
        button(
            "Reset customisation",
            () -> {
              android.content.SharedPreferences.Editor e = Settings.of(this).edit();
              for (String key : Settings.of(this).getAll().keySet())
                if (key.startsWith(w.id + ".")) e.remove(key);
              e.apply();
              toast("Customisation reset");
            },
            false),
        -1,
        48);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(options);
    new AlertDialog.Builder(this)
        .setTitle("Customise " + w.title)
        .setView(scroll)
        .setPositiveButton("Done", null)
        .show();
  }

  private interface FloatChanged {
    void set(float value);
  }

  private interface IntChanged {
    void set(int value);
  }

  private void floatSlider(
      LinearLayout parent, String name, float min, float max, float current, FloatChanged changed) {
    TextView label = text(name + " • " + String.format(Locale.ROOT, "%.2f", current), 14, fg, true);
    add(parent, label, -1, 40);
    SeekBar bar = new SeekBar(this);
    bar.setMax(100);
    bar.setProgress((int) ((current - min) / Math.max(.001f, max - min) * 100));
    bar.setContentDescription(name);
    bar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar s, int progress, boolean user) {
            if (user) {
              float v = min + progress * (max - min) / 100;
              label.setText(name + " • " + String.format(Locale.ROOT, "%.2f", v));
              changed.set(v);
            }
          }

          public void onStartTrackingTouch(SeekBar s) {}

          public void onStopTrackingTouch(SeekBar s) {}
        });
    add(parent, bar, -1, 48);
  }

  private void choose(String title, String[] options, int current, IntChanged change) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setSingleChoiceItems(
            options,
            Math.max(0, Math.min(options.length - 1, current)),
            (d, w) -> {
              change.set(w);
              d.dismiss();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void settings() {
    chart = null;
    memoryText = null;
    ScrollView scroll = new ScrollView(this);
    scroll.setClipToPadding(false);
    scroll.setPadding(dp(20), dp(130), dp(20), dp(24));
    LinearLayout body = column();
    scroll.addView(body);
    content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> captureGlass());
    android.content.SharedPreferences p = Settings.of(this);
    if (settingCategory.equals("General")) {
      section(body, "Appearance");
      choiceRow(
          body,
          "Theme",
          "Follow system, light or dark",
          new String[] {"System", "Light", "Dark"},
          new String[] {"system", "light", "dark"},
          "theme",
          "system",
          true);
      toggle(body, "Liquid Glass", "Refracted navigation with clear text", "glass", true, true);
      toggle(body, "Reduce motion", "Use immediate UI transitions", "reduce_motion", false, false);
      section(body, "Library");
      toggle(
          body,
          "Wallpaper rotation",
          "Cycle favourites, or all wallpapers if fewer than two favourites",
          "rotation",
          false,
          false);
      intChoice(
          body,
          "Rotation interval",
          "Changes while your wallpaper is visible",
          new String[] {"15 minutes", "30 minutes", "1 hour", "4 hours", "1 day"},
          new int[] {15, 30, 60, 240, 1440},
          "rotation_minutes",
          60);
      toggle(
          body,
          "Double tap to change",
          "Double tap an empty area of the home screen",
          "double_tap",
          false,
          false);
      actionRow(
          body,
          "Add playback tile",
          "Pause and resume from Quick Settings",
          () -> {
            if (Build.VERSION.SDK_INT >= 33) {
              android.app.StatusBarManager manager =
                  getSystemService(android.app.StatusBarManager.class);
              manager.requestAddTileService(
                  new ComponentName(this, PauseTileService.class),
                  "Lively playback",
                  android.graphics.drawable.Icon.createWithResource(
                      this, io.lively.android.R.drawable.ic_pause),
                  getMainExecutor(),
                  result ->
                      toast(
                          result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                              ? "Tile added"
                              : "Manage tiles in Quick Settings"));
            } else
              message(
                  "Quick Settings",
                  "Edit your Quick Settings tiles and drag Lively playback into your panel.");
          });
    } else if (settingCategory.equals("Performance")) {
      section(body, "Playback rules");
      toggle(
          body,
          "Pause on Battery Saver",
          "Stops animation while Battery Saver is enabled",
          "pause_saver",
          true,
          false);
      toggle(
          body,
          "Pause on battery power",
          "Animate only while connected to a charger",
          "pause_battery",
          false,
          false);
      toggle(
          body,
          "Pause when hot",
          "Stops at Android's severe thermal level",
          "pause_thermal",
          true,
          false);
      intChoice(
          body,
          "Low battery threshold",
          "Pause below this level unless charging",
          new String[] {"Off", "5%", "10%", "15%", "20%", "30%"},
          new int[] {0, 5, 10, 15, 20, 30},
          "low_battery",
          15);
      section(body, "Rendering");
      intChoice(
          body,
          "Scene frame rate",
          "Native scenes. Videos retain their source frame rate.",
          new String[] {"15 fps", "24 fps", "30 fps", "60 fps"},
          new int[] {15, 24, 30, 60},
          "fps",
          30);
      intChoice(
          body,
          "Render resolution",
          "Longest edge for scenes, images and web. Video decoding retains the source.",
          new String[] {
            "720 px • economy", "1080 px", "1920 px • balanced", "2560 px", "3840 px • 4K"
          },
          new int[] {720, 1080, 1920, 2560, 3840},
          "resolution",
          1920);
      section(body, "Live memory");
      memoryText = text("Sampling app process memory…", 14, fg, true);
      add(body, memoryText, -1, 44);
      chart = new MemoryChart(this, muted);
      add(body, chart, -1, 154);
      add(
          body,
          text(
              "Process PSS, sampled once per second while this page is open. System decoder and"
                  + " WebView child processes are separate. Hidden wallpaper resources are released"
                  + " after 15 seconds.",
              12,
              muted,
              false),
          -1,
          110);
      main.removeCallbacks(sampleMemory);
      main.post(sampleMemory);
    } else if (settingCategory.equals("Wallpaper")) {
      section(body, "Display");
      choiceRow(
          body,
          "Choose a fit",
          "Image fit. Videos support crop or fit; stretch uses fit.",
          new String[] {"Crop to fill", "Fit inside", "Stretch images"},
          new String[] {"crop", "fit", "stretch"},
          "fit",
          "crop",
          false);
      toggle(
          body,
          "Home screen parallax",
          "Follows launcher page offsets on supported launchers",
          "parallax",
          true,
          false);
      section(body, "Audio");
      toggle(
          body,
          "Video sound",
          "Muted by default. Sound stops when wallpaper is hidden.",
          "sound",
          false,
          false);
      intChoice(
          body,
          "Video volume",
          "Applies when video sound is on",
          new String[] {"10%", "20%", "40%", "60%", "80%", "100%"},
          new int[] {10, 20, 40, 60, 80, 100},
          "volume",
          20);
      section(body, "Web wallpapers");
      toggle(
          body,
          "External web resources",
          "Allow local packages to load HTTPS scripts, fonts and media",
          "web_network",
          false,
          false);
      actionRow(
          body,
          "Wallpaper compatibility",
          "Android equivalents of Lively's Windows features",
          this::compatibility);
    } else if (settingCategory.equals("Screensaver")) {
      section(body, "Android screensaver");
      actionRow(
          body,
          "Choose Lively screensaver",
          "Uses your current wallpaper while Android is dreaming",
          () -> {
            try {
              startActivity(new Intent(android.provider.Settings.ACTION_DREAM_SETTINGS));
            } catch (Exception e) {
              message(
                  "Screensaver unavailable",
                  "This device does not expose Android screensaver settings.");
            }
          });
      add(
          body,
          text(
              "In Android's screensaver settings, choose Lively and select when it starts."
                  + " Availability depends on the manufacturer. This app does not keep your screen"
                  + " awake in the background.",
              14,
              muted,
              false),
          -1,
          140);
    } else {
      section(body, "Device capabilities");
      add(body, text(deviceInfo(), 14, fg, false), -1, -2);
      section(body, "Diagnostics");
      actionRow(
          body,
          "Last playback error",
          p.getString("last_error", "No playback errors recorded"),
          () ->
              message(
                  "Playback diagnostic", p.getString("last_error", "No playback errors recorded")));
      actionRow(
          body,
          "Clear thumbnail cache",
          "Original wallpapers are kept",
          () -> {
            thumbs.evictAll();
            toast("Thumbnail cache cleared");
          });
      actionRow(
          body,
          "Open Android app settings",
          "Storage, battery policy and system permissions",
          () ->
              startActivity(
                  new Intent(
                      android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                      Uri.parse("package:" + getPackageName()))));
      actionRow(body, "About Lively Android", "Version 0.1.0 • GPL-3.0", this::about);
    }
  }

  private final Runnable sampleMemory =
      new Runnable() {
        public void run() {
          if (isFinishing()
              || isDestroyed()
              || !page.equals("Settings")
              || !settingCategory.equals("Performance")
              || chart == null) return;
          float mb = Debug.getPss() / 1024f;
          memoryText.setText(String.format(Locale.ROOT, "%.1f MiB • app process", mb));
          chart.add(mb);
          main.postDelayed(this, 1000);
        }
      };

  private void section(LinearLayout body, String name) {
    TextView t = text(name, 20, fg, true);
    t.setPadding(0, dp(20), 0, dp(8));
    add(body, t, -1, 56);
  }

  private void actionRow(LinearLayout body, String title, String desc, Runnable action) {
    LinearLayout r = row();
    r.setPadding(dp(14), dp(12), dp(10), dp(12));
    r.setBackground(clickable(panel, 8));
    LinearLayout labels = column();
    TextView name = text(title, 15, fg, true), hint = text(desc, 12, muted, false);
    hint.setPadding(0, dp(3), 0, 0);
    add(labels, name, -1, -2);
    add(labels, hint, -1, -2);
    r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    TextView chevron = text("›", 25, muted, false);
    chevron.setGravity(Gravity.CENTER);
    add(r, chevron, 26, 44);
    r.setOnClickListener(v -> action.run());
    r.setContentDescription(title + ". " + desc);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(6);
    body.addView(r, lp);
  }

  private void toggle(
      LinearLayout body, String title, String desc, String key, boolean fallback, boolean rebuild) {
    LinearLayout r = row();
    r.setPadding(dp(14), dp(12), dp(14), dp(12));
    r.setBackground(shape(panel, 8));
    LinearLayout labels = column();
    add(labels, text(title, 15, fg, true), -1, -2);
    TextView hint = text(desc, 12, muted, false);
    hint.setPadding(0, dp(4), dp(12), 0);
    add(labels, hint, -1, -2);
    r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    Switch sw = new Switch(this);
    sw.setContentDescription(title);
    sw.setChecked(Settings.of(this).getBoolean(key, fallback));
    sw.setMinHeight(dp(48));
    add(r, sw, -2, 48);
    sw.setOnCheckedChangeListener(
        (v, on) -> {
          Settings.of(this).edit().putBoolean(key, on).apply();
          if (rebuild) build();
        });
    r.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(6);
    body.addView(r, lp);
  }

  private void choiceRow(
      LinearLayout body,
      String title,
      String desc,
      String[] labels,
      String[] values,
      String key,
      String fallback,
      boolean rebuild) {
    String current = Settings.of(this).getString(key, fallback);
    int at = 0;
    for (int i = 0; i < values.length; i++) if (current.equals(values[i])) at = i;
    final int selected = at;
    actionRow(
        body,
        title,
        labels[at] + "\n" + desc,
        () ->
            choose(
                title,
                labels,
                selected,
                index -> {
                  Settings.of(this).edit().putString(key, values[index]).apply();
                  if (rebuild) build();
                  else settingsRebuild();
                }));
  }

  private void intChoice(
      LinearLayout body,
      String title,
      String desc,
      String[] labels,
      int[] values,
      String key,
      int fallback) {
    int current = Settings.of(this).getInt(key, fallback), at = 0;
    for (int i = 0; i < values.length; i++) if (current == values[i]) at = i;
    final int selected = at;
    actionRow(
        body,
        title,
        labels[at] + "\n" + desc,
        () ->
            choose(
                title,
                labels,
                selected,
                index -> {
                  Settings.of(this).edit().putInt(key, values[index]).apply();
                  settingsRebuild();
                }));
  }

  private void settingsRebuild() {
    content.removeAllViews();
    settings();
  }

  private void controlPanel() {
    Wallpaper active = library.find(Settings.active(this));
    LinearLayout body = column();
    body.setPadding(dp(22), dp(10), dp(22), dp(20));
    ImageView figure = new ImageView(this);
    try (InputStream in = getAssets().open("display-engine.png")) {
      figure.setImageBitmap(BitmapFactory.decodeStream(in));
    } catch (Exception ignored) {
    }
    figure.setContentDescription("Isometric display, decoder and memory modules");
    figure.setScaleType(ImageView.ScaleType.FIT_CENTER);
    add(body, figure, -1, 160);
    add(body, text(active.title, 24, fg, true), -1, 44);
    add(
        body,
        text(
            isLivelyActive() ? "Applied to your home screen" : "Lively is ready to apply",
            13,
            muted,
            false),
        -1,
        32);
    add(body, text(deviceInfo(), 13, muted, false), -1, -2);
    add(body, button("Preview active wallpaper", () -> showDetail(active), true), -1, 48);
    add(
        body,
        button(
            "Playback settings",
            () -> {
              settingCategory = "Performance";
              navigate("Settings");
            },
            false),
        -1,
        56);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(body);
    new AlertDialog.Builder(this)
        .setTitle("Active wallpapers")
        .setView(scroll)
        .setPositiveButton("Done", null)
        .show();
  }

  private String deviceInfo() {
    Display d = getWindowManager().getDefaultDisplay();
    Display.Mode mode = d.getMode();
    StringBuilder info =
        new StringBuilder(
            mode.getPhysicalWidth()
                + " × "
                + mode.getPhysicalHeight()
                + " display\n"
                + Math.round(mode.getRefreshRate())
                + " Hz • Android "
                + Build.VERSION.RELEASE
                + "\n");
    if (Build.VERSION.SDK_INT >= 24) {
      int[] hdr = d.getHdrCapabilities().getSupportedHdrTypes();
      info.append(
          hdr.length == 0
              ? "HDR display: not advertised\n"
              : "HDR display types: " + Arrays.toString(hdr) + "\n");
    }
    boolean hevc = false, avc = false;
    try {
      for (MediaCodecInfo codec :
          new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
        if (codec.isEncoder() || (Build.VERSION.SDK_INT >= 29 && !codec.isHardwareAccelerated()))
          continue;
        for (String type : codec.getSupportedTypes())
          if (type.equals("video/hevc") || type.equals("video/avc")) {
            MediaCodecInfo.VideoCapabilities v =
                codec.getCapabilitiesForType(type).getVideoCapabilities();
            boolean supported =
                v != null && (v.isSizeSupported(3840, 2160) || v.isSizeSupported(2160, 3840));
            if (type.equals("video/hevc")) hevc |= supported;
            else avc |= supported;
          }
      }
    } catch (Exception ignored) {
    }
    info.append(
        "4K decoder: "
            + (hevc ? "HEVC " : "")
            + (avc ? "H.264" : (!hevc ? "not advertised" : ""))
            + "\n");
    info.append(
        "Ultra HDR: "
            + (Build.VERSION.SDK_INT >= 34
                ? "gain-map decode / preview supported by Android"
                : "requires Android 14+")
            + "\nHDR wallpaper output depends on the system compositor.\n");
    return info.toString();
  }

  private void compatibility() {
    message(
        "Android compatibility",
        "Supported: native GPU scenes; original videos up to the device's decoding limits; images;"
            + " GIF and animated WebP; Lively web packages; HTTPS websites; LivelyProperties"
            + " sliders, checkboxes, dropdowns, text and colours; rotation; power rules; Android"
            + " screensavers; sharing.\n\n"
            + "Windows EXE, Unity/Godot executables, BizHawk, Windows multi-monitor spanning,"
            + " Windows tray/CLI and privileged desktop audio/hardware APIs cannot run on Android."
            + " YouTube pages are not a reliable background video source.\n\n"
            + "4K is source / render support, not a promise that every screen is 4K. Ultra HDR"
            + " images keep their original gain maps; the Android compositor decides wallpaper"
            + " output. Native preview requests HDR on supported displays.");
  }

  private void about() {
    new AlertDialog.Builder(this)
        .setTitle("Lively Wallpaper for Android")
        .setMessage(
            "Version 0.1.0 • independent Android adaptation\n\n"
                + "Inspired by Lively Wallpaper, originally created by rocksdanister / Dani John,"
                + " now maintained at lively-community/lively. This Android app is not an official"
                + " Lively release.\n\n"
                + "Native Android engine and responsive Lively library layout. GPL-3.0 source. No"
                + " ads, account, analytics, or bundled Windows runtime.\n\n"
                + "Design references: App Designer, Liquid Glass (MIT), Anatomy (MIT), and Arc"
                + " (MIT). Native adaptations keep the app small.")
        .setPositiveButton("Compatibility", (d, w) -> compatibility())
        .setNeutralButton(
            "Upstream project", (d, w) -> open("https://github.com/lively-community/lively"))
        .setNegativeButton("Close", null)
        .show();
  }

  private void open(String url) {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    } catch (Exception e) {
      message("No browser available", url);
    }
  }

  private void message(String title, String body) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(body)
        .setPositiveButton("OK", null)
        .show();
  }

  private void error(Exception e) {
    message(
        "Unable to complete action",
        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
  }

  private void toast(String message) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
  }

  protected void onPause() {
    super.onPause();
    if (preview != null) preview.visible(false);
    main.removeCallbacks(sampleMemory);
  }

  protected void onResume() {
    super.onResume();
    if (preview != null) preview.visible(true);
    if (chart != null) main.post(sampleMemory);
  }

  protected void onDestroy() {
    closePreview();
    main.removeCallbacksAndMessages(null);
    if (operation != null) operation.cancel(true);
    work.shutdownNow();
    if (backdrop != null) backdrop.recycle();
    super.onDestroy();
  }
}
