package com.mrkuangker.shijian;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        PendingResult result = goAsync();
        new Thread(() -> {
            try {
                String raw = DataStore.read(context), id = intent.getStringExtra("eventId");
                if (raw == null || id == null) return;
                JSONArray events = new JSONObject(raw).getJSONArray("events");
                for (int i=0; i<events.length(); i++) {
                    JSONObject event=events.getJSONObject(i);
                    if (!id.equals(event.optString("id")) || event.optBoolean("completed") || event.optString("reminderAt").isEmpty()) continue;
                    String title=event.optString("title").trim(), content=event.optString("content").trim();
                    if (title.isEmpty()) title=content.length()>50?content.substring(0,50):content;
                    if (title.isEmpty()) title="有一件事，记得留意";
                    Intent openIntent=new Intent(context,MainActivity.class).setData(Uri.parse("shijian://event/"+Uri.encode(id))).putExtra("eventId",id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    PendingIntent open=PendingIntent.getActivity(context,0,openIntent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                    Notification notification=new Notification.Builder(context,ReminderScheduler.CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(content.isEmpty()?"打开时笺，看看这件事。":content).setStyle(new Notification.BigTextStyle().bigText(content)).setContentIntent(open).setAutoCancel(true).build();
                    context.getSystemService(NotificationManager.class).notify(id,0,notification);
                    break;
                }
            } catch (Exception ignored) { }
            finally { result.finish(); }
        },"shijian-reminder").start();
    }
}
