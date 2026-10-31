package com.qureshisahib;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.PrintWriter;
import java.io.StringWriter;

public class MainActivity extends Activity {

    private static final int REQ_NOTIF = 100;

    private static final int GOLD = 0xFFFFD700;
    private static final int GREEN = 0xFF2ECC71;
    private static final int RED = 0xFFE74C3C;
    private static final int CARD = 0xFF1A1A1A;

    private TextView statusDot, statusText, uptimeText, statsText, logText;
    private ScrollView logScroll;
    private Switch botSwitch;
    private boolean uiReady = false;
    private boolean registered = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            try {
                updateLive();
            } catch (Throwable ignored) {
            }
            handler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String a = intent.getAction();
                if (BotService.ACTION_LOG.equals(a)) {
                    refreshLog();
                } else if (BotService.ACTION_STATE.equals(a)) {
                    syncSwitch();
                }
            } catch (Throwable ignored) {
            }
        }
    };

    private final CompoundButton.OnCheckedChangeListener switchListener = (button, checked) -> {
        try {
            if (checked) requestStart();
            else stopBot();
        } catch (Throwable t) {
            BotService.log(this, "ERROR: " + t);
        }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashSaver();
        try {
            buildUi();
            uiReady = true;
        } catch (Throwable t) {
            showFatal(t);
            return;
        }
        showLastCrash();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!uiReady) return;
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(BotService.ACTION_LOG);
            f.addAction(BotService.ACTION_STATE);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(receiver, f);
            }
            registered = true;

            syncSwitch();
            refreshLog();
            handler.post(ticker);
        } catch (Throwable t) {
            showFatal(t);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(ticker);
        if (registered) {
            try {
                unregisterReceiver(receiver);
            } catch (Throwable ignored) {
            }
            registered = false;
        }
    }

    // ---------------------------------------------------------------- crash reporting

    private void installCrashSaver() {
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            try {
                getSharedPreferences("crash", MODE_PRIVATE)
                        .edit().putString("last", stackTrace(ex)).commit();
            } catch (Throwable ignored) {
            }
            if (old != null) old.uncaughtException(thread, ex);
        });
    }

    private void showLastCrash() {
        try {
            SharedPreferences p = getSharedPreferences("crash", MODE_PRIVATE);
            String last = p.getString("last", null);
            if (last != null) {
                p.edit().remove("last").apply();
                new AlertDialog.Builder(this)
                        .setTitle("Last crash")
                        .setMessage(last)
                        .setPositiveButton("OK", null)
                        .show();
            }
        } catch (Throwable ignored) {
        }
    }

    private void showFatal(Throwable t) {
        TextView tv = new TextView(this);
        tv.setText("ERROR - send this text:\n\n" + stackTrace(t));
        tv.setTextColor(Color.RED);
        tv.setBackgroundColor(Color.BLACK);
        tv.setTextSize(12);
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(16), dp(40), dp(16), dp(16));
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.BLACK);
        sv.addView(tv);
        setContentView(sv);
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    // ---------------------------------------------------------------- bot control

    private void requestStart() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } else {
            startBot();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_NOTIF) {
            if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
                BotService.log(this, "Notification permission denied - status shows only in the app");
            }
            try {
                startBot();
            } catch (Throwable t) {
                BotService.log(this, "ERROR: " + t);
            }
        }
    }

    private void startBot() {
        startForegroundService(new Intent(this, BotService.class).setAction(BotService.ACTION_START));
    }

    private void stopBot() {
        startService(new Intent(this, BotService.class).setAction(BotService.ACTION_STOP));
    }

    // ---------------------------------------------------------------- UI updates

    private void syncSwitch() {
        botSwitch.setOnCheckedChangeListener(null);
        botSwitch.setChecked(BotService.running);
        botSwitch.setOnCheckedChangeListener(switchListener);
        updateLive();
    }

    private void updateLive() {
        boolean running = BotService.running;

        statusDot.setTextColor(running ? GREEN : RED);
        statusText.setText(running ? "BOT STATUS: RUNNING" : "BOT STATUS: STOPPED");

        long ms = running ? System.currentTimeMillis() - BotService.startTime : 0;
        uptimeText.setText("Uptime  " + BotService.formatUptime(ms));

        statsText.setText("Ticks: " + (running ? BotService.ticks : 0)
                + "   |   Network: " + (BotService.isOnline(this) ? "ONLINE" : "OFFLINE"));
    }

    private void refreshLog() {
        logText.setText(BotService.getLog());
        logScroll.post(() -> logScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void copyLog() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("bot log", BotService.getLog()));
        Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show();
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open battery settings", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- UI building

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(20), dp(48), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("♛ QURESHI SAHIB ♛");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(GOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(0, 0, 0, dp(20)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(rounded(CARD, GOLD, dp(16)));

        statusDot = new TextView(this);
        statusDot.setText("●");
        statusDot.setTextSize(40);
        statusDot.setTextColor(RED);
        statusDot.setGravity(Gravity.CENTER);
        card.addView(statusDot);

        statusText = new TextView(this);
        statusText.setText("BOT STATUS: STOPPED");
        statusText.setTextSize(20);
        statusText.setTypeface(Typeface.DEFAULT_BOLD);
        statusText.setTextColor(Color.WHITE);
        statusText.setGravity(Gravity.CENTER);
        card.addView(statusText, lp(0, dp(6), 0, dp(4)));

        uptimeText = new TextView(this);
        uptimeText.setText("Uptime  00:00:00");
        uptimeText.setTextSize(16);
        uptimeText.setTypeface(Typeface.MONOSPACE);
        uptimeText.setTextColor(GOLD);
        uptimeText.setGravity(Gravity.CENTER);
        card.addView(uptimeText, lp(0, 0, 0, dp(4)));

        statsText = new TextView(this);
        statsText.setText("Ticks: 0");
        statsText.setTextSize(13);
        statsText.setTextColor(0xFFAAAAAA);
        statsText.setGravity(Gravity.CENTER);
        card.addView(statsText, lp(0, 0, 0, dp(14)));

        botSwitch = new Switch(this);
        botSwitch.setText("Bot Power  ");
        botSwitch.setTextSize(18);
        botSwitch.setTextColor(Color.WHITE);
        card.addView(botSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(card, lp(0, 0, 0, dp(16)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button clear = smallButton("Clear Log");
        clear.setOnClickListener(v -> BotService.clearLog(this));

        Button copy = smallButton("Copy Log");
        copy.setOnClickListener(v -> copyLog());

        Button battery = smallButton("Battery");
        battery.setOnClickListener(v -> openBatterySettings());

        row.addView(clear, weight());
        row.addView(copy, weight());
        row.addView(battery, weight());
        root.addView(row, lp(0, 0, 0, dp(12)));

        TextView logTitle = new TextView(this);
        logTitle.setText("ACTIVITY LOG");
        logTitle.setTextSize(14);
        logTitle.setTextColor(GOLD);
        root.addView(logTitle, lp(0, 0, 0, dp(6)));

        logScroll = new ScrollView(this);
        logScroll.setBackground(rounded(CARD, 0xFF333333, dp(12)));
        logScroll.setPadding(dp(12), dp(12), dp(12), dp(12));

        logText = new TextView(this);
        logText.setTextSize(13);
        logText.setTextColor(0xFFBBBBBB);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setTextIsSelectable(true);
        logScroll.addView(logText);

        root.addView(logScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private GradientDrawable rounded(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(l, t, r, b);
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
