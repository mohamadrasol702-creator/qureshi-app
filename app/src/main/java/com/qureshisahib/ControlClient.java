package com.qureshisahib;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Talks to control.js running in Termux (127.0.0.1:8787). Returns null on failure. */
public class ControlClient {

    private static final String BASE = "http://127.0.0.1:8787";
    private static final String TOKEN = "QURESHI-LOCAL-2026";

    public static String get(String path) {
        return request("GET", path, null);
    }

    public static String post(String path) {
        return request("POST", path, "");
    }

    public static String post(String path, String body) {
        return request("POST", path, body);
    }

    private static String request(String method, String path, String body) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(BASE + path).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(2000);
            c.setReadTimeout(6000);
            c.setRequestProperty("x-qureshi-token", TOKEN);
            if ("POST".equals(method)) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                OutputStream o = c.getOutputStream();
                if (body != null) o.write(body.getBytes("UTF-8"));
                o.close();
            }
            int code = c.getResponseCode();
            if (code >= 400) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
