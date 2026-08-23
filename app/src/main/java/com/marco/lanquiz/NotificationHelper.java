package com.marco.lanquiz;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/** Notifica di aggiornamento disponibile. */
public final class NotificationHelper {

    private static final String CHANNEL = "aggiornamenti";
    private static final int ID_UPDATE = 1;

    private NotificationHelper() {
    }

    public static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                    c.getString(R.string.canale_aggiornamenti),
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    public static void notifyUpdate(Context c, String version) {
        if (ContextCompat.checkSelfPermission(c, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Intent open = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(c.getString(R.string.nuova_versione))
                .setContentText(c.getString(R.string.notifica_aggiornamento, version))
                .setAutoCancel(true)
                .setContentIntent(pi);
        NotificationManagerCompat.from(c).notify(ID_UPDATE, b.build());
    }
}
