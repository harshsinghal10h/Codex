package dev.harsh.protonbridge;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict parser for one standard Proton WireGuard peer. Errors never include key material. */
public final class WireGuardProfile {
    public static final int MAX_BYTES = 65536;
    public final String id;
    public final String label;
    public final List<Cidr> addresses;
    public final List<Cidr> routes;
    public final List<InetAddress> dnsServers;
    public final int mtu;
    public final boolean hasIpv6;
    private final String original;
    private final String privateKeyHex;
    private final String publicKeyHex;
    private final String presharedKeyHex;
    private final String endpoint;
    private final int keepalive;

    public static final class Cidr {
        public final InetAddress address;
        public final int prefix;
        Cidr(String text) throws Exception {
            String[] parts = text.trim().split("/", -1);
            if (parts.length != 2) throw new Exception("Address or route must include a prefix.");
            address = literalIp(parts[0]);
            prefix = number(parts[1], 0, address.getAddress().length * 8, "network prefix");
        }
        public String canonical() { return address.getHostAddress() + "/" + prefix; }
    }

    private WireGuardProfile(String id, String label, String text, Map<String,String> iface,
                             Map<String,String> peer) throws Exception {
        this.id = id;
        this.label = cleanLabel(label);
        original = text;
        privateKeyHex = key(required(iface, "privatekey"), "private key", false);
        publicKeyHex = key(required(peer, "publickey"), "server public key", false);
        presharedKeyHex = peer.containsKey("presharedkey")
                ? key(peer.get("presharedkey"), "preshared key", true) : null;
        List<Cidr> a = new ArrayList<>();
        boolean v6 = false;
        for (String item : required(iface, "address").split(",")) {
            Cidr c = new Cidr(item);
            if (c.prefix == 0) throw new Exception("An interface address needs a nonzero prefix.");
            a.add(c);
            v6 |= c.address.getAddress().length == 16;
        }
        addresses = Collections.unmodifiableList(a);
        hasIpv6 = v6;
        List<InetAddress> dns = new ArrayList<>();
        for (String item : required(iface, "dns").split(",")) {
            InetAddress ip = literalIp(item.trim());
            if (ip.getAddress().length == 16 && !hasIpv6)
                throw new Exception("IPv6 DNS needs an IPv6 interface address.");
            dns.add(ip);
        }
        dnsServers = Collections.unmodifiableList(dns);
        List<Cidr> r = new ArrayList<>();
        boolean all4 = false, all6 = false;
        for (String item : required(peer, "allowedips").split(",")) {
            Cidr c = new Cidr(item);
            if (c.prefix == 0 && isZero(c.address.getAddress())) {
                all4 |= c.address.getAddress().length == 4;
                all6 |= c.address.getAddress().length == 16;
            }
            r.add(c);
        }
        if (!all4 || !all6)
            throw new Exception("This integration requires full IPv4 and IPv6 AllowedIPs.");
        routes = Collections.unmodifiableList(r);
        String value = required(peer, "endpoint");
        String host, port;
        if (value.startsWith("[")) {
            int close = value.indexOf("]:");
            if (close < 0) throw new Exception("Invalid server endpoint.");
            host = value.substring(1, close);
            port = value.substring(close + 2);
        } else {
            int colon = value.lastIndexOf(':');
            if (colon < 1) throw new Exception("Server endpoint needs an IP and port.");
            host = value.substring(0, colon);
            port = value.substring(colon + 1);
        }
        InetAddress remote = literalIp(host);
        int serverPort = number(port, 1, 65535, "server port");
        endpoint = (remote.getAddress().length == 16 ? "["+remote.getHostAddress()+"]"
                : remote.getHostAddress()) + ":" + serverPort;
        keepalive = number(peer.getOrDefault("persistentkeepalive", "25"), 0, 65535,
                "keepalive");
        mtu = number(iface.getOrDefault("mtu", "1280"), 1280, 1500, "MTU");
        if (iface.containsKey("listenport"))
            number(iface.get("listenport"), 0, 65535, "listen port");
    }

    public static WireGuardProfile parse(String id, String label, String text) throws Exception {
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new Exception("The profile is empty or too large.");
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        Map<String,String> iface = new LinkedHashMap<>(), peer = new LinkedHashMap<>();
        Map<String,String> active = null;
        boolean seenInterface = false, seenPeer = false;
        for (String raw : text.split("\\r?\\n")) {
            int hash = raw.indexOf('#');
            String line = (hash >= 0 ? raw.substring(0, hash) : raw).trim();
            if (line.isEmpty()) continue;
            if (line.equalsIgnoreCase("[Interface]")) {
                if (seenInterface) throw new Exception("Only one interface is supported.");
                seenInterface = true; active = iface; continue;
            }
            if (line.equalsIgnoreCase("[Peer]")) {
                if (seenPeer) throw new Exception("Only one peer is supported per server profile.");
                seenPeer = true; active = peer; continue;
            }
            int split = line.indexOf('=');
            if (active == null || split < 1) throw new Exception("Invalid WireGuard configuration.");
            String k = line.substring(0, split).trim().toLowerCase(java.util.Locale.ROOT);
            String v = line.substring(split + 1).trim();
            String permitted = active == iface ? ",privatekey,address,dns,mtu,listenport,"
                    : ",publickey,presharedkey,endpoint,allowedips,persistentkeepalive,";
            if (!permitted.contains(","+k+","))
                throw new Exception("Unsupported configuration option; use a standard Proton profile.");
            if (v.isEmpty() || active.put(k, v) != null)
                throw new Exception("Empty or repeated WireGuard configuration field.");
        }
        if (!seenInterface || !seenPeer) throw new Exception("The profile needs Interface and Peer sections.");
        return new WireGuardProfile(id, label, text, iface, peer);
    }

    /** Native WireGuard UAPI; never log, persist in plaintext, or include in diagnostics. */
    String uapi() {
        StringBuilder out = new StringBuilder("private_key=").append(privateKeyHex)
                .append("\nreplace_peers=true\npublic_key=").append(publicKeyHex).append('\n');
        if (presharedKeyHex != null) out.append("preshared_key=").append(presharedKeyHex).append('\n');
        out.append("endpoint=").append(endpoint).append("\npersistent_keepalive_interval=")
                .append(keepalive).append("\nreplace_allowed_ips=true\n");
        for (Cidr r : routes) out.append("allowed_ip=").append(r.canonical()).append('\n');
        return out.toString();
    }
    String originalForEncryptedStorage() { return original; }
    public String displayEndpoint() { return endpoint; }

    private static String required(Map<String,String> map, String name) throws Exception {
        String value = map.get(name);
        if (value == null) throw new Exception("Required WireGuard field is missing: "+name+".");
        return value;
    }
    private static int number(String value, int min, int max, String name) throws Exception {
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            int n = Integer.parseInt(value);
            if (n < min || n > max) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) { throw new Exception("Invalid "+name+"."); }
    }
    private static String key(String value, String name, boolean allowZero) throws Exception {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(value); }
        catch (IllegalArgumentException e) { throw new Exception("Invalid "+name+"."); }
        if (bytes.length != 32 || (!allowZero && isZero(bytes))) throw new Exception("Invalid "+name+".");
        StringBuilder out = new StringBuilder(64);
        final char[] hex = "0123456789abcdef".toCharArray();
        for (byte b : bytes) { out.append(hex[(b&255) >>> 4]); out.append(hex[b&15]); }
        java.util.Arrays.fill(bytes, (byte)0);
        return out.toString();
    }
    private static boolean isZero(byte[] bytes) {
        int n = 0; for (byte b : bytes) n |= b; return n == 0;
    }
    static InetAddress literalIp(String text) throws Exception {
        try {
            if (text.contains(":")) {
                if (!text.matches("[0-9a-fA-F:]+")) throw new UnknownHostException();
                InetAddress a = InetAddress.getByName(text);
                if (a.getAddress().length != 16) throw new UnknownHostException();
                return a;
            }
            String[] parts = text.split("\\.", -1);
            if (parts.length != 4) throw new UnknownHostException();
            byte[] bytes = new byte[4];
            for (int i=0; i<4; i++) bytes[i] = (byte)number(parts[i], 0, 255, "IP address");
            return InetAddress.getByAddress(bytes);
        } catch (Exception e) { throw new Exception("A numeric IPv4 or IPv6 address is required."); }
    }
    static String cleanLabel(String input) {
        String s = input == null ? "Imported server" : input.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (s.isEmpty()) s = "Imported server";
        return s.length() > 64 ? s.substring(0,64) : s;
    }
}
