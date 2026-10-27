package com.mrkuangker.shijian;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int EXPORT = 41, IMPORT = 42, NOTIFICATIONS = 43;
    private WebView web;
    private UpdateManager updates;
    private boolean pendingInstall;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(false);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        web.setBackgroundColor(Color.rgb(246,247,242));
        // Only this embedded, offline origin can reach the native bridge.
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"https".equals(uri.getScheme()) || !"app.shijian.local".equals(uri.getHost())) return blocked();
                String path = uri.getPath();
                if (path == null || path.contains("..")) return blocked();
                if ("/".equals(path)) path = "/index.html";
                try {
                    String mime = path.endsWith(".css") ? "text/css" : path.endsWith(".js") ? "application/javascript" : path.endsWith(".svg") ? "image/svg+xml" : "text/html";
                    return new WebResourceResponse(mime, "UTF-8", getAssets().open(path.substring(1)));
                } catch (Exception error) { return blocked(); }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public void onPageFinished(WebView view, String url) { openNotificationEvent(); }
            private WebResourceResponse blocked() { return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", null, new java.io.ByteArrayInputStream(new byte[0])); }
        });
        updates = new UpdateManager(this);
        web.addJavascriptInterface(new Bridge(), "ShijianAndroid");
        setContentView(web);
        web.loadUrl("https://app.shijian.local/index.html");
        io.execute(() -> ReminderScheduler.reschedule(this));
    }

    void emit(String method, JSONObject payload) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            web.evaluateJavascript("window.ShijianApp && window.ShijianApp." + method + "(" + payload.toString() + ");", null);
        });
    }
    void notifyUser(String message) {
        runOnUiThread(() -> { if (!isFinishing()) Toast.makeText(this, message, Toast.LENGTH_LONG).show(); });
    }

    private final class Bridge {
        @JavascriptInterface public String loadData() {
            try { return DataStore.read(MainActivity.this); }
            catch (Exception error) { return "{\"error\":\"本机数据读取失败\"}"; }
        }
        @JavascriptInterface public String saveData(String raw) {
            try {
                DataStore.write(MainActivity.this, raw);
                io.execute(() -> ReminderScheduler.reschedule(MainActivity.this));
                return "{\"ok\":true}";
            } catch (Exception error) {
                JSONObject result = new JSONObject();
                try { result.put("ok", false); result.put("error", error.getMessage()); } catch (Exception ignored) { }
                return result.toString();
            }
        }
        @JavascriptInterface public String appInfo() {
            JSONObject result = new JSONObject();
            try { result.put("versionName", BuildConfig.VERSION_NAME); result.put("versionCode", BuildConfig.VERSION_CODE); result.put("repository", BuildConfig.GITHUB_REPO); result.put("systemDark", (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES); }
            catch (Exception ignored) { }
            return result.toString();
        }
        @JavascriptInterface public void setTheme(boolean dark) {
            runOnUiThread(() -> {
                int color = Color.parseColor(dark ? "#171e1b" : "#f6f7f2");
                getWindow().setStatusBarColor(color); getWindow().setNavigationBarColor(color); web.setBackgroundColor(color);
                getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            });
        }
        @JavascriptInterface public void exportData() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "时笺备份-" + new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()) + ".json");
                try { startActivityForResult(intent, EXPORT); } catch (Exception error) { notifyUser("未找到文件保存工具"); }
            });
        }
        @JavascriptInterface public void importData() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
                try { startActivityForResult(intent, IMPORT); } catch (Exception error) { notifyUser("未找到文件选择工具"); }
            });
        }
        @JavascriptInterface public void checkUpdate() { updates.check(); }
        @JavascriptInterface public void downloadUpdate() { updates.download(); }
        @JavascriptInterface public void installUpdate() { runOnUiThread(() -> launchInstaller()); }
        @JavascriptInterface public void openRepository() {
            runOnUiThread(() -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/" + BuildConfig.GITHUB_REPO))); }
                catch (Exception error) { notifyUser("未找到浏览器"); }
            });
        }
        @JavascriptInterface public void requestNotifications() { runOnUiThread(() -> askNotifications()); }
        @JavascriptInterface public void notificationSettings() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !getPreferences(0).getBoolean("asked-notification", false)) { askNotifications(); return; }
                try { startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())); }
                catch (Exception error) { notifyUser("请在系统设置中打开时笺的通知权限"); }
            });
        }
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            getPreferences(0).edit().putBoolean("asked-notification", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATIONS);
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == NOTIFICATIONS && (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED)) notifyUser("未开启通知权限，提醒暂时无法显示；日程已保存");
        else if (requestCode == NOTIFICATIONS) io.execute(() -> ReminderScheduler.reschedule(this));
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent result) {
        super.onActivityResult(requestCode, resultCode, result);
        if (resultCode != RESULT_OK || result == null || result.getData() == null) return;
        Uri uri = result.getData();
        io.execute(() -> {
            try {
                if (requestCode == EXPORT) {
                    String raw = DataStore.read(this);
                    if (raw == null) raw = "{\"schemaVersion\":1,\"events\":[],\"preferences\":{}}";
                    try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                        if (output == null) throw new IllegalStateException();
                        output.write(raw.getBytes(StandardCharsets.UTF_8));
                    }
                    notifyUser("备份已保存，请妥善保管");
                } else if (requestCode == IMPORT) {
                    String raw;
                    try (InputStream input = getContentResolver().openInputStream(uri)) {
                        if (input == null) throw new IllegalStateException();
                        raw = DataStore.readLimited(input);
                    }
                    final String content = raw;
                    runOnUiThread(() -> web.evaluateJavascript("window.ShijianApp.importData(" + JSONObject.quote(content) + ");", null));
                }
            } catch (Exception error) { notifyUser("文件操作失败，请检查保存位置和文件格式"); }
        });
    }

    private void launchInstaller() {
        if (!updates.isReady()) { notifyUser("请先下载并校验新版本"); return; }
        if (!getPackageManager().canRequestPackageInstalls()) {
            pendingInstall = true;
            try { startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); }
            catch (Exception error) { pendingInstall = false; notifyUser("请在系统设置中允许时笺安装应用更新"); }
            return;
        }
        pendingInstall = false;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(UpdateProvider.URI, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception error) { notifyUser("系统安装器未能打开，请稍后重试"); }
    }
    @Override protected void onResume() {
        super.onResume();
        if (pendingInstall && getPackageManager().canRequestPackageInstalls()) launchInstaller();
    }
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        web.evaluateJavascript("window.ShijianApp && window.ShijianApp.refreshTheme();", null);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); openNotificationEvent();
    }
    private void openNotificationEvent() {
        String id = getIntent().getStringExtra("eventId");
        if (id == null) return;
        getIntent().removeExtra("eventId");
        web.evaluateJavascript("window.ShijianApp && window.ShijianApp.openEvent(" + JSONObject.quote(id) + ");", null);
    }
    @Override public void onBackPressed() {
        web.evaluateJavascript("window.ShijianApp ? window.ShijianApp.onBack() : false", value -> { if (!"true".equals(value)) super.onBackPressed(); });
    }
    @Override protected void onDestroy() {
        updates.close(); io.shutdown();
        web.removeJavascriptInterface("ShijianAndroid"); web.destroy();
        super.onDestroy();
    }
}
