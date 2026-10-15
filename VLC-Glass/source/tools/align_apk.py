#!/usr/bin/env python3
"""Align stored ZIP entries; native libraries in this app are compressed.
No signing or verification is performed here. apksigner handles signatures.
"""
import sys,zipfile,struct
src,dst=sys.argv[-2:]
with zipfile.ZipFile(src) as before,zipfile.ZipFile(dst,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as after:
 for info in before.infolist():
  data=before.read(info.filename);info.flag_bits&=~8
  if info.compress_type==zipfile.ZIP_STORED:
   offset=after.fp.tell()+30+len(info.filename.encode('utf8'))+len(info.extra)
   padding=(-offset)%4
   if padding:info.extra+=struct.pack('<HH',0xd935,padding)+bytes(padding)
  after.writestr(info,data)
with zipfile.ZipFile(dst) as result:
 for i in result.infolist():
  if i.compress_type==zipfile.ZIP_STORED:assert (i.header_offset+30+len(i.filename.encode('utf8'))+len(i.extra))%4==0
print('Stored ZIP entries are aligned to 4 bytes; compressed native libraries extract on install.')
