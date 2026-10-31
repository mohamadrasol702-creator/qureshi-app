package com.qureshisahib;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

public class FeaturesActivity extends AppCompatActivity {
    private Plan plan;
    private LinearLayout root;
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        plan = Plan.byId(FeatureManager.getPlan(this));
        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.addView(root);
        setContentView(sv);
        render();
    }

    private void render() {
        root.removeAllViews();
        int on = 0;
        for (FeatureManager.Feature f : FeatureManager.ALL)
            if (FeatureManager.active(this, f, plan.level)) on++;

        TextView head = new TextView(this);
        head.setText("نسخه‌ی " + plan.title);
        head.setTextSize(24); head.setTextColor(plan.color);
        root.addView(head);

        TextView count = new TextView(this);
        count.setText(on + " از " + FeatureManager.ALL.length + " قابلیت روشن است");
        count.setPadding(0, dp(4), 0, dp(12));
        root.addView(count);

        // کلید اصلی
        root.addView(makeRow("کلید اصلی (همه)", FeatureManager.master(this), true, (bv, v) -> {
            FeatureManager.setMaster(this, v); render();
        }, false));

        String last = "";
        for (final FeatureManager.Feature f : FeatureManager.ALL) {
            if (!f.group.equals(last)) {
                last = f.group;
                TextView g = new TextView(this);
                g.setText(last); g.setTextSize(16); g.setTextColor(Color.DKGRAY);
                g.setPadding(0, dp(16), 0, dp(4));
                root.addView(g);
            }
            boolean locked = plan.level < f.min;
            boolean checked = !locked && FeatureManager.master(this) && FeatureManager.userOn(this, f.id);
            View row = makeRow(f.fa, checked, !locked, (bv, v) -> {
                FeatureManager.setUser(this, f.id, v); render();
            }, locked);
            root.addView(row);
        }

        Button change = new Button(this);
        change.setText(plan.level < 2 ? "ارتقا به پرو" : "تغییر نسخه");
        change.setOnClickListener(v -> {
            startActivity(new Intent(this, PlanActivity.class)); finish();
        });
        root.addView(change);
    }

    private interface OnSwitch { void on(View v, boolean checked); }

    private View makeRow(String text, boolean checked, boolean enabled, OnSwitch cb, boolean locked) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView label = new TextView(this);
        label.setText((locked ? "🔒 " : "") + text);
        label.setTextSize(17);
        label.setTextColor(locked ? Color.parseColor("#616161") : (checked ? Color.BLACK : Color.parseColor("#9E9E9E")));
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

        if (locked) {
            TextView up = new TextView(this);
            up.setText("ارتقا"); up.setTextColor(Color.parseColor("#E53935"));
            up.setOnClickListener(v -> { startActivity(new Intent(this, PlanActivity.class)); finish(); });
            row.addView(up);
        } else {
            SwitchCompat s = new SwitchCompat(this);
            s.setChecked(checked);
            // روشن = رنگ نسخه، خاموش = خاکستری روشن
            int[][] st = { {android.R.attr.state_checked}, {} };
            s.setThumbTintList(new ColorStateList(st, new int[]{plan.color, Color.parseColor("#E0E0E0")}));
            s.setTrackTintList(new ColorStateList(st, new int[]{(plan.color & 0x00FFFFFF) | 0x66000000, Color.parseColor("#BDBDBD")}));
            s.setOnCheckedChangeListener((bv, v) -> cb.on(bv, v));
            row.addView(s);
        }
        return row;
    }
}
