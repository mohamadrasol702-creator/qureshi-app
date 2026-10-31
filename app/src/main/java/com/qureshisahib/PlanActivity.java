package com.qureshisahib;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class PlanActivity extends AppCompatActivity {
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(32), dp(16), dp(16));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.addView(root);

        TextView t = new TextView(this);
        t.setText("نسخه‌ی خود را انتخاب کنید");
        t.setTextSize(22); t.setTextColor(Color.BLACK); t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(16));
        root.addView(t);

        String[][] info = {
            {"همه امکانات • ۵ شماره • AI قوی (qwen-7b)", "تحلیل هوشمند • پیام همگانی • Code Studio"},
            {"۲ شماره • ۲۵ گروه • AI (qwen-3b)", "چت‌بات • سفارش‌گیری • ضد اسپم"},
            {"۱ شماره • ۳ گروه • AI کوچک", "ضد لینک • خوش‌آمدگویی • فروشگاه کوچک"}
        };
        for (int i = 0; i < Plan.ALL.length; i++) {
            final Plan p = Plan.ALL[i];
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(16), dp(16), dp(16));
            GradientDrawable g = new GradientDrawable();
            g.setColor(p.color); g.setCornerRadius(dp(16));
            card.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(12);

            TextView name = new TextView(this);
            name.setText(p.title); name.setTextSize(24); name.setTextColor(Color.WHITE);
            TextView price = new TextView(this);
            price.setText(p.price == 0 ? "رایگان" : "$" + p.price + " / ماه");
            price.setTextSize(18); price.setTextColor(Color.WHITE);
            TextView f = new TextView(this);
            f.setText(info[i][0] + "\n" + info[i][1]);
            f.setTextColor(Color.WHITE); f.setPadding(0, dp(8), 0, 0);
            card.addView(name); card.addView(price); card.addView(f);
            card.setOnClickListener(v -> choose(p));
            root.addView(card, lp);
        }
        setContentView(sv);
    }

    private void choose(Plan p) {
        if (p.price > 0) {
            new AlertDialog.Builder(this)
                .setTitle("نسخه‌ی " + p.title)
                .setMessage("قیمت: $" + p.price + " در ماه\n\n(درگاه پرداخت بعداً وصل می‌شود. فعلاً فعال می‌شود.)")
                .setPositiveButton("ادامه", (d, w) -> done(p))
                .setNegativeButton("لغو", null).show();
        } else done(p);
    }
    private void done(Plan p) {
        FeatureManager.setPlan(this, p.id);
        startActivity(new Intent(this, FeaturesActivity.class));
        finish();
    }
}
