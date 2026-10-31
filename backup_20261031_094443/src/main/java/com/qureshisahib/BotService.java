package com.qureshisahib;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BotService extends Service {

    public static final String ACTION_START = "com.qureshisahib.START";
    public static final String ACTION_STOP = "com.qureshisahib.STOP";
    public static final String ACTION_LOG = "com.qureshisahib.LOG";
    public static final String ACTION_STATE = "com.qureshisahib.STATE";

    private static final String CHANNEL_ID = "bot_channel";
    private static final int NOTIF_ID = 1001;
    private static final int INTERVAL_MS = 5000;
    private static final int MAX_LOG_CHARS = 20000;

    public static volatile boolean running = false;
    public static volatile long startTime = 0;
    public static volatile int ticks = 0;

    private static final StringBuilder LOG = new StringBuilder();

    private Thread worker;
    private PowerManager.WakeLock wakeLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopBot();
            stopSelf();
            return START_NOT_STICKY;
        }

        startBot();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (running) stopBot();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startBot() {
        createChannel();

        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIF_ID, n);
        }

        if (running) return;

        running = true;
        startTime = System.currentTimeMillis();
        ticks = 0;

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "qureshisahib:bot");
        wakeLock.acquire();

        log(this, "Bot started");
        sendState(this);

        worker = new Thread(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    ticks++;
                    runBotTask();
                    updateNotification();
                    Thread.sleep(INTERVAL_MS);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    log(this, "Error: " + e.getMessage());
                }
            }
        }, "bot-worker");
        worker.start();
    }

    private void stopBot() {
        boolean wasRunning = running;
        running = false;

        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;

        stopForeground(STOP_FOREGROUND_REMOVE);

        if (wasRunning) {
            log(this, "Bot stopped (uptime " + formatUptime(System.currentTimeMillis() - startTime) + ")");
        }
        sendState(this);
    }

    /** SIMULATION ONLY: put the real bot work here. */
    private void runBotTask() {
        if (ticks == 1 || ticks % 6 == 0) {
            log(this, "Tick #" + ticks + " | network: " + (isOnline(this) ? "ONLINE" : "OFFLINE"));
        }
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Bot status", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Shows while the bot is running");
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

        PendingIntent openPi = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class), flags);

        PendingIntent stopPi = PendingIntent.getService(
                this, 1, new Intent(this, BotService.class).setAction(ACTION_STOP), flags);

        String text = running
                ? "Running • " + formatUptime(System.currentTimeMillis() - startTime) + " • ticks " + ticks
                : "Starting…";

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("♛ QURESHI SAHIB")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(openPi)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification() {
        getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification());
    }

    public static boolean isOnline(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public static String formatUptime(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    public static void log(Context c, String msg) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        synchronized (LOG) {
            LOG.append('[').append(time).append("] ").append(msg).append('\n');
            if (LOG.length() > MAX_LOG_CHARS) {
                LOG.delete(0, LOG.length() - MAX_LOG_CHARS);
            }
        }
        c.sendBroadcast(new Intent(ACTION_LOG).setPackage(c.getPackageName()));
    }

    public static String getLog() {
        synchronized (LOG) {
            return LOG.toString();
        }
    }

    public static void clearLog(Context c) {
        synchronized (LOG) {
            LOG.setLength(0);
        }
        c.sendBroadcast(new Intent(ACTION_LOG).setPackage(c.getPackageName()));
    }

    private static void sendState(Context c) {
        c.sendBroadcast(new Intent(ACTION_STATE).setPackage(c.getPackageName()));
    }
}
