package io.github.carrot.suite;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;

final class Controls extends LinearLayout {
    private final int id;
    private final TextView status, balances;
    private final ArrayList<Switch> switches = new ArrayList<>();
    private boolean updating;
    Controls(Context c, int game) {
        super(c); id = game; setOrientation(1);
        status = Ui.text(c,"等待游戏",12,Ui.GREEN,true);
        balances = Ui.text(c,"",13,Ui.MUTED,false);
        addView(status); Ui.space(this,6); addView(balances); Ui.space(this,10);
        for (int i=0;i<GameSpec.KEYS[id].length;i++) {
            final String key = GameSpec.KEYS[id][i];
            Switch toggle = new Switch(c); toggle.setText(GameSpec.LABELS[id][i]);
            toggle.setTextSize(15); toggle.setTextColor(Ui.INK); toggle.setSwitchPadding(Ui.dp(c,12));
            toggle.setMinHeight(Ui.dp(c,52)); toggle.setShowText(false);
            toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Ui.GREEN,0xFF8C9C92}));
            toggle.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xFFB5D0BC,Ui.LINE}));
            toggle.setOnCheckedChangeListener((v,on)->{if(!updating)Store.write(c,id,key,on);});
            switches.add(toggle); addView(toggle,new LayoutParams(-1,-2));
            if (i<GameSpec.KEYS[id].length-1) { android.view.View line = new android.view.View(c); line.setBackgroundColor(Ui.LINE); addView(line,new LayoutParams(-1,Ui.dp(c,1))); }
        }
        if (id == 2) {
            Ui.space(this,12);
            addView(Ui.button(c,"解锁关卡与资源",false,v->{
                if (!Store.running(c)) { android.widget.Toast.makeText(c,"请先运行",0).show(); return; }
                Bundle b = new Bundle(); b.putBoolean("unlock",true);
                c.getContentResolver().call(Store.URI,"configure","2",b);
                android.widget.Toast.makeText(c,"已发送",0).show();
            }),new LayoutParams(-1,Ui.dp(c,48)));
        }
        refresh();
    }
    void refresh() {
        Bundle b = Store.read(getContext(),id); status.setText(Store.label(id,b));
        boolean connected = Store.connected(b);
        long gold = id == 1 ? b.getInt("status") == 1 ? 999999 : -1 : b.getLong("coins",-1);
        String value = "对局  " + Ui.number(connected?gold:-1);
        if (id>1) value += "   ·   钻石  " + Ui.number(connected?b.getLong("gems",-1):-1);
        balances.setText(value);
        updating = true;
        for (int i=0;i<switches.size();i++) switches.get(i).setChecked(b.getBoolean(GameSpec.KEYS[id][i],GameSpec.defaultOn(id,GameSpec.KEYS[id][i])));
        updating = false;
    }
}
