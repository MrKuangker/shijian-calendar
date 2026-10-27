package com.mrkuangker.shijian;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** GitHub release metadata + SHA-256 + APK package/version/signature verification. */
final class UpdateManager {
    private static final long MAX_APK = 100L * 1024 * 1024;
    private final MainActivity activity;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile boolean ready;
    private volatile JSONObject manifest;
    UpdateManager(MainActivity activity) { this.activity = activity; }

    private void status(String state, String message) {
        try { JSONObject o = new JSONObject(); o.put("state", state); if (message != null) o.put("message", message); activity.emit("updateStatus", o); }
        catch (Exception ignored) { }
    }
    void check() {
        if (!busy.compareAndSet(false, true)) return;
        status("checking", null);
        worker.execute(() -> {
            try {
                JSONObject release = readJson("https://api.github.com/repos/" + BuildConfig.GITHUB_REPO + "/releases/latest");
                JSONArray assets = release.getJSONArray("assets");
                String manifestUrl = null, apkUrl = null;
                for (int i=0; i<assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    if ("update.json".equals(asset.optString("name"))) manifestUrl = asset.getString("browser_download_url");
                    if ("shijian.apk".equals(asset.optString("name"))) apkUrl = asset.getString("browser_download_url");
                }
                if (manifestUrl == null || apkUrl == null) throw new IllegalStateException("最新版本尚未提供完整安装包，请稍后重试");
                validateAsset(manifestUrl); validateAsset(apkUrl);
                JSONObject next = readJson(manifestUrl);
                if (!activity.getPackageName().equals(next.optString("applicationId"))) throw new IllegalStateException("更新包信息不匹配");
                if (next.getInt("versionCode") <= BuildConfig.VERSION_CODE) {
                    JSONObject response = new JSONObject(); response.put("state", "latest"); response.put("versionName", BuildConfig.VERSION_NAME); activity.emit("updateStatus", response);
                    manifest = null;
                } else {
                    if (!next.optString("sha256").matches("[a-fA-F0-9]{64}")) throw new IllegalStateException("更新包缺少完整性校验");
                    if (next.optLong("size") <= 0 || next.optLong("size") > MAX_APK) throw new IllegalStateException("更新包大小无效");
                    next.put("url", apkUrl); manifest = next; ready = false;
                    JSONObject response = new JSONObject(); response.put("state", "available"); response.put("versionName", next.getString("versionName")); response.put("notes", release.optString("body", "体验改进与问题修复")); activity.emit("updateStatus", response);
                }
            } catch (Exception error) { status("error", friendly(error)); }
            finally { busy.set(false); }
        });
    }
    void download() {
        if (!busy.compareAndSet(false, true)) return;
        final JSONObject target = manifest;
        worker.execute(() -> {
            File partial = new File(activity.getCacheDir(), "update-part.apk");
            try {
                ready = false;
                if (target == null) throw new IllegalStateException("请先检查更新");
                String address = target.getString("url"); validateAsset(address);
                HttpURLConnection connection = connect(address);
                try {
                    int code = connection.getResponseCode();
                    if (code != 200) throw new IllegalStateException("安装包下载失败，请稍后重试（" + code + "）");
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    long total = target.getLong("size"), read = 0;
                    int lastPercent = -1;
                    try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(partial)) {
                        byte[] buffer = new byte[32768]; int n;
                        while ((n = input.read(buffer)) != -1) {
                            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                            read += n;
                            if (read > MAX_APK || read > total) throw new IllegalStateException("安装包大小校验失败");
                            output.write(buffer, 0, n); digest.update(buffer, 0, n);
                            int percent = (int) (read * 100 / total);
                            if (percent != lastPercent) { lastPercent = percent; JSONObject response = new JSONObject(); response.put("state", "downloading"); response.put("progress", percent); activity.emit("updateStatus", response); }
                        }
                        output.getFD().sync();
                    }
                    if (read != total || !hex(digest.digest()).equalsIgnoreCase(target.getString("sha256"))) throw new IllegalStateException("安装包校验失败，请重新下载");
                    verifyApk(partial, target.getInt("versionCode"));
                    File apk = UpdateProvider.file(activity);
                    if (!partial.renameTo(apk)) throw new IllegalStateException("无法保存更新包，请检查手机剩余空间");
                    ready = true; status("ready", null);
                } finally { connection.disconnect(); }
            } catch (Exception error) { partial.delete(); status("error", friendly(error)); }
            finally { busy.set(false); }
        });
    }
    boolean isReady() { return ready && UpdateProvider.file(activity).isFile(); }
    void close() { worker.shutdownNow(); }

    private void verifyApk(File file, int expectedCode) throws Exception {
        PackageManager pm = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo incoming = pm.getPackageArchiveInfo(file.getPath(), flags), installed = pm.getPackageInfo(activity.getPackageName(), flags);
        if (incoming == null || !activity.getPackageName().equals(incoming.packageName)) throw new IllegalStateException("安装包不属于时笺");
        long code = Build.VERSION.SDK_INT >= 28 ? incoming.getLongVersionCode() : incoming.versionCode;
        if (code != expectedCode || code <= BuildConfig.VERSION_CODE) throw new IllegalStateException("安装包版本不匹配");
        Signature[] next = Build.VERSION.SDK_INT >= 28 ? incoming.signingInfo.getApkContentsSigners() : incoming.signatures;
        Signature[] current = Build.VERSION.SDK_INT >= 28 ? installed.signingInfo.getApkContentsSigners() : installed.signatures;
        if (next == null || current == null || !Arrays.equals(next, current)) throw new IllegalStateException("安装包签名不匹配，已停止安装");
    }
    private static JSONObject readJson(String address) throws Exception {
        HttpURLConnection connection = connect(address);
        try {
            int code = connection.getResponseCode();
            if (code == 404) throw new IllegalStateException("暂时没有可用的发布版本，请稍后再试");
            if (code == 403 || code == 429) throw new IllegalStateException("GitHub 请求次数已达限制，请稍后再试");
            if (code != 200) throw new IllegalStateException("GitHub 暂时不可用（" + code + "）");
            try (InputStream input = connection.getInputStream()) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
                while ((n = input.read(buffer)) != -1) { if (output.size()+n>1024*1024) throw new IllegalStateException("更新信息过大"); output.write(buffer,0,n); }
                return new JSONObject(new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }
    private static HttpURLConnection connect(String address) throws Exception {
        // Every redirect must remain HTTPS. Signed release-asset redirects use GitHub's CDN.
        for (int i=0; i<8; i++) {
            URL url = new URL(address);
            if (!"https".equals(url.getProtocol())) throw new IllegalStateException("更新地址不安全");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(20000); connection.setReadTimeout(30000); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Shijian/" + BuildConfig.VERSION_NAME);
            connection.setRequestProperty("Accept", "application/octet-stream, application/json");
            int code = connection.getResponseCode();
            if (code >= 300 && code <= 399) {
                String location = connection.getHeaderField("Location"); connection.disconnect();
                if (location == null) throw new IllegalStateException("更新地址重定向无效");
                address = new URL(url, location).toString();
            } else return connection;
        }
        throw new IllegalStateException("更新地址重定向次数过多");
    }
    private static void validateAsset(String address) {
        if (!address.startsWith("https://github.com/" + BuildConfig.GITHUB_REPO + "/releases/download/")) throw new IllegalArgumentException("更新来源与项目仓库不匹配");
    }
    private static String hex(byte[] bytes) { StringBuilder result = new StringBuilder(); for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b & 255)); return result.toString(); }
    private static String friendly(Exception error) {
        if (error instanceof java.net.SocketTimeoutException) return "连接超时，请检查网络后重试";
        if (error instanceof java.io.IOException) return "连接失败，请检查手机能否访问 GitHub 后重试";
        return error.getMessage() != null ? error.getMessage() : "更新未能完成，请稍后重试";
    }
}
