package io.github.carrot3.coinlock;
final class NativeBridge {
    static native int onFrame(int flags);
    static native long balance();
    static native long wallet(int index);
    private NativeBridge() {}
}
