package com.wireguard.android.backend;
/** Compile-only ABI. The original Brave APK supplies this class and its native engine. */
public final class GoBackend {
    public static native int wgTurnOn(String name, int tunFd, String config);
    public static native void wgTurnOff(int handle);
    public static native String wgGetConfig(int handle);
    public static native int wgGetSocketV4(int handle);
    public static native int wgGetSocketV6(int handle);
    public static native String wgVersion();
}
