package io.github.carrot.suite;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

public final class Store {
    public static final Uri URI = Uri.parse("content://" + GameSpec.APP + ".bridge");
    public static final Uri CONFIG = Uri.withAppendedPath(URI, "config");
    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences("suite", 0); }
    public static int selected(Context c) { return prefs(c).getInt("selected", 1); }
    public static boolean running(Context c) {
        long age = SystemClock.elapsedRealtime() - prefs(c).getLong("serviceBeat",0);
        return prefs(c).getBoolean("running", false) && age >= 0 && age < 5000;
    }
    public static void select(Context c, int id) {
        prefs(c).edit().putInt("selected", id).apply();
        c.getContentResolver().notifyChange(CONFIG, null);
    }
    public static void running(Context c, boolean value) {
        prefs(c).edit().putBoolean("running", value).putLong("serviceBeat",SystemClock.elapsedRealtime()).apply();
        c.getContentResolver().notifyChange(CONFIG, null);
    }
    public static Bundle read(Context c, int id) {
        Bundle b = c.getContentResolver().call(URI, "read", String.valueOf(id), null);
        return b == null ? new Bundle() : b;
    }
    public static void write(Context c, int id, String key, boolean value) {
        Bundle b = new Bundle(); b.putBoolean(key, value);
        c.getContentResolver().call(URI, "configure", String.valueOf(id), b);
    }
    public static boolean connected(Bundle b) {
        long seen = b.getLong("seen"), age = SystemClock.elapsedRealtime() - seen;
        return seen > 0 && age >= 0 && age < 4000;
    }
    public static String label(int id, Bundle b) {
        if (!b.getBoolean("running")) return "已暂停";
        if (!connected(b)) return "等待游戏";
        int s = b.getInt("status");
        if (b.getBoolean("unsupported")) return "版本不支持";
        if (id == 1) return s < 0 ? "版本不支持" : s == 1 ? "已锁定" : "已连接";
        if (id == 2) return (s & 32) != 0 ? "连接异常" : (s & 4) != 0 ? "已锁定" : "已连接";
        if (id == 3) return (s & 16) != 0 ? "版本不支持" : (s & 8) != 0 ? "连接异常" : (s & 4) != 0 ? "已锁定" : "已连接";
        return s == 6 ? "版本不支持" : s == 5 ? "连接异常" : s == 3 ? "已锁定" : "已连接";
    }
    private Store() { }
}
