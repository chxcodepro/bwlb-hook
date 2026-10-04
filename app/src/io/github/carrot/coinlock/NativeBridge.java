package io.github.carrot.coinlock;

final class NativeBridge {
    static native int onFrame(int flags, int gold, int diamond, int props);
    static native long balance();
    static native long diamond();
    static native int unlock();
    private NativeBridge() {}
}
