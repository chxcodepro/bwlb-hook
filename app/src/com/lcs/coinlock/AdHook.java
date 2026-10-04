package com.lcs.coinlock;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed entry point: skips the rewarded video and pays out immediately.
 *
 * The game routes every advert through its own bridge class, a thin wrapper
 * over the OPPO HeyTap SDK. The Java side is small enough to read end to end:
 *
 *   canShowAd(a,b,c)  -> true only while a video is preloaded; otherwise it
 *                        kicks off a load and returns false
 *   showAd(a,b,c)     -> mRewardVideoAd.showAd()
 *
 * and the SDK listener then walks a four-event sequence back into the game:
 *
 *   AdHelper$2.onVideoPlayStart()    -> onAdShowStart()  ("play.start")
 *   AdHelper$2.onReward()            -> mIsReward = true
 *   AdHelper$2.onVideoPlayComplete() -> onAdPlayOk()     ("play.finish")
 *   AdHelper$2.onVideoPlayClose()    -> runNativeCallback() -> jniLandingPageClose()
 *                                                         ("play.close")
 *
 * Those natives only log analytics and bump a per-seat play counter, so the
 * payout itself is decided by C++ state that this sequence maintains: the game
 * blocks in its "watching an advert" state until play.close arrives. Replaying
 * all three events is therefore what actually completes the transaction;
 * firing onAdPlayOk alone leaves the button gone and nothing granted.
 *
 * Timing matters too. Replaying inline from showAd() runs before the C++ caller
 * has armed its waiting state and gets overwritten as the JNI frame unwinds,
 * so the events are dispatched from a later thread.
 *
 * No interception is needed for interstitials or splash ads: the game never
 * references those SDK classes at all.
 */
public class AdHook implements IXposedHookLoadPackage {

    public static volatile boolean enabled = false;

    private static final String GAME = "com.carrot.carrotfantasy";
    private static final String AD_HELPER = GAME + ".adwork.AdHelper";
    private static final String TAG = "coinlock: ";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!GAME.equals(lp.packageName) || !lp.packageName.equals(lp.processName)) {
            return;
        }

        final Class<?> helper;
        try {
            helper = XposedHelpers.findClass(AD_HELPER, lp.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "AdHelper missing, nothing hooked: " + t);
            return;
        }

        // The green "watch a video" badge is only offered once canShowAd() says
        // yes, and that answer depends on a preloaded advert. Always say yes.
        XposedBridge.hookAllMethods(helper, "canShowAd", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                if (!enabled) return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                remember(helper, param.args);
                return Boolean.TRUE;
            }
        });

        XposedBridge.hookAllMethods(helper, "showAd", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                if (!enabled) return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                remember(helper, param.args);
                final Object[] a = param.args.clone();
                XposedBridge.log(TAG + "showAd(" + a[0] + "," + a[1] + "," + a[2] + ")");
                // The callbacks must NOT fire inline. The game arms its
                // "an advert is running" state only after showAd() returns to
                // its C++ caller, so a synchronous replay would be wiped the
                // instant the JNI frame unwinds. The native handler also keys
                // off play.start having happened first, hence the pair.
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        pause(500);
                        fire(helper, "onAdShowStart", a[0], a[1], a[2]);
                        pause(500);
                        fire(helper, "onAdPlayOk", a[0], a[1], a[2]);
                        pause(500);
                        // runNativeCallback is the "advert closed" hop. It posts
                        // jniLandingPageClose onto the GL thread, which is what
                        // hands control back to the game and settles the payout.
                        // Without it the game sits in its "watching" state.
                        fire(helper, "runNativeCallback", a[0], a[1], a[2]);
                    }
                }, "coinlock-ad");
                t.setDaemon(true);
                t.start();
                return Boolean.TRUE;
            }
        });

        // Ad SDK init can fail on a rooted or offline device, and the game then
        // greys out its advert entry. Report success anyway; the payout path
        // above never consults the SDK.
        try {
            Class<?> initListener = XposedHelpers.findClass(AD_HELPER + "$1", lp.classLoader);
            XposedBridge.hookAllMethods(initListener, "onFailed", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enabled) return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                    XposedHelpers.callStaticMethod(helper, "onSDKInitOk", "oppo");
                    return null;
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "init listener not hooked: " + t);
        }

        XposedBridge.log(TAG + "rewarded video bypass active");
    }

    /** Mirrors what canShowAd()/showAd() store before they touch the SDK. */
    private static void remember(Class<?> helper, Object[] args) {
        if (args == null || args.length < 3) {
            return;
        }
        XposedHelpers.setStaticObjectField(helper, "adname", args[0]);
        XposedHelpers.setStaticObjectField(helper, "seatname", args[1]);
        XposedHelpers.setStaticObjectField(helper, "seatcode", args[2]);
    }

    private static void fire(Class<?> helper, String method, Object a, Object b, Object c) {
        try {
            XposedHelpers.callStaticMethod(helper, method, a, b, c);
            XposedBridge.log(TAG + method + " dispatched");
        } catch (Throwable t) {
            XposedBridge.log(TAG + method + " failed: " + t);
        }
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
