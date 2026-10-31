package com.qureshisahib;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

/** Tiny translation helper: English + Persian. */
public class S {

    public static boolean fa = true;
    private static final Map<String, String[]> M = new HashMap<>();

    private static void a(String k, String en, String faText) {
        M.put(k, new String[]{en, faText});
    }

    static {
        a("title", "♛ QURESHI SAHIB ♛", "♛ قریشی صاحب ♛");
        a("status_label", "BOT STATUS", "وضعیت ربات");
        a("running", "RUNNING", "فعال");
        a("stopped", "STOPPED", "متوقف");
        a("uptime", "Uptime", "مدت اجرا");
        a("network", "Network", "اینترنت");
        a("online", "ONLINE", "متصل");
        a("offline", "OFFLINE", "قطع");
        a("whatsapp", "WhatsApp", "واتساپ");
        a("power", "Bot Power", "روشن / خاموش ربات");
        a("connect", "Connect", "اتصال واتساپ");
        a("backup", "Backup", "پشتیبان");
        a("settings", "Settings", "تنظیمات");
        a("cmd_hint", "Type a command…", "دستور را بنویسید…");
        a("send", "Send", "ارسال");
        a("clear", "Clear", "پاک کردن");
        a("copy", "Copy", "کپی");
        a("filter_errors", "Errors only", "فقط خطاها");
        a("filter_all", "Show all", "نمایش همه");
        a("log_title", "ACTIVITY LOG", "گزارش‌ها");
        a("copied", "Log copied", "گزارش کپی شد");
        a("cmd_sent", "Command sent", "دستور ارسال شد");
        a("cmd_fail", "Control server not reachable", "سرور کنترل در دسترس نیست");
        a("connect_title", "Connect WhatsApp", "اتصال واتساپ");
        a("opt_qr", "QR code", "کد QR");
        a("opt_phone", "Phone number", "شماره تلفن");
        a("qr_wait", "Waiting for QR… turn the bot on first.", "در انتظار QR… اول ربات را روشن کنید.");
        a("qr_help", "WhatsApp > Linked devices > Link a device, then scan.",
                "واتساپ ← دستگاه‌های متصل ← اتصال دستگاه، سپس اسکن کنید.");
        a("phone_title", "Phone number", "شماره تلفن");
        a("phone_hint", "With country code, e.g. 93700000000", "با کد کشور، مثلاً 93700000000");
        a("get_code", "Get code", "دریافت کد");
        a("code_title", "Pairing code", "کد جفت‌سازی");
        a("code_help", "WhatsApp > Linked devices > Link with phone number, then type this code.",
                "واتساپ ← دستگاه‌های متصل ← اتصال با شماره تلفن، سپس این کد را وارد کنید.");
        a("code_fail", "No code received. The bot side is not ready yet.", "کدی دریافت نشد. بخش ربات هنوز آماده نیست.");
        a("phone_bad", "Invalid number", "شماره نامعتبر است");
        a("backup_ok", "Backup saved", "پشتیبان ذخیره شد");
        a("backup_fail", "Backup failed", "پشتیبان‌گیری ناموفق بود");
        a("settings_title", "Settings", "تنظیمات");
        a("pin_set", "Set / change PIN", "تعیین یا تغییر رمز");
        a("pin_remove", "Remove PIN", "حذف رمز");
        a("pin_title", "Enter PIN", "رمز را وارد کنید");
        a("pin_new", "New PIN (4+ digits)", "رمز جدید (حداقل ۴ رقم)");
        a("pin_wrong", "Wrong PIN", "رمز اشتباه است");
        a("pin_saved", "PIN saved", "رمز ذخیره شد");
        a("pin_removed", "PIN removed", "رمز حذف شد");
        a("pin_short", "At least 4 digits", "حداقل ۴ رقم");
        a("battery", "Battery settings", "تنظیمات باتری");
        a("cancel", "Cancel", "لغو");
        a("ok", "OK", "تأیید");
        a("close", "Close", "بستن");
        a("notif_denied", "Notification permission denied - status shows only in the app",
                "اجازه اعلان داده نشد - وضعیت فقط داخل برنامه دیده می‌شود");
        a("battery_fail", "Cannot open battery settings", "تنظیمات باتری باز نشد");
        a("panel_started", "Panel started", "پنل روشن شد");
        a("panel_stopped", "Panel stopped", "پنل خاموش شد");
        a("start_sent", "Start sent to bot", "فرمان شروع به ربات ارسال شد");
        a("stop_sent", "Stop sent to bot", "فرمان توقف به ربات ارسال شد");
        a("stop_fail", "Stop could not reach control server", "فرمان توقف به سرور کنترل نرسید");
        a("control_off", "Control server is off. In Termux run: cd ~/ARSLAN-BOT-NEW && node control.js",
                "سرور کنترل خاموش است. در Termux بزنید: cd ~/ARSLAN-BOT-NEW && node control.js");
        a("bot_state", "WhatsApp bot: ", "وضعیت ربات واتساپ: ");
        a("bad_response", "Bad response from control server", "پاسخ نامعتبر از سرور کنترل");
        a("alert_title", "WhatsApp bot problem", "مشکل در ربات واتساپ");
        a("alert_text", "Status: ", "وضعیت: ");
        a("st_connected", "CONNECTED", "متصل");
        a("st_disconnected", "DISCONNECTED", "قطع شد");
        a("st_starting", "STARTING", "در حال شروع");
        a("st_stopping", "STOPPING", "در حال توقف");
        a("st_stopped", "STOPPED", "متوقف");
        a("st_error", "ERROR", "خطا");
        a("st_restarting", "RESTARTING", "راه‌اندازی دوباره");
        a("st_control_offline", "CONTROL OFFLINE", "سرور کنترل خاموش");
    }

    public static void load(Context c) {
        fa = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("fa", true);
    }

    public static void save(Context c, boolean value) {
        fa = value;
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("fa", value).apply();
    }

    public static String t(String key) {
        String[] v = M.get(key);
        if (v == null) return key;
        return fa ? v[1] : v[0];
    }

    public static String status(String s) {
        String k = "st_" + s.replace(' ', '_');
        return M.containsKey(k) ? t(k) : s;
    }
}
