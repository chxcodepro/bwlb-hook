package io.github.carrot.suite;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = 0xFFF5F6F1, CARD = 0xFFFFFFFF, INK = 0xFF183D34,
        MUTED = 0xFF64746D, GREEN = 0xFF245C49, LINE = 0xFFDFE6DF, PALE = 0xFFE9F1EA, ORANGE = 0xFFF6A654;
    static int dp(Context c, float n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(Context c, int color, int radius, int border) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius));
        if (border != 0) d.setStroke(dp(c,1),border);
        return d;
    }
    static LinearLayout column(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(1); return v; }
    static LinearLayout row(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(0); v.setGravity(16); return v; }
    static TextView text(Context c, String value, int size, int color, boolean bold) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("tnum"); if (bold) t.setTypeface(Typeface.create("sans-serif-medium",0)); return t;
    }
    static TextView button(Context c, String label, boolean primary, View.OnClickListener click) {
        TextView b = text(c,label,15,primary?CARD:INK,true); b.setGravity(17);
        b.setMinHeight(dp(c,52)); b.setPadding(dp(c,12),0,dp(c,12),0);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(primary?0x33FFFFFF:0x15245C49),shape(c,primary?GREEN:PALE,16,0),null));
        b.setOnClickListener(click); return b;
    }
    static void space(LinearLayout parent, int height) {
        View v = new View(parent.getContext()); parent.addView(v,new LinearLayout.LayoutParams(1,dp(parent.getContext(),height)));
    }
    static void pad(View v, int n) { int d = dp(v.getContext(),n); v.setPadding(d,d,d,d); }
    static String number(long n) { return n < 0 ? "—" : String.format(java.util.Locale.US,"%,d",n); }
    private Ui() { }
}
