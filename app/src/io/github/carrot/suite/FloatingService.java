package io.github.carrot.suite;

import android.app.*;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public final class FloatingService extends Service {
    private static final String CHANNEL = "floating";
    private final Handler handler = new Handler();
    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private View window;
    private Controls controls;
    private boolean expanded;
    private int game;
    private Rect bounds;
    private final Runnable tick = new Runnable() { @Override public void run() {
        if (!Settings.canDrawOverlays(FloatingService.this)) { stopSelf();return; }
        Store.prefs(FloatingService.this).edit().putLong("serviceBeat",SystemClock.elapsedRealtime()).apply();
        if(game != Store.selected(FloatingService.this))show(expanded);
        if(controls!=null)controls.refresh();
        handler.postDelayed(this,1000);
    }};
    @Override public void onCreate() {
        super.onCreate(); wm=(WindowManager)getSystemService(WINDOW_SERVICE); updateBounds();
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"悬浮控制",NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent content=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,FloatingService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(104,new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_carrot)
            .setContentTitle("保卫萝卜合集").setContentText("悬浮控制已开启").setContentIntent(content)
            .addAction(new Notification.Action.Builder(null,"停止",stop).build()).setOngoing(true).build());
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null&&"stop".equals(intent.getAction())) { Store.running(this,false);stopSelf();return START_NOT_STICKY; }
        if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
        try {
            if(window==null)show(false);
            Store.running(this,true);
            handler.removeCallbacks(tick);handler.post(tick);
        } catch (Exception e) { Toast.makeText(this,"悬浮窗未开启",0).show();Store.running(this,false);stopSelf(); }
        return START_NOT_STICKY;
    }
    private void updateBounds() {
        android.view.WindowMetrics metrics=wm.getCurrentWindowMetrics(); Rect all=metrics.getBounds();
        android.graphics.Insets insets=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        bounds=new Rect(0,0,all.width()-insets.left-insets.right,all.height()-insets.top-insets.bottom);
    }
    private void show(boolean open) {
        int x=params==null?Ui.dp(this,12):params.x, y=params==null?Ui.dp(this,112):params.y;
        if(window!=null){wm.removeView(window);window=null;}
        expanded=open;game=Store.selected(this);controls=null;
        int width=open?Math.min(Ui.dp(this,304),bounds.width()-Ui.dp(this,16)):Ui.dp(this,56);
        params=new WindowManager.LayoutParams(width,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;params.x=x;params.y=y;
        if(!open) {
            TextView bubble=Ui.text(this,String.valueOf(game),20,Ui.CARD,true);bubble.setGravity(Gravity.CENTER);
            bubble.setContentDescription("打开保卫萝卜"+game+"控制面板");bubble.setBackground(Ui.shape(this,Ui.GREEN,28,0));bubble.setElevation(Ui.dp(this,4));
            params.height=Ui.dp(this,56);window=bubble;drag(bubble,true);
        } else {
            LinearLayout panel=Ui.column(this);Ui.pad(panel,16);panel.setBackground(Ui.shape(this,Ui.CARD,22,Ui.LINE));panel.setElevation(Ui.dp(this,6));
            LinearLayout header=Ui.row(this);
            TextView title=Ui.text(this,"保卫萝卜 "+game,17,Ui.INK,true);title.setMinHeight(Ui.dp(this,48));title.setGravity(16);
            header.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));drag(title,false);
            TextView close=Ui.button(this,"收起",false,v->show(false));header.addView(close,new LinearLayout.LayoutParams(Ui.dp(this,64),Ui.dp(this,48)));
            panel.addView(header);Ui.space(panel,8);
            ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
            controls=new Controls(this,game);scroll.addView(controls,new ScrollView.LayoutParams(-1,-2));
            int contentHeight=Math.max(Ui.dp(this,104),Math.min(Ui.dp(this,game==2?406:game==1?178:344),bounds.height()-Ui.dp(this,164)));
            panel.addView(scroll,new LinearLayout.LayoutParams(-1,contentHeight));Ui.space(panel,12);
            LinearLayout actions=Ui.row(this);
            TextView launch=Ui.button(this,"打开游戏",true,v->launch());
            actions.addView(launch,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
            TextView stop=Ui.button(this,"停止",false,v->{Store.running(this,false);stopSelf();});
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(Ui.dp(this,72),Ui.dp(this,48));sp.setMarginStart(Ui.dp(this,8));actions.addView(stop,sp);
            panel.addView(actions);window=panel;
        }
        window.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(bounds.height(),View.MeasureSpec.AT_MOST));
        clamp(window.getMeasuredWidth(),window.getMeasuredHeight());wm.addView(window,params);
    }
    private void launch() {
        String pkg=GameSpec.installed(this,game);
        Intent launch=pkg==null?null:getPackageManager().getLaunchIntentForPackage(pkg);
        if(launch==null){Toast.makeText(this,"游戏未安装",0).show();return;}
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(launch);show(false);
    }
    private void clamp(int width,int height) {
        params.x=Math.max(0,Math.min(params.x,bounds.width()-width));params.y=Math.max(0,Math.min(params.y,bounds.height()-height));
    }
    private void drag(View handle,boolean tap) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downX,downY;int startX,startY;boolean moved;
            final int slop=ViewConfiguration.get(FloatingService.this).getScaledTouchSlop();
            @Override public boolean onTouch(View v,MotionEvent event) {
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getRawX();downY=event.getRawY();startX=params.x;startY=params.y;moved=false;return true;}
                if(event.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
                    if(Math.abs(dx)>slop||Math.abs(dy)>slop)moved=true;
                    if(moved){params.x=startX+(int)dx;params.y=startY+(int)dy;clamp(window.getWidth(),window.getHeight());wm.updateViewLayout(window,params);}return true;}
                if(event.getActionMasked()==MotionEvent.ACTION_UP){if(tap&&!moved){v.performClick();show(true);}return true;}
                return event.getActionMasked()==MotionEvent.ACTION_CANCEL;
            }
        });
        if(tap)handle.setOnClickListener(v->{});
    }
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);updateBounds();if(window!=null)show(expanded);}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);Store.running(this,false);if(window!=null){wm.removeView(window);window=null;}stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
