package io.github.carrot4.coinlock;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

final class State {
    static final String PACKAGE = "io.github.carrot.suite";
    static final String GAME = "com.feiyu.luobo4.nearme.gamecenter";
    static final Uri URI = Uri.parse("content://" + PACKAGE + ".bridge");
    static final Uri CONFIG = Uri.withAppendedPath(URI, "config");
    final boolean running, enabled, connected, gemLock, goldLock, shopFree, staminaFree, shopOpen;
    final int status;
    final long coins, shopGold, gems, energy, shopFixed, staminaInfinite;
    State(Bundle b) {
        running = b.getBoolean("running");
        enabled = b.getBoolean("enabled", true);
        gemLock = b.getBoolean("gemLock", false);
        goldLock = b.getBoolean("goldLock", false);
        shopFree = b.getBoolean("shopFree", false);
        staminaFree = b.getBoolean("staminaFree", false);
        status = b.getInt("status");
        coins = b.getLong("coins", -1);
        shopGold = b.getLong("shopGold", -1); gems = b.getLong("gems", -1); energy = b.getLong("energy", -1);
        shopFixed = b.getLong("shopFixed", -1);
        shopOpen = b.getBoolean("shopOpen", false);
        staminaInfinite = b.getLong("staminaInfinite", -1);
        long seen = b.getLong("seen");
        connected = seen > 0 && SystemClock.elapsedRealtime() - seen < 4000;
    }
    static State read(Context context) {
        Bundle b = context.getContentResolver().call(URI, "read", "4", null);
        return new State(b == null ? new Bundle() : b);
    }
    static void write(Context context, String key, boolean value) {
        Bundle b = new Bundle(); b.putBoolean(key, value);
        context.getContentResolver().call(URI, "configure", "4", b);
    }
    boolean anyLock() { return enabled || gemLock || goldLock || shopFree || staminaFree; }
    String label() {
        if (!connected) return "等待游戏";
        if (status == 6) return "版本不支持";
        if (status == 5) return "连接异常";
        if (!anyLock()) return "未锁定";
        if (gemLock || goldLock || shopFree || staminaFree) return "已锁定";
        if (status == 3) return "已锁定";
        if (status == 2 || status == 1 || status == 0) return "等待对局";
        return "已连接";
    }
    boolean locked() { return connected && status == 3 && running && enabled; }
    boolean active() { return connected && running && anyLock(); }
    String walletValue(int index) {
        if (index == 2 && staminaInfinite > 0) return "∞";
        long value = index == 0 ? shopGold : index == 1 ? gems : energy;
        return connected && value >= 0 ? Long.toString(value) : "—";
    }
    String shopNote() {
        if (!shopFree) return "";
        long count = shopFixed < 0 ? 0 : shopFixed;
        return (shopOpen ? "商城已打开 · " : "") + "已解除 " + count + " 项限购";
    }
}
