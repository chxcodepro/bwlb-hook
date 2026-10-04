package io.github.carrot.coinlock;

import android.app.Application;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import java.io.File;
import javax.microedition.khronos.opengles.GL10;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.carrot.suite.GameSpec;

public final class GameModule implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static String modulePath;
    private static boolean started, loaded, applied;
    private static volatile int flags, status;
    private static volatile boolean pendingUnlock, running;
    private static Context context;
    private static Handler worker;
    @Override public void initZygote(StartupParam p) { modulePath = p.modulePath; }
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (!GameSpec.isGame(2,p.packageName) || !p.packageName.equals(p.processName)) return;
        XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam hook) {
                if (started) return;
                started = true; context = (Context)hook.args[0];
                HandlerThread thread = new HandlerThread("Carrot2Control"); thread.start(); worker = new Handler(thread.getLooper());
                try {
                    if (!GameSpec.supported(context,2)) throw new IllegalStateException("Version");
                    System.load(new File(modulePath).getParent()+"/lib/arm64/libcoinlock.so"); loaded = true;
                    context.getContentResolver().registerContentObserver(State.CONFIG,true,new ContentObserver(worker) {
                        @Override public void onChange(boolean selfChange, Uri uri) { readConfig(); }
                    });
                    XposedHelpers.findAndHookMethod("org.cocos2dx.lib.Cocos2dxRenderer",p.classLoader,"onDrawFrame",GL10.class,new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam frame) {
                            if (!loaded || (!running && !applied)) return;
                            try {
                                if (pendingUnlock && running) pendingUnlock = NativeBridge.unlock() == 0;
                                status = NativeBridge.onFrame(flags,State.GOLD_TOP,State.GEM_TOP,State.PROP_TOP);
                                applied = flags != 0;
                            } catch (Throwable t) { status = State.ERROR; flags = 0; }
                        }
                    });
                } catch (Throwable t) { status = State.ERROR; XposedBridge.log(t); }
                worker.post(new Runnable() { @Override public void run() {
                    readConfig();
                    try {
                        Bundle b = new Bundle(); b.putInt("status",status);
                        b.putLong("coins",loaded ? NativeBridge.balance() : -1);
                        b.putLong("gems",loaded ? NativeBridge.diamond() : -1);
                        b.putBoolean("unsupported",!GameSpec.supported(context,2));
                        context.getContentResolver().call(State.URI,"report","2",b);
                    } catch (Throwable ignored) { }
                    worker.postDelayed(this,1000);
                }});
            }
        });
    }
    private static void readConfig() {
        try {
            Bundle b = context.getContentResolver().call(State.URI,"read","2",null);
            running = loaded && b != null && b.getBoolean("running");
            if (!running) { flags = 0; pendingUnlock = false; return; }
            flags = (b.getBoolean("gold")?1:0)|(b.getBoolean("gem")?2:0)|(b.getBoolean("props")?4:0)|(b.getBoolean("shop")?8:0)|(b.getBoolean("carrot")?16:0);
            if (b.getBoolean("unlock")) pendingUnlock = true;
        } catch (Throwable ignored) { running = false; flags = 0; pendingUnlock = false; }
    }
}
