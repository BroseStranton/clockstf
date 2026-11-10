package com.example.horlogewidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;
import android.widget.RemoteViews;

import java.util.Calendar;

/**
 * Widget heure + date.
 * - L'heure (HH:mm, 24h) est geree par un TextClock : mise a jour automatique.
 * - La date ("ven 6 nov") est calculee ici et rafraichie a minuit, ainsi qu'a chaque
 *   changement de date / heure / fuseau horaire.
 */
public class ClockWidgetProvider extends AppWidgetProvider {

    private static final String CLOCK_PACKAGE = "com.android.deskclock";
    private static final String ACTION_MIDNIGHT = "com.example.horlogewidget.ACTION_MIDNIGHT";

    // Calendar.DAY_OF_WEEK : dimanche = 1
    private static final String[] JOURS = {"Dim", "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam"};
    private static final String[] MOIS = {
            "jan", "fév", "mar", "avr", "mai", "juin",
            "juil", "aoû", "sep", "oct", "nov", "déc"};

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
        String action = intent.getAction();
        if (ACTION_MIDNIGHT.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            refreshAll(context);
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
        views.setOnClickPendingIntent(R.id.widget_root, clockAppIntent(context));
        return views;
    }

    /** Ouvre l'application Horloge (Xiaomi / AOSP : com.android.deskclock). */
    private static PendingIntent clockAppIntent(Context context) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(CLOCK_PACKAGE);
        if (intent == null) {
            // Repli : n'importe quelle application horloge installee
            intent = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Ex : "ven 6 nov" (jour sans zero devant). */
    static String formatDate(Calendar c) {
        String jour = JOURS[c.get(Calendar.DAY_OF_WEEK) - 1];
        int numero = c.get(Calendar.DAY_OF_MONTH);
        String mois = MOIS[c.get(Calendar.MONTH)];
        return jour + " " + numero + " " + mois;
    }

    private static PendingIntent midnightIntent(Context context) {
        Intent intent = new Intent(context, ClockWidgetProvider.class);
        intent.setAction(ACTION_MIDNIGHT);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
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
        am.setExact(AlarmManager.RTC, next.getTimeInMillis(), midnightIntent(context));
    }
}
