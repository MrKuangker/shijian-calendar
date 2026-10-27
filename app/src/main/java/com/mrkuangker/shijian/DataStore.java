package com.mrkuangker.shijian;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Atomic writes protect existing records during process death or a failed write. */
final class DataStore {
    static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final Object LOCK = new Object();
    private static AtomicFile file(Context context) { return new AtomicFile(new File(context.getFilesDir(), "shijian-data.json")); }

    static String read(Context context) throws Exception {
        synchronized (LOCK) {
            AtomicFile target = file(context);
            if (!target.getBaseFile().exists() && !new File(target.getBaseFile().getPath() + ".bak").exists()) return null;
            try (InputStream input = target.openRead()) { return readLimited(input); }
        }
    }

    static String readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = input.read(buffer)) != -1) {
            if (output.size() + length > MAX_BYTES) throw new IllegalArgumentException("文件过大，请使用 16 MB 以内的备份");
            output.write(buffer, 0, length);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    static void write(Context context, String raw) throws Exception {
        byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("数据容量超出上限，请先导出备份");
        JSONObject root = new JSONObject(raw);
        if (root.optInt("schemaVersion") != 1 || root.optJSONArray("events") == null) throw new IllegalArgumentException("数据格式无效");
        synchronized (LOCK) {
            AtomicFile target = file(context);
            FileOutputStream stream = null;
            try { stream = target.startWrite(); stream.write(bytes); target.finishWrite(stream); }
            catch (Exception error) { if (stream != null) target.failWrite(stream); throw error; }
        }
    }
}
