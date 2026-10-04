package io.github.carrot3.coinlock;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

final class State {
    static final String PACKAGE="io.github.carrot.suite";
    static final String GAME="com.feiyu.carrot3.nearme.gamecenter";
    static final Uri URI=Uri.parse("content://"+PACKAGE+".bridge");
    static final Uri CONFIG=Uri.withAppendedPath(URI,"config");
    static final int SUPPORTED=1,READY=2,LOCKED=4,ERROR=8,VERSION=16,GEM=32,STORE_GOLD=128,WALLET_READY=256,POINT=512,ADS=1024;
    final boolean gold,gem,storeGold,point,ad,skipping,running,connected;
    final int status,ads;
    final long coins,gems,shopCoins,points;
    State(Bundle b){
        gold=b.getBoolean("gold",true);running=b.getBoolean("running");
        gem=b.getBoolean("gem");storeGold=b.getBoolean("storeGold");point=b.getBoolean("point");
        ad=b.getBoolean("ad",true);skipping=b.getBoolean("skipping");ads=b.getInt("ads",0);
        status=b.getInt("status");coins=b.getLong("coins",-1);
        gems=b.getLong("gems",-1);shopCoins=b.getLong("shopCoins",-1);points=b.getLong("points",-1);
        long seen=b.getLong("seen");connected=seen>0&&SystemClock.elapsedRealtime()-seen<3500;
    }
    static State read(Context c){Bundle b=c.getContentResolver().call(URI,"read","3",null);return new State(b==null?new Bundle():b);}
    static void write(Context c,String key,boolean value){Bundle b=new Bundle();b.putBoolean(key,value);c.getContentResolver().call(URI,"configure","3",b);}
    String label(){
        if(!connected)return "等待游戏";
        if((status&VERSION)!=0)return "版本不支持";
        if((status&ERROR)!=0)return "锁定失败";
        if((status&READY)==0)return "等待对局";
        if((status&LOCKED)!=0)return "已锁定";
        return "已连接";
    }
    boolean locked(){return connected&&(status&LOCKED)!=0;}
    String walletLabel(){
        if(!connected)return "等待游戏";
        if((status&VERSION)!=0)return "版本不支持";
        if((status&ERROR)!=0)return "运行异常";
        if((status&WALLET_READY)==0)return "等待游戏数据";
        return (status&(GEM|STORE_GOLD|POINT))!=0?"已锁定":"已连接";
    }
}
