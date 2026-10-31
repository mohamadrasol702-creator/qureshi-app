package com.qureshisahib;
import android.content.Context;
import android.content.SharedPreferences;
public class FeatureManager {
    public static class Feature {
        public final String id, fa, group; public final int min;
        Feature(String id, String fa, String group, int min) { this.id = id; this.fa = fa; this.group = group; this.min = min; }
    }
    public static final Feature[] ALL = {
        new Feature("antilink",  "ضد لینک",           "گروه",    0),
        new Feature("welcome",   "خوش‌آمدگویی",        "گروه",    0),
        new Feature("antispam",  "ضد اسپم",           "گروه",    1),
        new Feature("antiswear", "ضد فحش",            "گروه",    1),
        new Feature("shop",      "فروشگاه",           "فروشگاه", 0),
        new Feature("orders",    "سفارش‌گیری خودکار",  "فروشگاه", 1),
        new Feature("ai",        "هوش مصنوعی",        "AI",      0),
        new Feature("analysis",  "تحلیل هوشمند",      "AI",      2),
        new Feature("chatbot",   "چت‌بات مشتری",       "ربات",    1),
        new Feature("automation","اتوماسیون",         "ربات",    1),
        new Feature("broadcast", "پیام همگانی",       "ربات",    2),
        new Feature("plugins",   "پلاگین‌ها",          "ابزارها", 1),
        new Feature("codestudio","Code Studio",       "ابزارها", 1),
        new Feature("backup",    "بکاپ خودکار",       "ابزارها", 1),
        new Feature("reports",   "گزارش‌ها",           "ابزارها", 1),
    };
    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("features", 0); }
    public static boolean master(Context c) { return sp(c).getBoolean("master", true); }
    public static void setMaster(Context c, boolean v) { sp(c).edit().putBoolean("master", v).apply(); }
    public static boolean userOn(Context c, String id) { return sp(c).getBoolean(id, true); }
    public static void setUser(Context c, String id, boolean v) { sp(c).edit().putBoolean(id, v).apply(); }
    public static boolean active(Context c, Feature f, int planLevel) {
        return planLevel >= f.min && master(c) && userOn(c, f.id);
    }
    public static String getPlan(Context c) { return c.getSharedPreferences("plan", 0).getString("id", null); }
    public static void setPlan(Context c, String id) { c.getSharedPreferences("plan", 0).edit().putString("id", id).apply(); }
}
