package io.github.carrot3.coinlock;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import javax.microedition.khronos.opengles.GL10;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class GameModule implements IXposedHookLoadPackage,IXposedHookZygoteInit {
    private static final int AD_PLAY=63,AD_SUCCESS=64;
    private static volatile boolean foreground;
    private static volatile boolean skipAds;
    private static final AtomicInteger adsSkipped=new AtomicInteger();
    private static volatile int flags;
    private static volatile int status;
    private static boolean started,loaded,applied;
    private static Context context;
    private static Handler worker;
    private static String modulePath;
    @Override public void initZygote(StartupParam p){modulePath=p.modulePath;}
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p)throws Throwable{
        if(!io.github.carrot.suite.GameSpec.isGame(3,p.packageName)||!p.packageName.equals(p.processName))return;
        XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam param){
                if(started)return;started=true;context=(Context)param.args[0];
                HandlerThread thread=new HandlerThread("Carrot3Control");thread.start();worker=new Handler(thread.getLooper());
                try{
                    if(!io.github.carrot.suite.GameSpec.supported(context,3))throw new IllegalStateException("Version");
                    System.load(new File(modulePath).getParent()+"/lib/arm64/libcarrot3.so");loaded=true;
                    context.getContentResolver().registerContentObserver(State.CONFIG,true,new ContentObserver(worker){
                        @Override public void onChange(boolean selfChange,Uri uri){readConfig();}
                    });
                    XposedHelpers.findAndHookMethod("org.cocos2dx.lib.Cocos2dxRenderer",p.classLoader,"onDrawFrame",GL10.class,new XC_MethodHook(){
                        @Override protected void beforeHookedMethod(MethodHookParam frame){
                            if(!loaded||(flags==0&&!applied))return;
                            try{status=NativeBridge.onFrame(flags);applied=flags!=0;}catch(Throwable t){status=State.ERROR;}
                        }
                    });
                    XposedBridge.log("Carrot3: renderer ready");
                }catch(Throwable t){status=State.ERROR;XposedBridge.log(t);}
                installAdSkip(p.classLoader);
                worker.post(new Runnable(){@Override public void run(){
                    readConfig();report();worker.postDelayed(this,foreground?1000:2000);
                }});
            }
        });
        XposedHelpers.findAndHookMethod(Activity.class,"onResume",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam param){
                if(context!=null&&io.github.carrot.suite.GameSpec.isGame(3,((Activity)param.thisObject).getPackageName())){foreground=true;worker.post(()->{readConfig();report();});}
            }
        });
        XposedHelpers.findAndHookMethod(Activity.class,"onPause",new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam param){foreground=false;}
        });
    }
    private static void readConfig(){
        try{Bundle b=context.getContentResolver().call(State.URI,"read","3",null);
            if(b!=null){
                boolean running=loaded&&b.getBoolean("running");
                skipAds=running&&b.getBoolean("ad",true);
                flags=running?(b.getBoolean("gold",true)?1:0)|(b.getBoolean("gem")?2:0)|(b.getBoolean("storeGold")?8:0)|(b.getBoolean("point")?16:0):0;
            }else{flags=0;skipAds=false;}}
        catch(Throwable ignored){flags=0;skipAds=false;}
    }
    private static void report(){
        try{Bundle b=new Bundle();b.putInt("status",status);b.putLong("coins",loaded?NativeBridge.balance():-1);
            b.putLong("gems",loaded?NativeBridge.wallet(0):-1);b.putLong("shopCoins",loaded?NativeBridge.wallet(1):-1);
            b.putLong("points",loaded?NativeBridge.wallet(2):-1);
            b.putBoolean("skipping",skipAds);b.putInt("ads",adsSkipped.get());
            b.putBoolean("unsupported",!io.github.carrot.suite.GameSpec.supported(context,3));
            context.getContentResolver().call(State.URI,"report","3",b);}
        catch(Throwable ignored){}
    }
    /* The rewarded-video flow posts Message what=63 to the game's own handler,
       which then calls IChannel.showAd. Rewriting it to what=64 makes the very
       same handler take the success branch, so the reward is granted without
       playing anything: no SDK callback, no timer, nothing to interrupt. */
    private static void installAdSkip(ClassLoader loader){
        try{
            XposedHelpers.findAndHookMethod("com.feiyu.carrot3.CarrotFantasy",loader,"b",Message.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam param){
                    if(!skipAds)return;
                    Object arg=param.args[0];
                    if(!(arg instanceof Message))return;
                    Message message=(Message)arg;
                    if(message.what!=AD_PLAY)return;
                    message.what=AD_SUCCESS;
                    adsSkipped.incrementAndGet();
                }
            });
            XposedBridge.log("Carrot3: ad skip ready");
        }catch(Throwable t){XposedBridge.log("Carrot3: ad skip unavailable");XposedBridge.log(t);}
    }
}
