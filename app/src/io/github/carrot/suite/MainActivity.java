package io.github.carrot.suite;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler();
    private Controls controls;
    private TextView run;
    private boolean pendingRun;
    private final Runnable refresh = new Runnable() { @Override public void run() {
        if (controls != null) controls.refresh();
        if (run != null) run.setText(Store.running(MainActivity.this)?"停止运行":"运行");
        handler.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle b) {
        super.onCreate(b); pendingRun = b != null && b.getBoolean("pending");
        getWindow().setStatusBarColor(Ui.BG); getWindow().setNavigationBarColor(Ui.BG);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        render();
    }
    private void render() {
        int id = Store.selected(this);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Ui.BG);
        LinearLayout page = Ui.column(this); page.setPadding(Ui.dp(this,24),Ui.dp(this,24),Ui.dp(this,24),Ui.dp(this,24));
        scroll.addView(page,new ScrollView.LayoutParams(-1,-2));
        LinearLayout hero = Ui.column(this); Ui.pad(hero,24); hero.setBackground(Ui.shape(this,Ui.INK,24,0));
        LinearLayout heading = Ui.row(this);
        ImageView icon = new ImageView(this); icon.setImageResource(R.drawable.ic_carrot); icon.setImportantForAccessibility(2);
        heading.addView(icon,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        TextView tag = Ui.text(this,"CARROT / 01—04",12,0xFFBFD6C8,true); tag.setPadding(Ui.dp(this,14),0,0,0); heading.addView(tag);
        hero.addView(heading); Ui.space(hero,24); hero.addView(Ui.text(this,"保卫萝卜合集",27,Ui.CARD,true));
        Ui.space(hero,8); hero.addView(Ui.text(this,"四代游戏，一个控制入口",14,0xFFCADBD1,false));
        page.addView(hero,new LinearLayout.LayoutParams(-1,-2)); Ui.space(page,24);
        page.addView(Ui.text(this,"选择游戏",17,Ui.INK,true)); Ui.space(page,12);
        for (int row=0;row<2;row++) {
            LinearLayout line = Ui.row(this);
            for(int col=0;col<2;col++) {
                final int game = row*2+col+1;
                LinearLayout card = Ui.column(this); card.setGravity(16); Ui.pad(card,16);
                card.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x18245C49),Ui.shape(this,game==id?Ui.PALE:Ui.CARD,18,game==id?Ui.GREEN:Ui.LINE),null));
                card.addView(Ui.text(this,String.format(java.util.Locale.US,"0%d",game),22,game==id?Ui.GREEN:Ui.MUTED,true));
                Ui.space(card,6); card.addView(Ui.text(this,"保卫萝卜 " + game,15,Ui.INK,true));
                Ui.space(card,4); card.addView(Ui.text(this,GameSpec.installed(this,game)==null?"未安装":game==id?"已选择":"已安装",11,Ui.MUTED,false));
                card.setContentDescription("保卫萝卜"+game+(game==id?"，已选择":""));
                card.setOnClickListener(v->{Store.select(this,game);render();});
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0,-2,1); if(col==0)cp.setMarginEnd(Ui.dp(this,12));
                line.addView(card,cp);
            }
            page.addView(line,new LinearLayout.LayoutParams(-1,-2)); if(row==0)Ui.space(page,12);
        }
        Ui.space(page,24);
        LinearLayout card = Ui.column(this); Ui.pad(card,20); card.setBackground(Ui.shape(this,Ui.CARD,22,Ui.LINE));
        card.addView(Ui.text(this,"保卫萝卜 " + id,18,Ui.INK,true)); Ui.space(card,12);
        controls = new Controls(this,id); card.addView(controls,new LinearLayout.LayoutParams(-1,-2)); page.addView(card);
        Ui.space(page,12);
        LinearLayout layout = Ui.column(this); layout.setBackgroundColor(Ui.BG);
        layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer = Ui.row(this);
        footer.setPadding(Ui.dp(this,24),Ui.dp(this,12),Ui.dp(this,24),Ui.dp(this,16));
        run = Ui.button(this,Store.running(this)?"停止运行":"运行",true,v->{if(Store.running(this))stop();else begin();});
        footer.addView(run,new LinearLayout.LayoutParams(0,Ui.dp(this,56),1));
        TextView setup = Ui.button(this,"LSPosed",false,v->{
            Intent launch = getPackageManager().getLaunchIntentForPackage("org.lsposed.manager");
            if(launch!=null)startActivity(launch); else android.widget.Toast.makeText(this,"请在 LSPosed 中启用合集并勾选游戏",0).show();
        });
        LinearLayout.LayoutParams setupParams = new LinearLayout.LayoutParams(Ui.dp(this,104),Ui.dp(this,56));
        setupParams.setMarginStart(Ui.dp(this,12));footer.addView(setup,setupParams);
        layout.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        setContentView(layout);
    }
    private void begin() {
        if (!Settings.canDrawOverlays(this)) {
            pendingRun = true;
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))); }
            catch (Exception e) { pendingRun=false;android.widget.Toast.makeText(this,"请允许显示悬浮窗",0).show(); }
            return;
        }
        pendingRun = false;
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=0)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7);
        startForegroundService(new Intent(this,FloatingService.class));
    }
    private void stop() {
        Store.running(this,false); stopService(new Intent(this,FloatingService.class)); render();
    }
    @Override protected void onResume() { super.onResume();
        if(pendingRun && Settings.canDrawOverlays(this))begin();
        handler.removeCallbacks(refresh); handler.post(refresh);
    }
    @Override protected void onPause() { handler.removeCallbacks(refresh);super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle b) { b.putBoolean("pending",pendingRun);super.onSaveInstanceState(b); }
}
