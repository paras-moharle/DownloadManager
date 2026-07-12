package com.downloadmanager;

import android.app.AndroidAppHelper;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {

    static final String ACTION = "com.downloadmanager.FORWARD";

    private static final Set<String> LOCKED = new HashSet<>(Arrays.asList(
            "mega.nz", "mega.co.nz", "mediafire.com", "1fichier.com", "testfile.org"
    ));

    private static final String[] WEB_MIME = {
            "text/html", "application/xhtml", "application/json", "text/javascript"
    };

    private static final Map<String, Long> seen = Collections.synchronizedMap(new HashMap<>());
    private static final long DEDUP_MS = 3000;

    // Config cache — one IPC per 10 seconds instead of per-download
    private static String[] cfgCache;
    private static long cfgTime;
    private static final long CFG_TTL = 10_000;

    // Expiry patterns
    private static final Pattern P_AMZ_EXP = Pattern.compile("[?&]X-Amz-Expires=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_AMZ_DATE = Pattern.compile("[?&]X-Amz-Date=(\\d{8}T\\d{6}Z)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_GOOG_EXP = Pattern.compile("[?&]X-Goog-Expires=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_UNIX_EXP = Pattern.compile("[?&](?:Expires|exp)=(\\d{10})(?:&|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_AZURE_SE = Pattern.compile("[?&]se=([^&]+)", Pattern.CASE_INSENSITIVE);
    private static final int EXP_THRESH = 120;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpp) {
        if (lpp.packageName == null) return;
        String p = lpp.packageName.toLowerCase(Locale.ROOT);
        if (p.contains("chrome") || p.contains("browser") || p.contains("brave") ||
                p.contains("vivaldi") || p.contains("opera") || p.contains("edge") ||
                p.contains("kiwi") || p.contains("emmx")) {
            hookBridge(lpp);
            hookController(lpp);
            hookCollection(lpp);
            XposedBridge.log("DM: hooks active in " + lpp.packageName);
        }
    }

    // ── Hook: DownloadBridge.enqueueDownload ──
    private void hookBridge(XC_LoadPackage.LoadPackageParam lpp) {
        try {
            Class<?> c = XposedHelpers.findClassIfExists(
                    "org.chromium.chrome.browser.download.DownloadBridge", lpp.classLoader);
            if (c == null) return;
            XposedBridge.hookAllMethods(c, "enqueueDownload", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length < 9) return;
                        forward(lpp, param.args[1], param.args[5], param.args[7],
                                param.args[3], param.args[8], param.args[4],
                                null, false, param);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    // ── Hook: DownloadController.onDownloadStarted ──
    private void hookController(XC_LoadPackage.LoadPackageParam lpp) {
        try {
            Class<?> c = XposedHelpers.findClassIfExists(
                    "org.chromium.chrome.browser.download.DownloadController", lpp.classLoader);
            if (c == null) return;
            XposedHelpers.findAndHookMethod(c, "onDownloadStarted",
                    "org.chromium.chrome.browser.download.DownloadItem", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                Object item = param.args[0];
                                if (item == null) return;
                                Object info = XposedHelpers.callMethod(item, "getDownloadInfo");
                                if (info == null) return;
                                forward(lpp,
                                        XposedHelpers.callMethod(info, "getUrlString"),
                                        XposedHelpers.callMethod(info, "getFileName"),
                                        XposedHelpers.callMethod(info, "getMimeType"),
                                        XposedHelpers.callMethod(info, "getReferer"),
                                        XposedHelpers.callMethod(info, "getCookie"),
                                        XposedHelpers.callMethod(info, "getUserAgent"),
                                        str(XposedHelpers.callMethod(info, "getDownloadGuid")),
                                        (boolean) XposedHelpers.callMethod(info, "isOffTheRecord"),
                                        param);
                            } catch (Throwable ignored) {}
                        }
                    });
        } catch (Throwable ignored) {}
    }

    // ── Hook: DownloadCollectionBridge.createIntermediateUriForPublish ──
    private void hookCollection(XC_LoadPackage.LoadPackageParam lpp) {
        try {
            Class<?> c = XposedHelpers.findClassIfExists(
                    "org.chromium.components.download.DownloadCollectionBridge", lpp.classLoader);
            if (c == null) return;
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("createIntermediateUriForPublish")) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args == null || param.args.length < 4) return;
                                forward(lpp, param.args[2], param.args[0], param.args[1],
                                        param.args[3], null, null, null, false, param);
                            } catch (Throwable ignored) {}
                        }
                    });
                }
            }
        } catch (Throwable ignored) {}
    }

    // ── Core: decide & forward ──
    private void forward(XC_LoadPackage.LoadPackageParam lpp,
                         Object urlO, Object fileO, Object mimeO, Object refO,
                         Object ckO, Object uaO, String guid, boolean otr,
                         XC_MethodHook.MethodHookParam param) {

        String url = str(urlO);
        if (url == null || url.isEmpty()) return;
        String lo = url.toLowerCase(Locale.ROOT);
        if (!lo.startsWith("http://") && !lo.startsWith("https://")) return;

        String mime = str(mimeO);
        if (mime != null) {
            String ml = mime.toLowerCase(Locale.ROOT);
            for (String w : WEB_MIME) if (ml.contains(w)) return;
        }

        if (dedup(url)) return;

        Context ctx = AndroidAppHelper.currentApplication();
        if (ctx == null) return;

        String[] cfg = loadConfig(ctx.getContentResolver());
        if ("browser".equals(cfg[0])) return;

        // Shield checks
        Set<String> bl = csvToSet(cfg[1]);
        Set<String> se = csvToSet(cfg[2]);
        String dom = domain(lo);

        for (String h : LOCKED) if (dom.equals(h) || dom.endsWith("." + h)) return;
        for (String h : bl) if (dom.equals(h) || dom.endsWith("." + h)) return;
        String ext = ext(lo);
        if (!ext.isEmpty() && se.contains(ext)) return;
        if (expiring(url)) return;

        // Build forward intent (custom action — no hardcoded package dependency)
        Intent intent = new Intent(ACTION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.putExtra("url", url);
        String fn = str(fileO);
        if (ok(fn)) intent.putExtra("filename", fn);
        if (ok(mime)) intent.putExtra("mime", mime);
        String ref = str(refO);
        if (ok(ref)) intent.putExtra("referer", ref);
        String ck = str(ckO);
        if (ok(ck)) intent.putExtra("cookies", ck);
        String ua = str(uaO);
        intent.putExtra("user_agent", ok(ua) ? ua : System.getProperty("http.agent"));

        // CRITICAL: launch FIRST, kill Chrome download ONLY on success
        boolean launched = false;
        try {
            ctx.startActivity(intent);
            launched = true;
        } catch (Exception e1) {
            // Action intent failed — try explicit component as fallback
            try {
                intent.setClassName("com.downloadmanager", "com.downloadmanager.ForwardActivity");
                ctx.startActivity(intent);
                launched = true;
            } catch (Exception e2) {
                XposedBridge.log("DM: forward failed: " + e2.getMessage());
            }
        }

        if (launched) {
            if (param != null) param.setResult(null);
            if (guid != null) cancelChrome(lpp.classLoader, guid, otr);
            toast(ctx, "🚀 Forwarding download...");
        } else {
            // Let Chrome download normally — don't kill anything
            toast(ctx, "⚠️ DM unavailable — browser will download");
        }
    }

    // ── Config: single CP query, cached 10s ──
    private String[] loadConfig(ContentResolver r) {
        long now = System.currentTimeMillis();
        if (cfgCache != null && now - cfgTime < CFG_TTL) return cfgCache;

        String mode = "external", bl = "", se = "";
        try {
            Cursor c = r.query(Uri.parse("content://com.downloadmanager.mode/config"),
                    null, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        int mi = c.getColumnIndex("mode");
                        int bi = c.getColumnIndex("blacklist");
                        int ei = c.getColumnIndex("skipped_ext");
                        if (mi >= 0 && c.getString(mi) != null) mode = c.getString(mi);
                        if (bi >= 0 && c.getString(bi) != null) bl = c.getString(bi);
                        if (ei >= 0 && c.getString(ei) != null) se = c.getString(ei);
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Throwable ignored) {}

        cfgCache = new String[]{mode, bl, se};
        cfgTime = now;
        return cfgCache;
    }

    // ── Expiry detection ──
    private boolean expiring(String url) {
        try {
            Matcher m1 = P_AMZ_EXP.matcher(url);
            if (m1.find()) {
                int life = Integer.parseInt(m1.group(1));
                if (life <= 30) return true;
                Matcher m2 = P_AMZ_DATE.matcher(url);
                if (m2.find()) {
                    SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
                    f.setTimeZone(TimeZone.getTimeZone("UTC"));
                    long rem = life - (System.currentTimeMillis() - f.parse(m2.group(1)).getTime()) / 1000;
                    return rem >= 0 && rem <= EXP_THRESH;
                }
                return life <= EXP_THRESH;
            }
            Matcher g = P_GOOG_EXP.matcher(url);
            if (g.find()) return Integer.parseInt(g.group(1)) <= EXP_THRESH;
            Matcher u = P_UNIX_EXP.matcher(url);
            if (u.find()) {
                long rem = Long.parseLong(u.group(1)) - System.currentTimeMillis() / 1000;
                return rem >= 0 && rem <= EXP_THRESH;
            }
            Matcher a = P_AZURE_SE.matcher(url);
            if (a.find()) {
                Date dt = parseIso(URLDecoder.decode(a.group(1), "UTF-8"));
                if (dt != null) {
                    long rem = (dt.getTime() - System.currentTimeMillis()) / 1000;
                    return rem >= 0 && rem <= EXP_THRESH;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ── Dedup: skip same URL within 3s ──
    private boolean dedup(String url) {
        long now = System.currentTimeMillis();
        synchronized (seen) { seen.entrySet().removeIf(e -> now - e.getValue() > 10_000); }
        Long last = seen.get(url);
        if (last != null && now - last < DEDUP_MS) return true;
        seen.put(url, now);
        return false;
    }

    // ── Cancel Chrome's internal download ──
    private void cancelChrome(ClassLoader cl, String guid, boolean otr) {
        try {
            Class<?> c = XposedHelpers.findClassIfExists(
                    "org.chromium.chrome.browser.download.DownloadManagerService", cl);
            if (c == null) return;
            Object svc = XposedHelpers.callStaticMethod(c, "getDownloadManagerService");
            if (svc != null) XposedHelpers.callMethod(svc, "cancelDownload", guid, otr);
        } catch (Throwable ignored) {}
    }

    // ── Utilities ──
    private Date parseIso(String s) {
        String[] pats = {"yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd"};
        for (String p : pats) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                return f.parse(s);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String domain(String url) {
        try {
            String r = url.substring(url.indexOf("://") + 3);
            int i = r.indexOf('/');
            String h = i > 0 ? r.substring(0, i) : r;
            int p = h.lastIndexOf(':');
            return p > 0 ? h.substring(0, p) : h;
        } catch (Exception e) { return ""; }
    }

    private String ext(String url) {
        try {
            int q = url.indexOf('?');
            String p = q > 0 ? url.substring(0, q) : url;
            int h = p.indexOf('#');
            if (h > 0) p = p.substring(0, h);
            int sl = p.lastIndexOf('/');
            String fn = sl >= 0 ? p.substring(sl + 1) : p;
            int d = fn.lastIndexOf('.');
            return (d > 0 && d < fn.length() - 1) ? fn.substring(d + 1) : "";
        } catch (Exception e) { return ""; }
    }

    private Set<String> csvToSet(String csv) {
        Set<String> s = new HashSet<>();
        if (csv == null || csv.isEmpty()) return s;
        for (String p : csv.split(",")) {
            String t = p.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) s.add(t);
        }
        return s;
    }

    private void toast(Context c, String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(c, msg, Toast.LENGTH_SHORT).show());
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }
    private boolean ok(String s) { return s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s); }
}
