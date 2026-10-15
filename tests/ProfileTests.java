package dev.harsh.protonbridge;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

public class ProfileTests {
    private static int checked;
    private static void check(boolean ok,String what) {
        checked++; if (!ok) throw new AssertionError(what);
    }
    private static String sample() {
        byte[] a=new byte[32],b=new byte[32];
        for (int i=0;i<32;i++) { a[i]=(byte)(i+1); b[i]=(byte)(i+41); }
        return "[Interface]\nPrivateKey = "+Base64.getEncoder().encodeToString(a)
                +"\nAddress=10.2.0.2/32\nDNS=10.2.0.1\n[Peer]\nPublicKey="
                +Base64.getEncoder().encodeToString(b)+"\nAllowedIPs=0.0.0.0/0, ::/0\n"
                +"Endpoint=192.0.2.10:51820\nPersistentKeepalive=25\n";
    }
    private static void reject(String cfg,String what) throws Exception {
        try { WireGuardProfile.parse("test","Test",cfg); throw new AssertionError(what); }
        catch (AssertionError e) { throw e; }
        catch (Exception e) {
            check(!e.getMessage().contains("PrivateKey ="),"no private material in validation errors");
        }
    }
    public static void main(String[] paths) throws Exception {
        String cfg=sample(); WireGuardProfile p=WireGuardProfile.parse("test","Test",cfg);
        check(p.addresses.size()==1,"interface address");
        check(p.dnsServers.get(0).getHostAddress().equals("10.2.0.1"),"Proton DNS retained");
        check(!p.hasIpv6,"IPv4-only interface detection");
        check(p.mtu==1280,"conservative MTU");
        check(p.uapi().contains("endpoint=192.0.2.10:51820\n"),"UDP port retained");
        check(p.uapi().contains("private_key=010203040506"),"base64-to-hex conversion");
        check(p.uapi().contains("persistent_keepalive_interval=25\n"),"keepalive retained");
        check(p.routes.size()==2,"full IPv4 and IPv6 coverage");
        String dual=cfg.replace("Address=10.2.0.2/32","Address=10.2.0.2/32, 2a07:b944::2:2/128")
                .replace("DNS=10.2.0.1","DNS=10.2.0.1, 2a07:b944::2:1")
                .replace("192.0.2.10:51820","[2001:db8::1]:51820");
        WireGuardProfile ipv6=WireGuardProfile.parse("dual","Dual",dual);
        check(ipv6.hasIpv6 && ipv6.addresses.size()==2,"dual-stack interface");
        check(ipv6.uapi().contains("endpoint=[2001:db8:0:0:0:0:0:1]:51820"),"IPv6 endpoint");
        reject(cfg.replace("0.0.0.0/0","192.0.2.0/24"),"partial IPv4 routes rejected");
        reject(cfg.replace("::/0","2001:db8::/32"),"partial IPv6 routes rejected");
        reject(cfg.replace("51820","70000"),"invalid endpoint port rejected");
        reject(cfg.replace("192.0.2.10:51820","vpn.example.org:51820"),"hostname endpoints rejected to avoid bootstrap DNS leaks");
        reject(cfg.replace("10.2.0.1","999.2.0.1"),"invalid DNS rejected");
        reject(cfg+"[Peer]\nPublicKey=x\n","multiple peers rejected");
        reject(cfg.replace("DNS=10.2.0.1","DNS=10.2.0.1\nPostUp=touch /tmp/test"),"shell hooks rejected");
        reject(cfg.replace("DNS=10.2.0.1","DNS=10.2.0.1\nDNS=8.8.8.8"),"duplicate values rejected");
        reject(cfg.replace("PrivateKey = "+cfg.split("PrivateKey = ")[1].split("\n")[0],
                "PrivateKey = "+Base64.getEncoder().encodeToString(new byte[32])),"zero key rejected");
        reject(cfg+new String(new char[70000]).replace('\0',' '),"oversize profile rejected");
        TunnelStats noHandshake=TunnelStats.parse("private_key=notlogged\nrx_bytes=0\ntx_bytes=42\nlast_handshake_time_sec=0\n");
        check(!noHandshake.isFresh(1000000),"interface-up is not connected");
        TunnelStats fresh=TunnelStats.parse("rx_bytes=100\ntx_bytes=200\nlast_handshake_time_sec=1000\nlast_handshake_time_nsec=500000000\n");
        check(fresh.rx==100 && fresh.tx==200,"transfer stats");
        check(fresh.handshakeMillis==1000500,"handshake time conversion");
        check(fresh.isFresh(1100000),"recent handshake connected");
        check(!fresh.isFresh(1300000),"stale handshake waiting");
        check(!fresh.isFresh(900000),"far-future handshake rejected");
        check(TunnelStats.parse("rx_bytes=bad\ntx_bytes=-1").tx==0,"malformed counters safe");
        for (String path:paths) {
            WireGuardProfile real=WireGuardProfile.parse("provided","Provided profile",
                    new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8));
            check(real.routes.size()>=2,"provided profile full routes");
            check(real.uapi().contains(":51820\n"),"provided profile server port");
            check(real.dnsServers.get(0).getHostAddress().equals("10.2.0.1"),"provided profile DNS");
        }
        System.out.println("PASS: "+checked+" assertions; "+paths.length+" supplied profiles checked. No credentials logged.");
    }
}
