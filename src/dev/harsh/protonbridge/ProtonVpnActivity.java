package dev.harsh.protonbridge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.ComponentName;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native replacement for Brave's paid VPN activities in this personal build. */
public final class ProtonVpnActivity extends Activity {
    private static final int CONSENT=651, IMPORT=652;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private List<WireGuardProfile> profiles = new ArrayList<>();
    private SharedPreferences prefs;
    private TextView stateText, detailText, transferText, handshakeText;
    private Button connectButton;
    private Switch scopeSwitch;
    private boolean loading = true;
    private String loadError = "";
    private int textColor, mutedColor, surfaceColor, backgroundColor;
    private static final int ORANGE = Color.rgb(234,78,43);
    private final Runnable refresh = new Runnable() {
        public void run() { updateStatus(); handler.postDelayed(this,1000); }
    };

    @Override public void onCreate(Bundle saved) {
        setTheme((getResources().getConfiguration().uiMode & 0x30) == 0x20
                ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(saved);
        prefs = getSharedPreferences(ProtonVpnService.PREFS,MODE_PRIVATE);
        buildScreen(); BraveVpnMenuHook.removeOldShortcut(this);
        io.execute(() -> {
            try {
                List<WireGuardProfile> result = new ProfileStore(this).load();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    profiles = result; loading = false; buildScreen();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    loading=false;
                    loadError="Profiles could not be loaded. Import your Proton .conf files below.";
                    buildScreen();
                });
            }
        });
        if (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},653);
    }
    @Override public void onResume() { super.onResume(); handler.post(refresh); }
    @Override public void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override public void onDestroy() { handler.removeCallbacks(refresh); io.shutdown(); super.onDestroy(); }

    private void buildScreen() {
        boolean dark = (getResources().getConfiguration().uiMode & 0x30) == 0x20;
        backgroundColor = dark ? Color.rgb(17,19,24) : Color.rgb(248,248,250);
        surfaceColor = dark ? Color.rgb(29,32,39) : Color.WHITE;
        textColor = dark ? Color.rgb(242,243,245) : Color.rgb(28,30,36);
        mutedColor = dark ? Color.rgb(174,179,190) : Color.rgb(94,102,116);
        getWindow().setStatusBarColor(backgroundColor);
        getWindow().setNavigationBarColor(backgroundColor);
        if (!dark) getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(backgroundColor); scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(12),dp(20),dp(24));
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));
        scroll.setOnApplyWindowInsetsListener((view,insets) -> {
            int top,bottom;
            if (Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                top=bars.top; bottom=bars.bottom;
            } else { top=insets.getSystemWindowInsetTop(); bottom=insets.getSystemWindowInsetBottom(); }
            root.setPadding(dp(20),dp(12)+top,dp(20),dp(24)+bottom); return insets;
        });
        Button back = button("‹  Back to Brave",false);
        back.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        back.setOnClickListener(v -> finish()); root.addView(back);
        TextView title=text("Proton Free VPN",30,textColor);
        title.setTypeface(null,1); add(root,title,18);
        add(root,text("Your browser. Your free VPN.",16,mutedColor),6);
        TextView badge=text("WIREGUARD  ·  NO SUBSCRIPTION",11,ORANGE);
        badge.setLetterSpacing(0.12f); add(root,badge,15);

        LinearLayout status=card();
        stateText=text("Loading…",24,textColor); stateText.setTypeface(null,1);
        status.addView(stateText);
        detailText=text("",14,mutedColor); add(status,detailText,8);
        transferText=text("",16,textColor); add(status,transferText,18);
        handshakeText=text("",12,mutedColor); add(status,handshakeText,5);
        add(root,status,22);

        TextView choose=text("Server",18,textColor); choose.setTypeface(null,1); add(root,choose,25);
        add(root,text(loading ? "Loading your profiles…" : "Select a supplied server or import another free profile.",13,mutedColor),5);
        RadioGroup servers=new RadioGroup(this);
        Map<Integer,String> ids=new HashMap<>();
        String selected=prefs.getString("selected","jp");
        boolean found=false;
        for (WireGuardProfile p:profiles) if (p.id.equals(selected)) found=true;
        if (!found && !profiles.isEmpty()) {
            selected=profiles.get(0).id; prefs.edit().putString("selected",selected).apply();
        }
        for (WireGuardProfile p:profiles) {
            RadioButton radio=new RadioButton(this);
            int viewId=View.generateViewId(); radio.setId(viewId); ids.put(viewId,p.id);
            String country=p.id.equals("jp") ? "Japan" : p.id.equals("us") ? "United States" : "Imported server";
            radio.setText(country+"\n"+p.label+"  ·  "+(p.hasIpv6 ? "IPv4 + IPv6" : "IPv4 · IPv6 blocked"));
            radio.setTextSize(15); radio.setTextColor(textColor); radio.setPadding(dp(12),dp(14),dp(12),dp(14));
            radio.setButtonTintList(android.content.res.ColorStateList.valueOf(ORANGE));
            radio.setBackground(rounded(surfaceColor,16));
            RadioGroup.LayoutParams rp=new RadioGroup.LayoutParams(-1,-2); rp.topMargin=dp(10);
            servers.addView(radio,rp);
            if (p.id.equals(selected)) servers.check(viewId);
            radio.setOnLongClickListener(v -> { removeProfile(p); return true; });
        }
        servers.setOnCheckedChangeListener((group,id) -> {
            String picked=ids.get(id); if (picked==null) return;
            prefs.edit().putString("selected",picked).apply();
            if (ProtonVpnService.snapshot().running()) requestConnect();
        });
        add(root,servers,2);

        LinearLayout scope=card();
        scopeSwitch=new Switch(this); scopeSwitch.setText("Protect all apps");
        scopeSwitch.setTextSize(16); scopeSwitch.setTextColor(textColor);
        scopeSwitch.setChecked(prefs.getBoolean("all_apps",false));
        scopeSwitch.setOnCheckedChangeListener((v,checked) -> prefs.edit().putBoolean("all_apps",checked).apply());
        scope.addView(scopeSwitch);
        add(scope,text("Off: only this Brave browser. Disconnect before changing scope.",12,mutedColor),8);
        add(root,scope,18);

        connectButton=button("Connect",true);
        connectButton.setOnClickListener(v -> {
            if (ProtonVpnService.snapshot().alwaysOn) openVpnSettings();
            else if (ProtonVpnService.snapshot().running())
                startService(new Intent(this,ProtonVpnService.class).setAction(ProtonVpnService.DISCONNECT));
            else requestConnect();
        });
        add(root,connectButton,20);
        Button browse=button("Open Brave browser",false);
        browse.setOnClickListener(v -> {
            Intent main=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(getPackageName());
            for (ResolveInfo result:getPackageManager().queryIntentActivities(main,0)) {
                if (result.activityInfo.name.equals(ProtonVpnActivity.class.getName())) continue;
                main.setComponent(new ComponentName(getPackageName(),result.activityInfo.name));
                main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(main); return;
            }
            finish();
        }); add(root,browse,7);

        LinearLayout settings=card();
        Button importButton=button("Import or update profiles",false);
        importButton.setOnClickListener(v -> {
            if (ProtonVpnService.snapshot().running()) {
                Toast.makeText(this,"Disconnect before replacing profiles.",Toast.LENGTH_SHORT).show(); return;
            }
            Intent chooseFile=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                    .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
            startActivityForResult(chooseFile,IMPORT);
        }); settings.addView(importButton);
        Button androidSettings=button("Android VPN / always-on settings",false);
        androidSettings.setOnClickListener(v -> openVpnSettings()); add(settings,androidSettings,2);
        add(settings,text("For a system kill switch, enable Always-on VPN and Block connections without VPN in Android settings. With Brave-only routing, lockdown can also block other apps; choose Protect all apps first if you want device-wide access.",12,mutedColor),12);
        add(root,settings,20);
        add(root,text("Free servers follow Proton's account limits and availability. There is no automatic access to paid countries or premium features.",12,mutedColor),20);
        add(root,text("Keep your Proton configuration files private. Profiles saved on this device use Android Keystore encryption.",12,mutedColor),12);
        add(root,text("Unofficial personal integration · Brave 1.97.56\nNot affiliated with Brave Software or Proton AG.\nLong-press a server to remove it.",11,mutedColor),17);
        setContentView(scroll); scroll.requestApplyInsets(); updateStatus();
    }

    private void requestConnect() {
        if (profiles.isEmpty() || loading) return;
        Intent consent=VpnService.prepare(this);
        if (consent!=null) startActivityForResult(consent,CONSENT); else startConnection();
    }
    private void startConnection() {
        Intent intent=new Intent(this,ProtonVpnService.class).setAction(ProtonVpnService.CONNECT)
                .putExtra("profile",prefs.getString("selected","jp"));
        try { startForegroundService(intent); }
        catch (RuntimeException e) {
            Toast.makeText(this,"Android could not start the VPN. Reopen this screen and try again.",Toast.LENGTH_LONG).show();
        }
        updateStatus();
    }
    private void openVpnSettings() {
        try { startActivity(new Intent("android.net.vpn.SETTINGS")); }
        catch (RuntimeException e) { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request==CONSENT) {
            if (result==RESULT_OK) startConnection();
            else Toast.makeText(this,"VPN permission was not granted.",Toast.LENGTH_SHORT).show();
        } else if (request==IMPORT && result==RESULT_OK && data!=null) {
            List<Uri> uris=new ArrayList<>();
            ClipData clip=data.getClipData();
            if (clip!=null) for (int i=0;i<clip.getItemCount();i++) uris.add(clip.getItemAt(i).getUri());
            else if (data.getData()!=null) uris.add(data.getData());
            if (uris.size()>20) {
                Toast.makeText(this,"Import up to 20 profiles at once.",Toast.LENGTH_SHORT).show(); return;
            }
            loading=true; updateStatus();
            List<WireGuardProfile> previous=new ArrayList<>(profiles);
            io.execute(() -> importProfiles(uris,previous));
        }
    }
    private void importProfiles(List<Uri> uris,List<WireGuardProfile> updated) {
        try {
            for (Uri uri:uris) {
                String name="Imported server";
                try (Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
                    if (cursor!=null && cursor.moveToFirst()) name=cursor.getString(0);
                }
                String label=name.replaceAll("(?i)(\\.conf)?\\.txt$|\\.conf$","");
                String upper=label.toUpperCase(Locale.ROOT);
                String id=upper.contains("JP-FREE") ? "jp" : upper.contains("US-FREE") ? "us" : UUID.randomUUID().toString();
                WireGuardProfile parsed;
                try (InputStream stream=getContentResolver().openInputStream(uri)) {
                    if (stream==null) throw new Exception();
                    parsed=WireGuardProfile.parse(id,label,ProfileStore.readLimited(stream,WireGuardProfile.MAX_BYTES));
                }
                for (int i=updated.size()-1;i>=0;i--) if (updated.get(i).id.equals(id)) updated.remove(i);
                updated.add(parsed);
            }
            if (updated.size()>20) throw new Exception();
            new ProfileStore(this).save(updated);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                profiles=updated; loading=false; loadError=""; buildScreen();
                Toast.makeText(this,"Profiles saved securely.",Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                loading=false; updateStatus();
                Toast.makeText(this,"Import failed. Select a valid Proton WireGuard .conf file. No profiles were changed.",Toast.LENGTH_LONG).show();
            });
        }
    }
    private void removeProfile(WireGuardProfile target) {
        if (ProtonVpnService.snapshot().running() || loading) {
            Toast.makeText(this,"Disconnect before removing a profile.",Toast.LENGTH_SHORT).show(); return;
        }
        new AlertDialog.Builder(this).setTitle("Remove "+target.label+"?")
                .setMessage("You can import this profile again later.")
                .setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w) -> {
                    List<WireGuardProfile> updated=new ArrayList<>(profiles);
                    updated.remove(target); loading=true; updateStatus();
                    io.execute(() -> {
                        try {
                            new ProfileStore(this).save(updated);
                            runOnUiThread(() -> { if (!isDestroyed()) { profiles=updated; loading=false; buildScreen(); } });
                        } catch (Exception e) {
                            runOnUiThread(() -> { loading=false; updateStatus();
                                Toast.makeText(this,"Could not remove this profile.",Toast.LENGTH_SHORT).show(); });
                        }
                    });
                }).show();
    }
    private void updateStatus() {
        if (stateText==null) return;
        ProtonVpnService.Snapshot s=ProtonVpnService.snapshot();
        stateText.setText(loading ? "Loading profiles…" : s.state);
        stateText.setTextColor(s.state.equals("Connected") ? Color.rgb(34,166,104) : textColor);
        detailText.setText(!loadError.isEmpty() ? loadError : s.detail
                +(s.profile.isEmpty() ? "" : "\n"+s.profile));
        transferText.setText("↓ "+ProtonVpnService.formatBytes(s.rx)+"    ↑ "+ProtonVpnService.formatBytes(s.tx));
        handshakeText.setText(s.handshakeMillis>0 ? "Last handshake: "+Math.max(0,
                (System.currentTimeMillis()-s.handshakeMillis)/1000)+" seconds ago" : "No verified handshake yet");
        connectButton.setText(s.alwaysOn ? "VPN settings" : s.running() ? "Disconnect" : "Connect");
        connectButton.setEnabled(!loading && (!profiles.isEmpty() || s.running()));
        connectButton.setAlpha(connectButton.isEnabled() ? 1f : 0.5f);
        scopeSwitch.setEnabled(!s.running());
    }
    private LinearLayout card() {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(18),dp(18),dp(18)); box.setBackground(rounded(surfaceColor,20)); return box;
    }
    private TextView text(String value,float size,int color) {
        TextView view=new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setLineSpacing(dp(3),1f); return view;
    }
    private Button button(String label,boolean primary) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15);
        b.setMinHeight(dp(50)); b.setMinimumHeight(dp(50));
        b.setTextColor(primary ? Color.WHITE : textColor);
        b.setBackgroundTintList(null); b.setBackground(rounded(primary ? ORANGE : surfaceColor,16));
        b.setPadding(dp(16),dp(10),dp(16),dp(10)); return b;
    }
    private GradientDrawable rounded(int color,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private void add(LinearLayout box,View view,int margin) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(margin); box.addView(view,p);
    }
    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
