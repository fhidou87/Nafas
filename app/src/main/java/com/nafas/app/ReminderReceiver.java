package com.nafas.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Shows the reminder when its alarm fires, and re-schedules reminders after a restart or an app update. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent != null ? intent.getAction() : null;
        if (Reminders.ACTION_FIRE.equals(a)) Reminders.fire(context);
        else Reminders.reschedule(context);
    }
}
