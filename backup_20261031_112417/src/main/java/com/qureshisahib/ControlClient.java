package com.qureshisahib;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Talks to control.js running in Termux (127.0.0.1:8787). Returns null on failure. */
public class ControlClient {

    private static final String BASE = "http://127.0.0.1:8787";
    private static final String TOKEN = "QURESHI-LOCAL-2026";

    public static String get(String path) {
        return request("GET", path);
    }

    public static String post(String path) {
        return request("POST", path);
    }

    private static String request(String method, String path) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(BASE + path).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(2000);
            c.setReadTimeout(3000);
            if ("POST".equals(method)) {
                c.setRequestProperty("x-qureshi-token", TOKEN);
                c.setDoOutput(true);
                c.getOutputStream().close();
            }
            int code = c.getResponseCode();
            if (code >= 400) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
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
