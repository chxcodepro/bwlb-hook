package io.github.carrot.coinlock;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

/** Shared contract between the module UI, the bridge provider and the game hook. */
final class State {
    static final String PACKAGE = "io.github.carrot.suite";
    static final String GAME = "com.carrot.iceworld";
    static final Uri URI = Uri.parse("content://" + PACKAGE + ".bridge");
    static final Uri CONFIG = Uri.withAppendedPath(URI, "config");
    static final Uri RUNTIME = Uri.withAppendedPath(URI, "runtime");

    /** Status bits reported by the native layer. */
    static final int SUPPORTED = 1, READY = 2, GOLD = 4, SHOP = 8, CARROT = 16, ERROR = 32;

    static final int GOLD_TOP = 999999;
    static final int GEM_TOP = 999999;
    static final int PROP_TOP = 999;

    final boolean gold, gem, props, shop, carrot, floating, foreground, connected, unlock;
    final int goldValue, gemFloor, propsFloor, status;
    final long coins, gems;

    State(Bundle b) {
        gold = b.getBoolean("gold");
        gem = b.getBoolean("gem");
        props = b.getBoolean("props");
        shop = b.getBoolean("shop");
        carrot = b.getBoolean("carrot");
        unlock = b.getBoolean("unlock");
        floating = b.getBoolean("floating");
        foreground = b.getBoolean("foreground");
        goldValue = b.getInt("goldValue", GOLD_TOP);
        gemFloor = b.getInt("gemFloor", GEM_TOP);
        propsFloor = b.getInt("propsFloor", PROP_TOP);
        status = b.getInt("status", 0);
        coins = b.getLong("coins", -1);
        gems = b.getLong("gems", -1);
        long seen = b.getLong("seen");
        connected = seen > 0 && SystemClock.elapsedRealtime() - seen < 4000;
    }

    static State read(Context c) {
        Bundle b = c.getContentResolver().call(URI, "read", "2", null);
        return new State(b == null ? new Bundle() : b);
    }

    static void write(Context c, String key, boolean value) {
        Bundle b = new Bundle();
        b.putBoolean(key, value);
        c.getContentResolver().call(URI, "configure", "2", b);
    }

    static void unlock(Context c) {
        Bundle b = new Bundle();
        b.putBoolean("unlock", true);
        c.getContentResolver().call(URI, "configure", "2", b);
    }

    /** Short status text for the badge. */
    String label() {
        if ((status & ERROR) != 0) return "运行异常";
        if (!connected) return "等待游戏";
        if ((status & SUPPORTED) == 0) return "版本不支持";
        if ((status & READY) == 0) return "等待进入游戏";
        return "已生效";
    }

    boolean active() {
        return connected && (status & SUPPORTED) != 0 && (status & READY) != 0 && (status & ERROR) == 0;
    }
}
