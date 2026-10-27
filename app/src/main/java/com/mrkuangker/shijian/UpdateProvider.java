package com.mrkuangker.shijian;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** A read-only grant for the single verified APK; no other app files are exposed. */
public final class UpdateProvider extends ContentProvider {
    static final Uri URI = Uri.parse("content://com.mrkuangker.shijian.updates/update.apk");
    static File file(Context context) { return new File(context.getCacheDir(), "update.apk"); }
    @Override public boolean onCreate() { return true; }
    private void validate(Uri uri) { if (!URI.equals(uri)) throw new SecurityException("Unknown URI"); }
    @Override public String getType(Uri uri) { validate(uri); return "application/vnd.android.package-archive"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        validate(uri); if (!"r".equals(mode)) throw new SecurityException("Read-only provider");
        return ParcelFileDescriptor.open(file(getContext()), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        validate(uri);
        String[] columns = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns); Object[] values = new Object[columns.length];
        for (int i=0;i<columns.length;i++) { if(OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i]="shijian.apk"; else if(OpenableColumns.SIZE.equals(columns[i])) values[i]=file(getContext()).length(); }
        cursor.addRow(values); return cursor;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
