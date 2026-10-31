package com.qureshisahib;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS = "qureshi_prefs";
    private static final String KEY_RUNNING = "bot_running";

    private static final int GOLD = 0xFFFFD700;
    private static final int GREEN = 0xFF2ECC71;
    private static final int RED = 0xFFE74C3C;
    private static final int CARD = 0xFF1A1A1A;

    private TextView statusText, statusDot, logText;
    private ScrollView logScroll;
    private Switch botSwitch;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(1);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(20), dp(40), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("♛ QURESHI SAHIB ♛");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(GOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap(0, 0, 0, dp(24)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(1);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(20), dp(24), dp(20), dp(24));
        card.setBackground(rounded(CARD, GOLD, dp(16)));

        statusDot = new TextView(this);
        statusDot.setText("●");
        statusDot.setTextSize(40);
        statusDot.setGravity(Gravity.CENTER);
        card.addView(statusDot);

        statusText = new TextView(this);
        statusText.setTextSize(20);
        statusText.setTypeface(Typeface.DEFAULT_BOLD);
        statusText.setTextColor(Color.WHITE);
        statusText.setGravity(Gravity.CENTER);
        card.addView(statusText, matchWrap(0, dp(8), 0, dp(16)));

        botSwitch = new Switch(this);
        botSwitch.setText("Bot Power  ");
        botSwitch.setTextSize(18);
        botSwitch.setTextColor(Color.WHITE);
        card.addView(botSwitch);

        root.addView(card, matchWrap(0, 0, 0, dp(20)));

        TextView logTitle = new TextView(this);
        logTitle.setText("ACTIVITY LOG");
        logTitle.setTextSize(14);
        logTitle.setTextColor(GOLD);
        root.addView(logTitle, matchWrap(0, 0, 0, dp(6)));

        logScroll = new ScrollView(this);
        logScroll.setBackground(rounded(CARD, 0xFF333333, dp(12)));
        logScroll.setPadding(dp(12), dp(12), dp(12), dp(12));

        logText = new TextView(this);
        logText.setTextSize(13);
        logText.setTextColor(0xFFBBBBBB);
        logText.setTypeface(Typeface.MONOSPACE);
        logScroll.addView(logText);

        root.addView(logScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        boolean wasRunning = prefs.getBoolean(KEY_RUNNING, false);
        botSwitch.setChecked(wasRunning);
        render(wasRunning);
        log("App opened. Bot is " + (wasRunning ? "RUNNING" : "STOPPED"));

        botSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            prefs.edit().putBoolean(KEY_RUNNING, isChecked).apply();
            render(isChecked);
            if (isChecked) {
                log("Bot started");
                onBotStart();
            } else {
                log("Bot stopped");
                onBotStop();
            }
        });
    }

    private void onBotStart() {
        // Real bot connection will be added later.
    }

    private void onBotStop() {
        // Real bot stop logic will be added later.
    }

    private void render(boolean running) {
        statusText.setText(running ? "BOT STATUS: RUNNING" : "BOT STATUS: STOPPED");
        statusDot.setTextColor(running ? GREEN : RED);
    }

    private void log(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        logText.append("[" + time + "] " + message + "\n");
        logScroll.post(() -> logScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private GradientDrawable rounded(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout.LayoutParams matchWrap(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(l, t, r, b);
        return p;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
