package nl.gtaac.governor;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String FEED = "https://raw.githubusercontent.com/miltonminecraft-afk/Gta-ac-list/main/data/status.json";
    private static final String PROJECT = "https://github.com/miltonminecraft-afk/Gta-ac-list";
    private static final int LIMIT = 1024 * 1024;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private WebView web;
    private SharedPreferences cache;
    private boolean ready, fetching, resumed, destroyed;
    private long lastAttempt;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (resumed && ready) refresh();
            if (resumed) ui.postDelayed(this, 5 * 60 * 1000L);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        cache = getSharedPreferences("progress", MODE_PRIVATE);
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(13, 16, 21));
        getWindow().setStatusBarColor(Color.rgb(13, 16, 21));
        getWindow().setNavigationBarColor(Color.rgb(13, 16, 21));
        web.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("governor".equals(u.getScheme())) {
                    if ("refresh".equals(u.getHost())) refresh();
                    if ("github".equals(u.getHost())) {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT))); }
                        catch (android.content.ActivityNotFoundException ignored) { }
                    }
                }
                // No remote page can replace the trusted, bundled application.
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", null,
                    new ByteArrayInputStream(new byte[0]));
            }
            @Override public void onPageFinished(WebView view, String url) {
                ready = true;
                String payload = cache.getString("payload", "");
                String mode = "cached";
                if (payload.isEmpty()) {
                    try { payload = asset("snapshot.json"); mode = "bundled"; }
                    catch (Exception ignored) { payload = ""; }
                }
                if (!payload.isEmpty()) {
                    try { validate(payload); deliver(payload, mode); }
                    catch (Exception ignored) { status("error"); }
                }
                refresh();
            }
        });
        setContentView(web);
        try { web.loadDataWithBaseURL("https://governor.invalid/", asset("index.html"), "text/html", "UTF-8", null); }
        catch (Exception e) { web.loadData("Kan de app niet openen.", "text/plain", "UTF-8"); }
    }

    private String asset(String name) throws Exception {
        try (InputStream in = getAssets().open(name)) { return read(in); }
    }
    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int count;
        while ((count = in.read(buffer)) != -1) {
            if (bytes.size() + count > LIMIT) throw new IllegalArgumentException("Response too large");
            bytes.write(buffer, 0, count);
        }
        return bytes.toString(StandardCharsets.UTF_8.name());
    }
    static void validate(String raw) throws Exception {
        JSONObject data = new JSONObject(raw);
        if (data.getInt("schema") != 1) throw new IllegalArgumentException("Schema");
        java.time.OffsetDateTime.parse(data.getString("updatedAt"));
        JSONArray tasks = data.getJSONArray("tasks");
        if (tasks.length() == 0 || tasks.length() > 200) throw new IllegalArgumentException("Tasks");
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject t = tasks.getJSONObject(i);
            if (!ids.add(t.getString("id"))) throw new IllegalArgumentException("Duplicate task");
            t.getString("title"); t.getString("owner"); t.getString("current"); t.getString("next");
            if (!java.util.Arrays.asList("active","prepared","queued","waiting","delivered").contains(t.getString("status"))) throw new IllegalArgumentException("Status");
            JSONArray checks = t.getJSONArray("checks");
            if (checks.length() < 1 || checks.length() > 100) throw new IllegalArgumentException("Checks");
            for (int j = 0; j < checks.length(); j++) {
                JSONObject check = checks.getJSONObject(j); check.getString("label");
                if (!java.util.Arrays.asList("done","doing","open").contains(check.getString("state"))) throw new IllegalArgumentException("Check state");
                if ("delivered".equals(t.getString("status")) && !"done".equals(check.getString("state"))) throw new IllegalArgumentException("Premature delivery");
            }
        }
        data.getJSONArray("updates"); data.getJSONArray("backlog"); data.getJSONArray("history");
    }
    private void refresh() {
        if (!ready || destroyed || fetching) return;
        long now = System.currentTimeMillis();
        if (now - lastAttempt < 3000) return;
        lastAttempt = now; fetching = true; status("loading");
        network.execute(() -> {
            String result = null;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(FEED + "?check=" + System.currentTimeMillis()).openConnection();
                conn.setConnectTimeout(12000); conn.setReadTimeout(12000);
                conn.setUseCaches(false); conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "GtaAcGovernor/1.0");
                if (conn.getResponseCode() != 200) throw new IllegalStateException("HTTP " + conn.getResponseCode());
                try (InputStream in = conn.getInputStream()) { result = read(in); }
                validate(result);
                String old = cache.getString("payload", "");
                if (!old.isEmpty()) {
                    java.time.Instant previous = java.time.OffsetDateTime.parse(new JSONObject(old).getString("updatedAt")).toInstant();
                    java.time.Instant incoming = java.time.OffsetDateTime.parse(new JSONObject(result).getString("updatedAt")).toInstant();
                    if (incoming.isBefore(previous)) throw new IllegalStateException("Older snapshot");
                }
                cache.edit().putString("payload", result).apply();
            } catch (Exception ignored) { result = null; }
            finally { if (conn != null) conn.disconnect(); }
            final String payload = result;
            ui.post(() -> {
                fetching = false;
                if (destroyed) return;
                if (payload != null) deliver(payload, "online"); else status("error");
            });
        });
    }
    private void deliver(String data, String mode) {
        if (!destroyed) web.evaluateJavascript("window.Governor.receive(" + JSONObject.quote(data) + "," + JSONObject.quote(mode) + ");", null);
    }
    private void status(String state) {
        if (ready && !destroyed) web.evaluateJavascript("window.Governor.connection(" + JSONObject.quote(state) + ");", null);
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        if (ready) refresh();
        ui.removeCallbacks(ticker); ui.postDelayed(ticker, 5 * 60 * 1000L);
    }
    @Override protected void onPause() { resumed = false; ui.removeCallbacks(ticker); super.onPause(); }
    @Override protected void onDestroy() {
        destroyed = true; ui.removeCallbacksAndMessages(null); network.shutdownNow();
        web.destroy(); super.onDestroy();
    }
}
