package io.github.carrot4.coinlock;

import android.app.Application;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import java.io.File;
import javax.microedition.khronos.opengles.GL10;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class GameModule implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static String modulePath;
    private static boolean started, loaded, fault, applied;
    private static Context context;
    private static Handler worker;
    private static volatile int mask, status;
    private static long lastFrame;
    @Override public void initZygote(StartupParam param) { modulePath = param.modulePath; }
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!io.github.carrot.suite.GameSpec.isGame(4,param.packageName) || !param.packageName.equals(param.processName)) return;
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam hook) {
                if (started) return;
                started = true;
                context = (Context) hook.args[0];
                HandlerThread thread = new HandlerThread("Carrot4Control");
                thread.start(); worker = new Handler(thread.getLooper());
                try {
                    if (!io.github.carrot.suite.GameSpec.supported(context,4)) throw new IllegalStateException("Version");
                    System.load(new File(modulePath).getParent() + "/lib/arm64/libcarrot4.so");
                    loaded = true;
                    context.getContentResolver().registerContentObserver(State.CONFIG, true, new ContentObserver(worker) {
                        @Override public void onChange(boolean selfChange, Uri uri) { readConfig(); }
                    });
                    XposedHelpers.findAndHookMethod("org.cocos2dx.lib.Cocos2dxRenderer", param.classLoader,
                        "onDrawFrame", GL10.class, new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam frame) {
                                long now = SystemClock.uptimeMillis();
                                if (!loaded || fault || (mask == 0 && !applied) || now - lastFrame < 300) return;
                                lastFrame = now;
                                try { status = NativeBridge.onFrame(mask); applied = mask != 0; }
                                catch (Throwable t) { fault = true; status = 5; XposedBridge.log(t); }
                            }
                        });
                    XposedBridge.log("Carrot4: renderer connected");
                } catch (Throwable t) { status = 5; XposedBridge.log(t); }
                worker.post(new Runnable() {
                    @Override public void run() {
                        readConfig();
                        try {
                            Bundle b = new Bundle(); b.putInt("status", status);
                            b.putLong("coins", loaded && !fault ? NativeBridge.balance() : -1);
                            b.putLong("shopGold", loaded && !fault ? NativeBridge.wallet(0) : -1);
                            b.putLong("gems", loaded && !fault ? NativeBridge.wallet(1) : -1);
                            b.putLong("energy", loaded && !fault ? NativeBridge.wallet(2) : -1);
                            b.putLong("shopFixed", loaded && !fault ? NativeBridge.wallet(3) : -1);
                            b.putBoolean("shopOpen", loaded && !fault && NativeBridge.wallet(4) == 1);
                            b.putLong("staminaInfinite", loaded && !fault ? NativeBridge.wallet(5) : -1);
                            b.putBoolean("unsupported",!io.github.carrot.suite.GameSpec.supported(context,4));
                            context.getContentResolver().call(State.URI, "report", "4", b);
                        } catch (Throwable ignored) {}
                        worker.postDelayed(this, 1000);
                    }
                });
            }
        });
    }
    private static void readConfig() {
        try {
            Bundle b = context.getContentResolver().call(State.URI, "read", "4", null);
            boolean running = b != null && b.getBoolean("running");
            int value = 0;
            if (running) {
                if (b.getBoolean("enabled", true)) value |= 1;
                if (b.getBoolean("gemLock")) value |= 2;
                if (b.getBoolean("goldLock")) value |= 4;
                if (b.getBoolean("shopFree")) value |= 8;
                if (b.getBoolean("staminaFree")) value |= 16;
            }
            mask = value;
        } catch (Throwable ignored) { mask = 0; }
    }
}
