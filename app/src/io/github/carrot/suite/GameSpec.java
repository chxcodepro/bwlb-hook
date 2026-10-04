package io.github.carrot.suite;

import android.content.Context;
import android.content.pm.PackageInfo;

public final class GameSpec {
    public static final String APP = "io.github.carrot.suite";
    public static final String[][] PACKAGES = { {}, {"com.carrot.carrotfantasy"}, {"com.carrot.iceworld"},
        {"com.feiyu.carrot3.nearme.gamecenter", "com.feiyu.carrot3"},
        {"com.feiyu.luobo4.nearme.gamecenter", "com.feiyu.luobo4"} };
    public static final String[][] KEYS = { {}, {"enabled", "adEnabled"},
        {"gold", "gem", "props", "shop", "carrot"}, {"gold", "gem", "storeGold", "point", "ad"},
        {"enabled", "goldLock", "gemLock", "shopFree", "staminaFree"} };
    public static final String[][] LABELS = { {}, {"对局金币", "跳过广告"},
        {"对局金币", "钻石补充", "道具补充", "商城解锁", "萝卜解锁"},
        {"对局金币", "钻石锁定", "商城金币", "点券锁定", "跳过广告"},
        {"对局金币", "金币锁定", "钻石锁定", "解除限购", "无限体力"} };
    public static boolean isGame(int id, String pkg) {
        for (String p : PACKAGES[id]) if (p.equals(pkg)) return true;
        return false;
    }
    public static boolean defaultOn(int id, String key) {
        return key.equals("gold") || key.equals("enabled") || key.equals("ad") || key.equals("adEnabled");
    }
    public static String installed(Context c, int id) {
        for (String p : PACKAGES[id]) try { c.getPackageManager().getApplicationInfo(p, 0); return p; }
        catch (Exception ignored) { }
        return null;
    }
    public static boolean supported(Context c, int id) {
        try {
            PackageInfo info = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            long code = info.getLongVersionCode();
            return id == 1 ? code == 74992 : id == 2 ? code == 821 : id == 3 ? code == 9245 : code == 36773 || code == 37304;
        } catch (Exception e) { return false; }
    }
    private GameSpec() { }
}
