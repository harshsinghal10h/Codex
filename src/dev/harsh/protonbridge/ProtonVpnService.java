package dev.harsh.protonbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import com.wireguard.android.backend.GoBackend;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Native Android VPN using the original Brave WireGuard JNI implementation. */
public final class ProtonVpnService extends VpnService {
    public static final String CONNECT = "dev.harsh.protonbridge.CONNECT";
    public static final String DISCONNECT = "dev.harsh.protonbridge.DISCONNECT";
    static final String PREFS = "personal_proton_v1";
    private static final String CHANNEL = "personal_proton_connection";
    private static final int NOTICE = 18632;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> monitor;
    private int handle = -1;
    private WireGuardProfile active;
    private long startedElapsed, lastNotice;
    private volatile boolean destroying;

    public static final class Snapshot {
        public final String state, profile, detail;
        public final long rx, tx, handshakeMillis;
        public final boolean alwaysOn;
        Snapshot(String state, String profile, String detail, long rx, long tx, long handshakeMillis) {
            this(state,profile,detail,rx,tx,handshakeMillis,false);
        }
        Snapshot(String state, String profile, String detail, long rx, long tx, long handshakeMillis,
                 boolean alwaysOn) {
            this.state=state; this.profile=profile; this.detail=detail;
            this.rx=rx; this.tx=tx; this.handshakeMillis=handshakeMillis;
            this.alwaysOn=alwaysOn;
        }
        public boolean running() {
            return state.equals("Connecting") || state.equals("Connected")
                    || state.equals("Waiting for server") || state.equals("Switching server");
        }
    }
    private static volatile Snapshot snapshot = new Snapshot("Disconnected","",
            "Choose a server, then connect.",0,0,0);
    public static Snapshot snapshot() { return snapshot; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String action = intent == null ? null : intent.getAction();
        if (DISCONNECT.equals(action)) {
            prefs.edit().putBoolean("wanted",false).apply();
            worker.execute(() -> {
                teardown();
                snapshot = new Snapshot("Disconnected","","VPN is off.",0,0,0);
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
            });
            return START_NOT_STICKY;
        }
        boolean systemAlwaysOn = intent != null && SERVICE_INTERFACE.equals(action);
        if (intent == null && !prefs.getBoolean("wanted",false)) { stopSelf(); return START_NOT_STICKY; }
        if (!CONNECT.equals(action) && !systemAlwaysOn && intent != null && action != null) {
            stopSelf(); return START_NOT_STICKY;
        }
        String id = intent == null ? null : intent.getStringExtra("profile");
        if (id == null) id = prefs.getString("selected","jp");
        final String requested = id;
        final boolean allApps = prefs.getBoolean("all_apps",false);
        prefs.edit().putBoolean("wanted",true).putString("selected",requested).apply();
        snapshot = new Snapshot(handle >= 0 ? "Switching server" : "Connecting","",
                "Preparing the encrypted tunnel…",0,0,0);
        try {
            ensureNotificationChannel();
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(NOTICE, notification(snapshot), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
            else startForeground(NOTICE, notification(snapshot));
        } catch (RuntimeException e) {
            prefs.edit().putBoolean("wanted",false).apply();
            snapshot = new Snapshot("Connection failed","",
                    "Android did not allow the VPN foreground service. Reopen VPN settings and try again.",0,0,0);
            stopSelf(); return START_NOT_STICKY;
        }
        worker.execute(() -> connect(requested,allApps));
        return START_STICKY;
    }

    private void connect(String id, boolean allApps) {
        if (destroying) return;
        teardown();
        try {
            if (VpnService.prepare(this) != null) throw new Exception("permission");
            List<WireGuardProfile> profiles = new ProfileStore(this).load();
            WireGuardProfile profile = null;
            for (WireGuardProfile p : profiles) if (p.id.equals(id)) profile = p;
            if (profile == null) throw new Exception("profile");
            active = profile;
            snapshot = new Snapshot("Connecting",profile.label,
                    "Waiting for a WireGuard handshake…",0,0,0);
            System.loadLibrary("wg-go");
            Builder builder = new Builder().setSession("Proton Free · "+profile.label)
                    .setMtu(profile.mtu).setBlocking(true);
            if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false);
            if (!allApps) builder.addAllowedApplication(getPackageName());
            for (WireGuardProfile.Cidr a : profile.addresses) builder.addAddress(a.address,a.prefix);
            for (java.net.InetAddress dns : profile.dnsServers) builder.addDnsServer(dns);
            for (WireGuardProfile.Cidr r : profile.routes) {
                // With no IPv6 address, do not enable the IPv6 family: Android blocks it.
                // We deliberately never call allowFamily(), which could permit IPv6 bypass.
                if (r.address.getAddress().length == 16 && !profile.hasIpv6) continue;
                builder.addRoute(r.address,r.prefix);
            }
            Intent controls = new Intent(this,ProtonVpnActivity.class).setAction(Intent.ACTION_VIEW);
            builder.setConfigureIntent(PendingIntent.getActivity(this,6,controls,
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            ParcelFileDescriptor tun = builder.establish();
            if (tun == null) throw new Exception("tun");
            try { handle = GoBackend.wgTurnOn("ProtonFree",tun.detachFd(),profile.uapi()); }
            finally { tun.close(); }
            if (handle < 0) throw new Exception("native");
            int v4 = GoBackend.wgGetSocketV4(handle), v6 = GoBackend.wgGetSocketV6(handle);
            if (v4 >= 0 && !protect(v4)) throw new Exception("protect");
            if (v6 >= 0 && !protect(v6)) throw new Exception("protect");
            if (v4 < 0 && v6 < 0) throw new Exception("sockets");
            setUnderlyingNetworks(null);
            startedElapsed = SystemClock.elapsedRealtime(); lastNotice = 0;
            sendHandshakeProbe(profile);
            monitor = worker.scheduleWithFixedDelay(this::poll,0,2,TimeUnit.SECONDS);
        } catch (Exception | LinkageError e) {
            teardown();
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("wanted",false).apply();
            snapshot = new Snapshot("Connection failed","",
                    "Could not start the VPN. Check the profile and Android VPN permission, then retry.",0,0,0);
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        }
    }

    private void poll() {
        if (handle < 0 || active == null || destroying) return;
        try {
            TunnelStats s = TunnelStats.parse(GoBackend.wgGetConfig(handle));
            boolean fresh = s.isFresh(System.currentTimeMillis());
            String state = fresh ? "Connected" : (SystemClock.elapsedRealtime()-startedElapsed > 20000
                    ? "Waiting for server" : "Connecting");
            String detail = fresh ? (getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("all_apps",false)
                    ? "All apps use the encrypted tunnel." : "This Brave browser uses the encrypted tunnel.")
                    : "No recent handshake. The VPN route remains active; check your network or choose another server.";
            snapshot = new Snapshot(state,active.label,detail,s.rx,s.tx,s.handshakeMillis,isAlwaysOn());
            if (SystemClock.elapsedRealtime()-lastNotice >= 10000) {
                ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTICE,notification(snapshot));
                lastNotice = SystemClock.elapsedRealtime();
            }
        } catch (RuntimeException | LinkageError e) {
            snapshot = new Snapshot("Waiting for server",active.label,
                    "Unable to read tunnel status. Disconnect and retry.",0,0,0);
        }
    }

    private void sendHandshakeProbe(WireGuardProfile profile) {
        // A minimal DNS query goes to the configured Proton DNS through this app's VPN route.
        // It triggers the peer handshake without a third-party IP-check request.
        byte[] query = new byte[]{0x50,0x46,1,0,0,1,0,0,0,0,0,0,
                7,'e','x','a','m','p','l','e',3,'o','r','g',0,0,1,0,1};
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(new DatagramPacket(query,query.length,profile.dnsServers.get(0),53));
        } catch (Exception ignored) { /* The keepalive and ordinary browsing will retry. */ }
    }

    private void teardown() {
        if (monitor != null) { monitor.cancel(false); monitor = null; }
        if (handle >= 0) {
            int old = handle; handle = -1;
            try { GoBackend.wgTurnOff(old); } catch (RuntimeException | LinkageError ignored) { }
        }
        active = null;
    }
    private void ensureNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL,"Proton VPN connection",
                NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }
    private Notification notification(Snapshot s) {
        PendingIntent open = PendingIntent.getActivity(this,2,
                new Intent(this,ProtonVpnActivity.class).setAction(Intent.ACTION_VIEW),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = s.alwaysOn
                ? PendingIntent.getActivity(this,4,new Intent("android.net.vpn.SETTINGS"),
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)
                : PendingIntent.getService(this,3,
                    new Intent(this,ProtonVpnService.class).setAction(DISCONNECT),
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("Proton Free · "+s.state)
                .setContentText((s.profile.isEmpty() ? "" : s.profile+" · ")
                        +formatBytes(s.rx)+" received / "+formatBytes(s.tx)+" sent")
                .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
                .addAction(new Notification.Action.Builder(null,s.alwaysOn ? "VPN settings" : "Disconnect",stop).build()).build();
    }
    static String formatBytes(long n) {
        if (n < 1024) return n+" B";
        if (n < 1024L*1024) return String.format(java.util.Locale.ROOT,"%.1f KB",n/1024d);
        if (n < 1024L*1024*1024) return String.format(java.util.Locale.ROOT,"%.1f MB",n/(1024d*1024));
        return String.format(java.util.Locale.ROOT,"%.2f GB",n/(1024d*1024*1024));
    }
    @Override public void onRevoke() {
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("wanted",false).apply();
        snapshot = new Snapshot("Permission revoked","","Another VPN took over, or Android VPN permission was removed.",0,0,0);
        stopSelf(); super.onRevoke();
    }
    @Override public void onDestroy() {
        destroying = true;
        worker.execute(this::teardown); worker.shutdown();
        if (snapshot.running()) snapshot = new Snapshot("Disconnected","","VPN service stopped.",0,0,0);
        super.onDestroy();
    }
}
