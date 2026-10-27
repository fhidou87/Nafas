package com.nafas.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/** The gentle reminder: a notification at the chosen time on the chosen days, scheduled with AlarmManager. */
final class Reminders {

    private static final String PREFS = "nafas_reminder";
    private static final String CHANNEL = "nafas_reminder";
    static final String ACTION_FIRE = "com.nafas.app.REMINDER";

    private Reminders() { }

    static void save(Context c, String json) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("cfg", json).apply();
    }

    private static JSONObject load(Context c) {
        try {
            String s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("cfg", null);
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(ACTION_FIRE);
        return PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Cancels any pending reminder and schedules the next one, if the reminder is on. */
    static void reschedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(c);
        am.cancel(pi);
        JSONObject cfg = load(c);
        if (cfg == null || !cfg.optBoolean("on", false)) return;
        long at = next(cfg, System.currentTimeMillis());
        if (at <= 0) return;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    /** Next time in the future that matches the chosen time and days (days are 1 = Monday … 7 = Sunday). */
    static long next(JSONObject cfg, long from) {
        try {
            String[] hm = cfg.optString("time", "20:30").split(":");
            int h = Integer.parseInt(hm[0]), m = hm.length > 1 ? Integer.parseInt(hm[1]) : 0;
            JSONArray days = cfg.optJSONArray("days");
            boolean[] on = new boolean[8];
            if (days != null) for (int i = 0; i < days.length(); i++) {
                int d = days.optInt(i, 0);
                if (d >= 1 && d <= 7) on[d] = true;
            }
            for (int add = 0; add < 8; add++) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(from);
                cal.add(Calendar.DAY_OF_YEAR, add);
                cal.set(Calendar.HOUR_OF_DAY, h);
                cal.set(Calendar.MINUTE, m);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                int iso = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1; // Sunday=1 → 7, Monday=2 → 1
                if (cal.getTimeInMillis() > from + 1000 && on[iso]) return cal.getTimeInMillis();
            }
        } catch (Exception ignored) { }
        return -1;
    }

    static void fire(Context c) {
        JSONObject cfg = load(c);
        if (cfg != null && cfg.optBoolean("on", false)) {
            notify(c, cfg.optString("title", "Nafas"), cfg.optString("text", "Time for a short breath, if you'd like."));
        }
        reschedule(c);
    }

    static void notify(Context c, String title, String text) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, c.getString(R.string.channel_reminder), NotificationManager.IMPORTANCE_DEFAULT));
        }
        PendingIntent open = PendingIntent.getActivity(c, 2,
                new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat_nafas)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true);
        nm.notify(7002, b.build());
    }
}
