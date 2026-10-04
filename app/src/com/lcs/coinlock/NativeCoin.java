package com.lcs.coinlock;

public final class NativeCoin {
    private NativeCoin() {}
    public static native int apply(long base, int value);
}
