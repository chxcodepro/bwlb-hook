package io.github.carrot.suite;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;

public final class BridgeProvider extends ContentProvider {
    private final Bundle[] runtime = {new Bundle(), new Bundle(), new Bundle(), new Bundle(), new Bundle()};
    private boolean unlock;
    @Override public boolean onCreate() { return true; }
    @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
        int id;
        try { id = Integer.parseInt(arg); } catch (Exception e) { throw new IllegalArgumentException("Game"); }
        if (id < 1 || id > 4) throw new IllegalArgumentException("Game");
        boolean own = Binder.getCallingUid() == Process.myUid(), game = false;
        String[] packages = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (packages != null) for (String p : packages) if (GameSpec.isGame(id, p)) game = true;
        if (!own && !game) throw new SecurityException("Caller");
        if ("configure".equals(method)) {
            if (!own || extras == null) throw new SecurityException("Caller");
            android.content.SharedPreferences.Editor edit = Store.prefs(getContext()).edit();
            for (String key : GameSpec.KEYS[id]) if (extras.containsKey(key)) edit.putBoolean(id + "." + key, extras.getBoolean(key));
            if (id == 2 && extras.getBoolean("unlock") && Store.running(getContext()) && Store.selected(getContext()) == id) unlock = true;
            edit.apply(); getContext().getContentResolver().notifyChange(Store.CONFIG, null);
        } else if ("report".equals(method) || "status".equals(method)) {
            if (!game || extras == null) throw new SecurityException("Caller");
            runtime[id] = new Bundle(extras); runtime[id].putLong("seen", SystemClock.elapsedRealtime());
            if (id == 1) runtime[id].putInt("status", extras.getInt("result"));
        } else if (!"read".equals(method) && !"get".equals(method)) throw new IllegalArgumentException("Operation");
        Bundle result = new Bundle(runtime[id]);
        boolean active = Store.running(getContext()) && Store.selected(getContext()) == id;
        result.putBoolean("running", active);
        for (String key : GameSpec.KEYS[id]) result.putBoolean(key, Store.prefs(getContext()).getBoolean(id + "." + key, GameSpec.defaultOn(id, key)));
        result.putInt("goldValue", 999999); result.putInt("gemFloor", 999999); result.putInt("propsFloor", 999);
        result.putBoolean("unlock", game && active && id == 2 && unlock);
        if (game && id == 2) unlock = false;
        if (id == 2 && !active) unlock = false;
        return result;
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
