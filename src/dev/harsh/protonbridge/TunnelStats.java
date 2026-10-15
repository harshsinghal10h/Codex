package dev.harsh.protonbridge;

/** Extracts only nonsecret counters from native UAPI output. */
public final class TunnelStats {
    public final long rx, tx, handshakeMillis;
    private TunnelStats(long rx, long tx, long handshakeMillis) {
        this.rx = rx; this.tx = tx; this.handshakeMillis = handshakeMillis;
    }
    static TunnelStats parse(String uapi) {
        long rx=0, tx=0, sec=0, nano=0;
        if (uapi != null) for (String line : uapi.split("\n")) {
            int at = line.indexOf('=');
            if (at < 1) continue;
            String k = line.substring(0, at);
            if (!k.equals("rx_bytes") && !k.equals("tx_bytes")
                    && !k.equals("last_handshake_time_sec") && !k.equals("last_handshake_time_nsec")) continue;
            try {
                long v = Long.parseLong(line.substring(at+1));
                if (v < 0) continue;
                if (k.equals("rx_bytes")) rx = v;
                else if (k.equals("tx_bytes")) tx = v;
                else if (k.equals("last_handshake_time_sec")) sec = v;
                else nano = Math.min(v, 999999999L);
            } catch (NumberFormatException ignored) { }
        }
        long time = sec > 0 && sec < Long.MAX_VALUE/1000 ? sec*1000+nano/1000000 : 0;
        return new TunnelStats(rx, tx, time);
    }
    boolean isFresh(long nowMillis) {
        return handshakeMillis > 0 && nowMillis >= handshakeMillis-5000
                && nowMillis-handshakeMillis <= 180000;
    }
}
