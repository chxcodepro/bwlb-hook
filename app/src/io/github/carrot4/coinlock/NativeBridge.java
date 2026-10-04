package io.github.carrot4.coinlock;

final class NativeBridge {
    static native int onFrame(int mask);
    static native long balance();
    static native long wallet(int index);
    private NativeBridge() {}
}
