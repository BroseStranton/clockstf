package com.example.horlogewidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.AlarmClock;
import android.widget.RemoteViews;

import java.util.Calendar;

/**
 * Widget heure + date.
 * - L'heure (HH:mm, 24h) est geree par un TextClock : mise a jour automatique.
 * - La date est calculee ici et rafraichie a minuit, a l'allumage de l'ecran,
 *   ainsi qu'a chaque changement de date / heure / fuseau horaire.
 */
public class ClockWidgetProvider extends AppWidgetProvider {

    private static final String CLOCK_PACKAGE = "com.android.deskclock";
    private static final String ACTION_MIDNIGHT = "com.example.horlogewidget.ACTION_MIDNIGHT";

    // Calendar.DAY_OF_WEEK : lundi doit donner l'index 0, dimanche l'index 6
    private static final String[] JOURS = {"LUN", "MAR", "MER", "JEU", "VEN", "SAM", "DIM"};
    private static final String[] MOIS = {
            "JAN", "FÉV", "MAR", "AVR", "MAI", "JUIN",
            "JUIL", "AOÛ", "SEP", "OCT", "NOV", "DÉC"};

    private static int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private static boolean canResolveIntent(Context context, Intent intent) {
        if (intent == null) {
            return false;
        }
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            return false;
        }
        ResolveInfo info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return info != null;
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, buildViews(context));
        }
        scheduleMidnight(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        boolean isDateOrScreenRefresh = ACTION_MIDNIGHT.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_SCREEN_ON.equals(action);
        boolean isClockChange = Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action);

        if (isDateOrScreenRefresh || isClockChange) {
            refreshAll(context);
        }
        if (ACTION_MIDNIGHT.equals(action) || isClockChange) {
            scheduleMidnight(context);
        }
    }

    @Override
    public void onEnabled(Context context) {
        scheduleMidnight(context);
    }

    @Override
    public void onDisabled(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(midnightIntent(context));
        }
    }

    private static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName cn = new ComponentName(context, ClockWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(cn);
        for (int id : ids) {
            manager.updateAppWidget(id, buildViews(context));
        }
    }

    private static RemoteViews buildViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_clock);
        views.setTextViewText(R.id.txt_date, formatDate(Calendar.getInstance()));
        PendingIntent openClock = clockAppIntent(context);
        if (openClock != null) {
            views.setOnClickPendingIntent(R.id.widget_root, openClock);
        }
        return views;
    }

    /** Ouvre l'application Horloge (Xiaomi / AOSP : com.android.deskclock). */
    private static PendingIntent clockAppIntent(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = (pm != null) ? pm.getLaunchIntentForPackage(CLOCK_PACKAGE) : null;

        if (!canResolveIntent(context, intent)) {
            intent = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        }
        if (!canResolveIntent(context, intent)) {
            intent = new Intent(AlarmClock.ACTION_SET_ALARM);
        }
        if (!canResolveIntent(context, intent)) {
            return null;
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, 1, intent, pendingIntentFlags());
    }

    /** Ex : "VEN 6 NOV" (jour sans zero devant). */
    static String formatDate(Calendar c) {
        // DAY_OF_WEEK : DIM=1, LUN=2, ..., SAM=7  ->  index : LUN=0, ..., DIM=6
        int index = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        String jour = JOURS[index];
        int numero = c.get(Calendar.DAY_OF_MONTH);
        String mois = MOIS[c.get(Calendar.MONTH)];
        return jour + " " + numero + " " + mois;
    }

    private static PendingIntent midnightIntent(Context context) {
        Intent intent = new Intent(context, ClockWidgetProvider.class);
        intent.setAction(ACTION_MIDNIGHT);
        return PendingIntent.getBroadcast(context, 0, intent, pendingIntentFlags());
    }

    /** Programme un rafraichissement juste apres le prochain minuit. */
    private static void scheduleMidnight(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Calendar next = Calendar.getInstance();
        next.add(Calendar.DAY_OF_MONTH, 1);
        next.set(Calendar.HOUR_OF_DAY, 0);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 1);
        next.set(Calendar.MILLISECOND, 0);
        long triggerAtMillis = next.getTimeInMillis();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAtMillis, midnightIntent(context));
        } else {
            am.setExact(AlarmManager.RTC, triggerAtMillis, midnightIntent(context));
        }
    }
}
