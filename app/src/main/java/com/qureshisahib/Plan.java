package com.qureshisahib;
import android.graphics.Color;
public class Plan {
    public final String id, title; public final double price; public final int level, color;
    public Plan(String id, String title, double price, int level, String color) {
        this.id = id; this.title = title; this.price = price; this.level = level; this.color = Color.parseColor(color);
    }
    public static final Plan PRO   = new Plan("pro",   "پرو",    2.99, 2, "#E53935");
    public static final Plan BASIC = new Plan("basic", "ساده",   1.99, 1, "#1E88E5");
    public static final Plan FREE  = new Plan("free",  "رایگان", 0.0,  0, "#757575");
    public static final Plan[] ALL = { PRO, BASIC, FREE };
    public static Plan byId(String id) { for (Plan p : ALL) if (p.id.equals(id)) return p; return FREE; }
}
