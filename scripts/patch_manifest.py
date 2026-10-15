#!/usr/bin/env python3
"""Patch binary AXML without rebuilding or renumbering Chromium resources.

Original string indices, resource IDs, unmodified elements, and typed values stay intact.
Only the manifest, a new dex, and optional personal assets change in the APK.
"""
import argparse
import struct
from pathlib import Path
from zipfile import ZipFile

NS = "http://schemas.android.com/apk/res/android"
NO = 0xffffffff
ACTIVITY = "dev.harsh.protonbridge.ProtonVpnActivity"
SERVICE = "dev.harsh.protonbridge.ProtonVpnService"
PAID_SERVICES = {
    "org.chromium.chrome.browser.vpn.wireguard.WireguardService",
    "com.wireguard.android.backend.GoBackend$VpnService",
}


def chunks(data):
    at = 8
    while at < len(data):
        kind, header, size = struct.unpack_from("<HHI", data, at)
        if size < header or size < 8 or at + size > len(data):
            raise ValueError("Invalid AXML chunk")
        yield kind, data[at:at+size]
        at += size
    if at != len(data):
        raise ValueError("Invalid AXML length")


class Pool:
    def __init__(self, raw):
        self.raw = raw
        _, self.header, _ = struct.unpack_from("<HHI", raw)
        count, self.style_count, self.flags, start, styles = struct.unpack_from("<IIIII", raw, 8)
        self.styles = raw[styles:] if styles else b""
        self.style_offsets = list(struct.unpack_from("<" + "I"*self.style_count,
                                                    raw, self.header+4*count))
        self.strings = []
        self.utf8 = bool(self.flags & 0x100)
        for offset in struct.unpack_from("<"+"I"*count, raw, self.header):
            at = start+offset
            if self.utf8:
                _, at = self.read_length(raw, at, 1)
                length, at = self.read_length(raw, at, 1)
                self.strings.append(raw[at:at+length].decode("utf-8"))
            else:
                length, at = self.read_length(raw, at, 2)
                self.strings.append(raw[at:at+2*length].decode("utf-16le"))
        self.index = {}
        for i, s in enumerate(self.strings):
            self.index.setdefault(s, i)

    @staticmethod
    def read_length(data, at, width):
        if width == 1:
            a = data[at]; at += 1
            if a & 0x80:
                a = ((a & 0x7f) << 8) | data[at]; at += 1
        else:
            a = struct.unpack_from("<H", data, at)[0]; at += 2
            if a & 0x8000:
                a = ((a & 0x7fff) << 16) | struct.unpack_from("<H", data, at)[0]; at += 2
        return a, at

    @staticmethod
    def length(n, width):
        if width == 1:
            return bytes([n]) if n < 0x80 else bytes([0x80 | (n >> 8), n & 255])
        return struct.pack("<H", n) if n < 0x8000 else struct.pack("<HH", 0x8000 | (n >> 16), n & 65535)

    def add(self, text):
        if text not in self.index:
            self.index[text] = len(self.strings); self.strings.append(text)
        return self.index[text]

    def emit(self):
        encoded = bytearray(); offsets = []
        for s in self.strings:
            offsets.append(len(encoded))
            units = len(s.encode("utf-16le"))//2
            if self.utf8:
                b = s.encode("utf-8")
                encoded += self.length(units, 1) + self.length(len(b), 1) + b + b"\x00"
            else:
                encoded += self.length(units, 2) + s.encode("utf-16le") + b"\x00\x00"
        encoded += b"\x00" * ((-len(encoded)) % 4)
        start = self.header + 4*(len(offsets)+self.style_count)
        styles_start = start+len(encoded) if self.style_count else 0
        size = start+len(encoded)+len(self.styles)
        header = struct.pack("<HHIIIIII",1,self.header,size,len(offsets),self.style_count,
                             self.flags & ~1,start,styles_start)
        header += self.raw[28:self.header]
        return header + struct.pack("<"+"I"*len(offsets),*offsets) \
            + struct.pack("<"+"I"*self.style_count,*self.style_offsets) + encoded + self.styles


class Manifest:
    def __init__(self, binary):
        if struct.unpack_from("<HHI",binary) != (3,8,len(binary)):
            raise ValueError("Not a supported binary manifest")
        self.parts = list(chunks(binary))
        self.pool = Pool(next(raw for kind,raw in self.parts if kind == 1))
        resource = next(raw for kind,raw in self.parts if kind == 0x180)
        self.resource_ids = list(struct.unpack_from("<"+"I"*((len(resource)-8)//4),resource,8))
        self.attrs = {self.pool.strings[i]:i for i,v in enumerate(self.resource_ids) if v}
        self.namespace = self.pool.add(NS)

    def attr(self, name, value, kind=3):
        if name not in self.attrs:
            raise ValueError("Attribute missing from original framework map: "+name)
        data = self.pool.add(value) if kind == 3 else value
        raw = data if kind == 3 else NO
        return struct.pack("<IIIHBBI",self.namespace,self.attrs[name],raw,8,0,kind,data)

    def start(self, name, attrs):
        # Attribute names are kept in their existing Android resource map.
        def resource_key(a):
            index = struct.unpack_from("<I",a,4)[0]
            return self.resource_ids[index] if index < len(self.resource_ids) else 0
        attrs = sorted(attrs, key=resource_key)
        size = 36+20*len(attrs)
        return struct.pack("<HHIIIIIHHHHHH",0x102,16,size,0,NO,NO,self.pool.add(name),
                           20,20,len(attrs),0,0,0)+b"".join(attrs)

    def end(self, name):
        return struct.pack("<HHIIIII",0x103,16,24,0,NO,NO,self.pool.add(name))

    def attributes(self, raw):
        attr_start, attr_size, count = struct.unpack_from("<HHH",raw,24)
        if attr_size != 20:
            raise ValueError("Unexpected attribute encoding")
        return [raw[16+attr_start+i*attr_size:16+attr_start+(i+1)*attr_size] for i in range(count)]

    def value(self, attrs, name):
        for a in attrs:
            _, n, r, _, _, kind, data = struct.unpack("<IIIHBBI",a)
            if self.pool.strings[n] == name:
                return self.pool.strings[data] if kind == 3 else data
        return None

    def change(self, raw, replace):
        attrs = self.attributes(raw)
        changed = []
        for a in attrs:
            name = self.pool.strings[struct.unpack_from("<I",a,4)[0]]
            if name in replace:
                kind,value = replace.pop(name)
                changed.append(self.attr(name,value,kind))
            else: changed.append(a)
        changed.extend(self.attr(name,value,kind) for name,(kind,value) in replace.items())
        # Existing special attr indices are zero in the application/root/service nodes we patch.
        name_idx = struct.unpack_from("<I",raw,20)[0]
        out = self.start(self.pool.strings[name_idx],changed)
        return out[:8] + raw[8:16] + out[16:]

    def injected(self):
        activity = self.start("activity",[
            self.attr("name",ACTIVITY),self.attr("label","Proton Free VPN"),
            self.attr("exported",0,0x12),self.attr("theme",16974382,1)])
        activity += self.end("activity")
        service = self.start("service",[
            self.attr("name",SERVICE),self.attr("permission","android.permission.BIND_VPN_SERVICE"),
            self.attr("exported",0,0x12),self.attr("foregroundServiceType",1024,0x11)])
        service += self.start("intent-filter",[]) + self.start("action",[
            self.attr("name","android.net.VpnService")]) + self.end("action") + self.end("intent-filter")
        service += self.start("meta-data",[
            self.attr("name","android.net.VpnService.SUPPORTS_ALWAYS_ON"),
            self.attr("value",1,0x12)]) + self.end("meta-data") + self.end("service")
        return activity+service

    def patch(self, launcher_names):
        body = []; stack = []; permissions_added=False; aliases=0
        for kind,raw in self.parts:
            if kind in (1,0x180): continue
            if kind == 0x102:
                tag = self.pool.strings[struct.unpack_from("<I",raw,20)[0]]
                attrs = self.attributes(raw)
                name = self.value(attrs,"name")
                new_tag = tag
                if tag == "manifest":
                    raw = self.change(raw,{"versionName":(3,"1.97.56-proton.2"),
                                           "versionCode":(0x10,429705606)})
                elif tag == "application":
                    if not permissions_added:
                        body += [self.start("uses-permission",[
                            self.attr("name","android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED")]),
                            self.end("uses-permission")]
                        permissions_added=True
                    raw = self.change(raw,{"label":(3,"Brave + Proton")})
                elif tag == "activity" and isinstance(name,str) and name.startswith("org.chromium.chrome.browser.vpn."):
                    new_tag="activity-alias"; aliases+=1
                    raw=self.start(new_tag,[self.attr("name",name),self.attr("targetActivity",ACTIVITY),
                                           self.attr("label","Proton Free VPN"),self.attr("exported",0,0x12)])
                elif tag == "service" and name in PAID_SERVICES:
                    raw=self.change(raw,{"enabled":(0x12,0)})
                elif tag == "receiver" and isinstance(name,str) and name.startswith("org.chromium.chrome.browser.vpn."):
                    raw=self.change(raw,{"enabled":(0x12,0)})
                elif tag in ("activity","activity-alias") and name in launcher_names:
                    raw=self.change(raw,{"label":(3,"Brave + Proton")})
                stack.append((tag,new_tag)); body.append(raw)
                if tag == "application": body.append(self.injected())
            elif kind == 0x103:
                old,new=stack.pop()
                if old != new: raw=self.end(new)
                body.append(raw)
            else: body.append(raw)
        if stack or aliases != 8:
            raise ValueError("Unexpected Brave VPN activity set; refusing a partial integration")
        resources = struct.pack("<HHI",0x180,8,8+4*len(self.resource_ids)) \
            +struct.pack("<"+"I"*len(self.resource_ids),*self.resource_ids)
        output=self.pool.emit()+resources+b"".join(body)
        return struct.pack("<HHI",3,8,8+len(output))+output


def patch_bytes(binary):
    from loguru import logger
    logger.remove()
    from androguard.core.axml import AXMLPrinter
    root=AXMLPrinter(binary).get_xml_obj()
    a="{"+NS+"}"
    launcher=set()
    for item in root.find("application"):
        if item.tag not in ("activity","activity-alias"): continue
        for intent in item.findall("intent-filter"):
            actions={c.get(a+"name") for c in intent.findall("action")}
            cats={c.get(a+"name") for c in intent.findall("category")}
            if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in cats:
                launcher.add(item.get(a+"name"))
    return Manifest(binary).patch(launcher)


if __name__ == "__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("apk",type=Path); parser.add_argument("output",type=Path)
    args=parser.parse_args()
    with ZipFile(args.apk) as z: binary=z.read("AndroidManifest.xml")
    result=patch_bytes(binary)
    args.output.write_bytes(result)
    print("Binary manifest patched; Chromium resource references preserved.")
