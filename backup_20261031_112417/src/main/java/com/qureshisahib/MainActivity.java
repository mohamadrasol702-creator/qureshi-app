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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_NOTIF = 100;

    private static final int GOLD = 0xFFFFD700;
    private static final int GREEN = 0xFF2ECC71;
    private static final int RED = 0xFFE74C3C;
    private static final int CARD = 0xFF1A1A1A;

    private LinearLayout root;
    private TextView statusDot, statusText, uptimeText, statsText, logText;
    private ScrollView logScroll;
    private Switch botSwitch;
    private EditText cmdInput;
    private LinearLayout historyRow;
    private Button filterBtn;

    private SharedPreferences prefs;
    private boolean uiReady = false;
    private boolean registered = false;
    private boolean errorsOnly = false;
    private volatile boolean dialogPolling = false;

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
        S.load(this);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        installCrashSaver();
        try {
            buildUi();
            uiReady = true;
        } catch (Throwable t) {
            showFatal(t);
            return;
        }
        showLastCrash();
        if (!prefs.getString("pin", "").isEmpty()) {
            root.setVisibility(View.INVISIBLE);
            askPin();
        }
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
            refreshHistory();
            handler.post(ticker);
        } catch (Throwable t) {
            showFatal(t);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(ticker);
        dialogPolling = false;
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

    // ---------------------------------------------------------------- PIN lock

    private static String sha(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void askPin() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setGravity(Gravity.CENTER);
        new AlertDialog.Builder(this)
                .setTitle(S.t("pin_title"))
                .setView(et)
                .setCancelable(false)
                .setPositiveButton(S.t("ok"), (d, w) -> {
                    if (sha(et.getText().toString()).equals(prefs.getString("pin", ""))) {
                        root.setVisibility(View.VISIBLE);
                    } else {
                        toast(S.t("pin_wrong"));
                        askPin();
                    }
                })
                .setNegativeButton(S.t("close"), (d, w) -> finish())
                .show();
    }

    private void askNewPin() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setHint(S.t("pin_new"));
        et.setGravity(Gravity.CENTER);
        new AlertDialog.Builder(this)
                .setTitle(S.t("pin_set"))
                .setView(et)
                .setPositiveButton(S.t("ok"), (d, w) -> {
                    String p = et.getText().toString();
                    if (p.length() < 4) {
                        toast(S.t("pin_short"));
                    } else {
                        prefs.edit().putString("pin", sha(p)).apply();
                        toast(S.t("pin_saved"));
                    }
                })
                .setNegativeButton(S.t("cancel"), null)
                .show();
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
                BotService.log(this, S.t("notif_denied"));
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

    // ---------------------------------------------------------------- commands

    private void sendCommand(final String raw) {
        final String text = raw.replace('\n', ' ').trim();
        if (text.isEmpty()) return;
        addHistory(text);
        new Thread(() -> {
            String r = ControlClient.post("/api/command", text);
            BotService.log(this, "> " + text);
            runOnUiThread(() -> toast(r == null ? S.t("cmd_fail") : S.t("cmd_sent")));
        }).start();
    }

    private List<String> loadHistory() {
        List<String> l = new ArrayList<>();
        String s = prefs.getString("hist", "");
        if (!s.isEmpty()) {
            for (String x : s.split("\n")) {
                if (!x.trim().isEmpty()) l.add(x);
            }
        }
        return l;
    }

    private void addHistory(String cmd) {
        List<String> h = loadHistory();
        h.remove(cmd);
        h.add(0, cmd);
        while (h.size() > 8) h.remove(h.size() - 1);
        prefs.edit().putString("hist", TextUtils.join("\n", h)).apply();
        refreshHistory();
    }

    private void refreshHistory() {
        if (historyRow == null) return;
        historyRow.removeAllViews();
        for (final String x : loadHistory()) {
            Button b = smallButton(x.length() > 18 ? x.substring(0, 18) + "…" : x);
            b.setOnClickListener(v -> sendCommand(x));
            b.setOnLongClickListener(v -> {
                cmdInput.setText(x);
                return true;
            });
            historyRow.addView(b, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    // ---------------------------------------------------------------- connect WhatsApp

    private void showConnect() {
        String[] items = {S.t("opt_qr"), S.t("opt_phone")};
        new AlertDialog.Builder(this)
                .setTitle(S.t("connect_title"))
                .setItems(items, (d, which) -> {
                    if (which == 0) showQr();
                    else showPhone();
                })
                .setNegativeButton(S.t("cancel"), null)
                .show();
    }

    private void showQr() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(16), dp(16), dp(16), dp(8));

        final ImageView img = new ImageView(this);
        box.addView(img, new LinearLayout.LayoutParams(dp(260), dp(260)));

        final TextView info = new TextView(this);
        info.setText(S.t("qr_wait"));
        info.setGravity(Gravity.CENTER);
        box.addView(info, lp(0, dp(8), 0, 0));

        TextView help = new TextView(this);
        help.setText(S.t("qr_help"));
        help.setTextSize(12);
        help.setGravity(Gravity.CENTER);
        box.addView(help, lp(0, dp(8), 0, 0));

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(S.t("connect_title"))
                .setView(box)
                .setNegativeButton(S.t("close"), null)
                .create();
        dlg.setOnDismissListener(d -> dialogPolling = false);
        dialogPolling = true;
        dlg.show();

        new Thread(() -> {
            while (dialogPolling) {
                String r = ControlClient.get("/api/qr");
                Bitmap bm = null;
                if (r != null) {
                    try {
                        String u = new JSONObject(r).optString("qr", "");
                        if (u.startsWith("data:image")) {
                            byte[] b = Base64.decode(u.substring(u.indexOf(',') + 1), Base64.DEFAULT);
                            bm = BitmapFactory.decodeByteArray(b, 0, b.length);
                        }
                    } catch (Exception ignored) {
                    }
                }
                final Bitmap fb = bm;
                final boolean off = (r == null);
                runOnUiThread(() -> {
                    if (fb != null) {
                        img.setImageBitmap(fb);
                        info.setText("");
                    } else {
                        info.setText(off ? S.t("control_off") : S.t("qr_wait"));
                    }
                });
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private void showPhone() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_PHONE);
        et.setHint(S.t("phone_hint"));
        et.setGravity(Gravity.CENTER);
        new AlertDialog.Builder(this)
                .setTitle(S.t("phone_title"))
                .setView(et)
                .setPositiveButton(S.t("get_code"), (d, w) -> {
                    String digits = et.getText().toString().replaceAll("\\D", "");
                    if (digits.length() < 8 || digits.length() > 15) {
                        toast(S.t("phone_bad"));
                    } else {
                        startPairing(digits);
                    }
                })
                .setNegativeButton(S.t("cancel"), null)
                .show();
    }

    private void startPairing(final String phone) {
        toast("…");
        new Thread(() -> {
            ControlClient.post("/api/backup", "");
            String r = ControlClient.post("/api/pair", phone);
            if (r == null) {
                runOnUiThread(() -> toast(S.t("cmd_fail")));
                return;
            }
            String code = "";
            for (int i = 0; i < 30 && code.isEmpty(); i++) {
                String g = ControlClient.get("/api/paircode");
                if (g != null) {
                    try {
                        code = new JSONObject(g).optString("code", "");
                    } catch (Exception ignored) {
                    }
                }
                if (code.isEmpty()) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
            final String fc = code;
            runOnUiThread(() -> showCode(fc));
        }).start();
    }

    private void showCode(String code) {
        if (code.isEmpty()) {
            toast(S.t("code_fail"));
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(16), dp(16), dp(16), dp(8));

        TextView big = new TextView(this);
        big.setText(code);
        big.setTextSize(34);
        big.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        big.setTextIsSelectable(true);
        big.setGravity(Gravity.CENTER);
        box.addView(big);

        TextView help = new TextView(this);
        help.setText(S.t("code_help"));
        help.setTextSize(12);
        help.setGravity(Gravity.CENTER);
        box.addView(help, lp(0, dp(10), 0, 0));

        new AlertDialog.Builder(this)
                .setTitle(S.t("code_title"))
                .setView(box)
                .setPositiveButton(S.t("close"), null)
                .show();
    }

    private void doBackup() {
        new Thread(() -> {
            String r = ControlClient.post("/api/backup", "");
            runOnUiThread(() -> toast(r == null ? S.t("backup_fail") : S.t("backup_ok")));
        }).start();
    }

    // ---------------------------------------------------------------- settings

    private void showSettings() {
        boolean hasPin = !prefs.getString("pin", "").isEmpty();
        List<String> items = new ArrayList<>();
        items.add(S.fa ? "زبان: تغییر به English" : "Language: switch to فارسی");
        items.add(S.t("pin_set"));
        if (hasPin) items.add(S.t("pin_remove"));
        items.add(S.t("battery"));

        final boolean pin = hasPin;
        new AlertDialog.Builder(this)
                .setTitle(S.t("settings_title"))
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        S.save(this, !S.fa);
                        recreate();
                    } else if (which == 1) {
                        askNewPin();
                    } else if (pin && which == 2) {
                        prefs.edit().remove("pin").apply();
                        toast(S.t("pin_removed"));
                    } else {
                        openBatterySettings();
                    }
                })
                .setNegativeButton(S.t("close"), null)
                .show();
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception e) {
            toast(S.t("battery_fail"));
        }
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
        statusText.setText(S.t("status_label") + ": " + (running ? S.t("running") : S.t("stopped")));

        long ms = running ? System.currentTimeMillis() - BotService.startTime : 0;
        uptimeText.setText(S.t("uptime") + "  " + BotService.formatUptime(ms));

        statsText.setText(S.t("network") + ": "
                + (BotService.isOnline(this) ? S.t("online") : S.t("offline"))
                + "\n" + S.t("whatsapp") + ": " + S.status(BotService.botStatus));
    }

    private void refreshLog() {
        String all = BotService.getLog();
        if (errorsOnly) {
            StringBuilder sb = new StringBuilder();
            for (String line : all.split("\n")) {
                String l = line.toLowerCase();
                if (l.contains("error") || l.contains("offline") || l.contains("disconnected")
                        || l.contains("denied") || l.contains("خطا") || l.contains("قطع")
                        || l.contains("خاموش")) {
                    sb.append(line).append('\n');
                }
            }
            all = sb.toString();
        }
        logText.setText(all);
        logScroll.post(() -> logScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void copyLog() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("bot log", BotService.getLog()));
        toast(S.t("copied"));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- UI building

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(16), dp(40), dp(16), dp(12));
        root.setLayoutDirection(S.fa ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        TextView title = new TextView(this);
        title.setText(S.t("title"));
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(GOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(0, 0, 0, dp(12)));

        // status card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(rounded(CARD, GOLD, dp(16)));

        statusDot = new TextView(this);
        statusDot.setText("●");
        statusDot.setTextSize(30);
        statusDot.setTextColor(RED);
        statusDot.setGravity(Gravity.CENTER);
        card.addView(statusDot);

        statusText = new TextView(this);
        statusText.setTextSize(18);
        statusText.setTypeface(Typeface.DEFAULT_BOLD);
        statusText.setTextColor(Color.WHITE);
        statusText.setGravity(Gravity.CENTER);
        card.addView(statusText, lp(0, dp(2), 0, dp(2)));

        uptimeText = new TextView(this);
        uptimeText.setTextSize(15);
        uptimeText.setTypeface(Typeface.MONOSPACE);
        uptimeText.setTextColor(GOLD);
        uptimeText.setGravity(Gravity.CENTER);
        card.addView(uptimeText, lp(0, 0, 0, dp(2)));

        statsText = new TextView(this);
        statsText.setTextSize(13);
        statsText.setTextColor(0xFFAAAAAA);
        statsText.setGravity(Gravity.CENTER);
        card.addView(statsText, lp(0, 0, 0, dp(8)));

        botSwitch = new Switch(this);
        botSwitch.setText(S.t("power") + "  ");
        botSwitch.setTextSize(17);
        botSwitch.setTextColor(Color.WHITE);
        card.addView(botSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(card, lp(0, 0, 0, dp(8)));

        // main buttons
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        Button connect = smallButton(S.t("connect"));
        connect.setOnClickListener(v -> showConnect());
        Button backup = smallButton(S.t("backup"));
        backup.setOnClickListener(v -> doBackup());
        Button settings = smallButton(S.t("settings"));
        settings.setOnClickListener(v -> showSettings());
        row1.addView(connect, weight());
        row1.addView(backup, weight());
        row1.addView(settings, weight());
        root.addView(row1, lp(0, 0, 0, dp(6)));

        // command input
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);

        cmdInput = new EditText(this);
        cmdInput.setHint(S.t("cmd_hint"));
        cmdInput.setHintTextColor(0xFF777777);
        cmdInput.setTextColor(Color.WHITE);
        cmdInput.setTextSize(15);
        cmdInput.setSingleLine(true);
        cmdInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        cmdInput.setBackground(rounded(CARD, 0xFF444444, dp(10)));

        Button send = smallButton(S.t("send"));
        send.setOnClickListener(v -> {
            sendCommand(cmdInput.getText().toString());
            cmdInput.setText("");
        });

        row2.addView(cmdInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row2.addView(send, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(row2, lp(0, 0, 0, dp(4)));

        // history chips
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        historyRow = new LinearLayout(this);
        historyRow.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(historyRow);
        root.addView(hs, lp(0, 0, 0, dp(6)));

        // log header
        LinearLayout logHead = new LinearLayout(this);
        logHead.setOrientation(LinearLayout.HORIZONTAL);
        logHead.setGravity(Gravity.CENTER_VERTICAL);

        TextView logTitle = new TextView(this);
        logTitle.setText(S.t("log_title"));
        logTitle.setTextSize(13);
        logTitle.setTextColor(GOLD);
        logHead.addView(logTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        filterBtn = smallButton(S.t("filter_errors"));
        filterBtn.setOnClickListener(v -> {
            errorsOnly = !errorsOnly;
            filterBtn.setText(errorsOnly ? S.t("filter_all") : S.t("filter_errors"));
            refreshLog();
        });
        Button copy = smallButton(S.t("copy"));
        copy.setOnClickListener(v -> copyLog());
        Button clear = smallButton(S.t("clear"));
        clear.setOnClickListener(v -> BotService.clearLog(this));

        logHead.addView(filterBtn);
        logHead.addView(copy);
        logHead.addView(clear);
        root.addView(logHead, lp(0, 0, 0, dp(4)));

        // log
        logScroll = new ScrollView(this);
        logScroll.setBackground(rounded(CARD, 0xFF333333, dp(12)));
        logScroll.setPadding(dp(10), dp(10), dp(10), dp(10));

        logText = new TextView(this);
        logText.setTextSize(12);
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
        b.setSingleLine(true);
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
