package com.mrkuangker.shijian;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDateTime;
import java.time.ZoneId;

final class ReminderScheduler {
    static final String CHANNEL = "shijian-reminders";
    private static final Object LOCK = new Object();
    static void reschedule(Context context) {
        synchronized (LOCK) {
            try {
                NotificationManager notifications = context.getSystemService(NotificationManager.class);
                notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "事项提醒", NotificationManager.IMPORTANCE_DEFAULT));
                AlarmManager alarms = context.getSystemService(AlarmManager.class);
                android.content.SharedPreferences preferences = context.getSharedPreferences("reminders", Context.MODE_PRIVATE);
                JSONArray previous = new JSONArray(preferences.getString("ids", "[]"));
                String raw = DataStore.read(context);
                // Reading must succeed before touching existing schedules.
                JSONArray events = raw == null ? new JSONArray() : new JSONObject(raw).getJSONArray("events");
                for (int i=0;i<previous.length();i++) {
                    PendingIntent pending = pending(context, previous.getString(i), PendingIntent.FLAG_NO_CREATE);
                    if (pending != null) { alarms.cancel(pending); pending.cancel(); }
                }
                JSONArray ids = new JSONArray();
                long now = System.currentTimeMillis();
                for (int i=0;i<events.length();i++) {
                    JSONObject event = events.getJSONObject(i);
                    String reminder = event.optString("reminderAt");
                    if (event.optBoolean("completed") || reminder.isEmpty()) continue;
                    try {
                        long time = LocalDateTime.parse(reminder).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                        if (time <= now) continue;
                        String id = event.getString("id");
                        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending(context,id,PendingIntent.FLAG_UPDATE_CURRENT));
                        ids.put(id);
                    } catch (Exception ignored) { }
                }
                preferences.edit().putString("ids",ids.toString()).apply();
            } catch (Exception ignored) { }
        }
    }
    static PendingIntent pending(Context context, String id, int flags) {
        Intent intent = new Intent(context, ReminderReceiver.class).setData(Uri.parse("shijian://reminder/" + Uri.encode(id))).putExtra("eventId",id);
        return PendingIntent.getBroadcast(context,0,intent,flags | PendingIntent.FLAG_IMMUTABLE);
    }
}
