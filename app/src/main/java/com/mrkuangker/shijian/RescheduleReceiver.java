package com.mrkuangker.shijian;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class RescheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        PendingResult pending=goAsync();
        new Thread(() -> { try { ReminderScheduler.reschedule(context); } finally { pending.finish(); } },"shijian-reschedule").start();
    }
}
