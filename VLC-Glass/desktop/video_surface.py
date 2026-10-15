"""LibVLC frame callbacks for Cocoa hosts without an embedded OpenGL context.

Conversion stays in LibVLC; a bounded RGB frame is copied on its decoder thread.
Qt paints the latest frame, coalescing notifications rather than queuing frames.
"""
import ctypes
from PySide6.QtCore import Signal,QRectF
from PySide6.QtGui import QImage,QPainter,QColor
from PySide6.QtWidgets import QWidget
import vlc

class VideoSurface(QWidget):
    frame_ready=Signal()
    def __init__(self):
        super().__init__();self.frame=None;self.buffer=None;self.width_px=0;self.height_px=0;self.pitch=0
        self.masked=False;self.pending=False;self.frame_count=0;self.paint_count=0
        self.frame_ready.connect(self.request_paint)
        self.lock_callback=vlc.CallbackDecorators.VideoLockCb(self.lock)
        self.display_callback=vlc.CallbackDecorators.VideoDisplayCb(self.display)
        # Use a writable char pointer, rather than c_char_p's bytes conversion.
        uintp=ctypes.POINTER(ctypes.c_uint)
        setup=ctypes.CFUNCTYPE(ctypes.c_uint,ctypes.POINTER(ctypes.c_void_p),ctypes.c_void_p,uintp,uintp,uintp,uintp)
        self.format_callback=setup(self.format)
        self.cleanup_callback=vlc.CallbackDecorators.VideoCleanupCb(self.cleanup)
    def attach(self,player):
        player.video_set_callbacks(self.lock_callback,None,self.display_callback,None)
        player.video_set_format_callbacks(self.format_callback,self.cleanup_callback)
    def format(self,opaque,chroma,width,height,pitches,lines):
        w,h=width[0],height[0]
        if not w or not h:return 0
        ratio=min(1,3840/w,2160/h);w=max(1,int(w*ratio));h=max(1,int(h*ratio))
        self.pitch=(w*4+31)&~31;rows=(h+31)&~31
        ctypes.memmove(chroma,b'RV32',4);width[0]=w;height[0]=h;pitches[0]=self.pitch;lines[0]=rows
        self.width_px=w;self.height_px=h;self.buffer=ctypes.create_string_buffer(self.pitch*rows)
        return 1
    def lock(self,opaque,planes):
        planes[0]=ctypes.addressof(self.buffer);return 0
    def display(self,opaque,picture):
        self.frame_count+=1
        if self.masked or self.buffer is None:return
        # Keep bytes alive alongside the QImage; no GUI work runs on this thread.
        data=bytes(self.buffer);image=QImage(data,self.width_px,self.height_px,self.pitch,QImage.Format.Format_RGB32)
        self.frame=(data,image)
        if not self.pending:self.pending=True;self.frame_ready.emit()
    def cleanup(self,opaque):
        self.buffer=None;self.frame=None
    def request_paint(self):
        self.pending=False;self.update()
    def set_masked(self,value):
        self.masked=value;self.frame=None;self.update()
    def paintEvent(self,event):
        painter=QPainter(self);painter.fillRect(self.rect(),QColor('black'));frame=self.frame
        if frame is not None and not self.masked:
            image=frame[1];ratio=min(self.width()/image.width(),self.height()/image.height())
            w=image.width()*ratio;h=image.height()*ratio
            painter.drawImage(QRectF((self.width()-w)/2,(self.height()-h)/2,w,h),image)
            self.paint_count+=1
