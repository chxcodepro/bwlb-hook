package com.lcs.coinlock;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.carrot.suite.GameSpec;
import io.github.carrot.suite.Store;

public final class CoinHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static String modulePath;
    private static volatile boolean enabled;
    private static volatile int result;
    private static volatile long base;
    private static boolean started;
    @Override public void initZygote(StartupParam p) { modulePath = p.modulePath; }
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (!GameSpec.isGame(1,p.packageName) || !p.packageName.equals(p.processName)) return;
        XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam hook) {
                if (started) return; started = true;
                Context c = (Context)hook.args[0];
                boolean supported = GameSpec.supported(c,1);
                try {
                    if (!supported) throw new IllegalStateException("Version");
                    System.load(new File(modulePath).getParent()+"/lib/arm64/libcarrot1.so");
                    Class<?> renderer = XposedHelpers.findClass("org.cocos2dx.lib.Cocos2dxRenderer",p.classLoader);
                    XposedBridge.hookAllMethods(renderer,"onDrawFrame",new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam frame) { apply(); }
                        @Override protected void afterHookedMethod(MethodHookParam frame) { apply(); }
                    });
                } catch (Throwable t) { result = -1; XposedBridge.log(t); }
                Thread worker = new Thread(() -> {
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            if (supported && base == 0) base = findBase();
                            Bundle b = Store.read(c,1);
                            boolean running = supported && b.getBoolean("running");
                            enabled = running && b.getBoolean("enabled");
                            AdHook.enabled = running && b.getBoolean("adEnabled");
                            Bundle report = new Bundle(); report.putInt("result",result);
                            report.putBoolean("unsupported",!supported);
                            c.getContentResolver().call(Store.URI,"status","1",report);
                        } catch (Throwable ignored) { enabled = false; AdHook.enabled = false; }
                        try { Thread.sleep(500); } catch (InterruptedException e) { return; }
                    }
                },"Carrot1Control"); worker.setDaemon(true); worker.start();
            }
        });
    }
    private static void apply() {
        if (!enabled || base == 0) return;
        try { result = NativeCoin.apply(base,999999); }
        catch (Throwable t) { result = -1; enabled = false; }
    }
    private static long findBase() {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            while ((line = r.readLine()) != null) if (line.endsWith("/libgame.so")) {
                String[] f = line.trim().split("\\s+");
                return Long.parseLong(f[0].split("-")[0],16)-Long.parseLong(f[2],16);
            }
        } catch (Exception ignored) { }
        return 0;
    }
}
