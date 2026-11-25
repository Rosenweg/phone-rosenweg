package org.fossify.phone.helpers

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import org.fossify.commons.extensions.canUseFullScreenIntent


/**
 * Rosenweg (25.11.2026): Ohne das Recht "Vollbild-Benachrichtigungen" (USE_FULL_SCREEN_INTENT,
 * Android 14 erteilt es nicht von selbst) erscheint ein eingehender Anruf bei gesperrtem Telefon
 * nur als kleines Symbol — man kann nicht abheben. Rosenweg Telefon prueft das darum beim Start,
 * bei jeder Anmeldung, bei jedem Anruf und alle zwei Stunden und warnt dauerhaft, solange es fehlt.
 * Auf geteilten Telefonen (Rosenweg Geraet installiert) gilt ausserdem "eingehende Anrufe immer
 * im Vollbild" fest, unabhaengig von der persoenlichen Einstellung.
 */
object Vollbildrecht {
    private const val KANAL = "rosenweg_warnung"
    private const val NOTIF_ID = 4310
    private const val GERAET = "ch.rosenweg4303.telefon"

    /** Geteiltes Rosenweg-Telefon (Rosenweg Geraet installiert)? */
    fun geteilt(c: Context): Boolean = try {
        c.packageManager.getPackageInfo(GERAET, 0); true
    } catch (_: Exception) {
        false
    }

    fun pruefen(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (c.canUseFullScreenIntent()) {
            nm.cancel(NOTIF_ID)
            return
        }
        if (nm.getNotificationChannel(KANAL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(KANAL, "Rosenweg Telefon: Einrichtung", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val einstellung = if (Build.VERSION.SDK_INT >= 34) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + c.packageName))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(c, NOTIF_ID, einstellung, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, KANAL)
            .setSmallIcon(org.fossify.commons.R.drawable.ic_phone_vector)
            .setContentTitle("Anrufe werden nicht angezeigt")
            .setContentText("Rosenweg Telefon fehlt das Recht für Vollbild-Anrufe. Bitte der Technik melden.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Rosenweg Telefon fehlt das Recht „Vollbild-Benachrichtigungen“. Bei gesperrtem Telefon "
                    + "erscheint ein Anruf dann nur als Symbol und lässt sich nicht annehmen. "
                    + "Bitte der Technik melden (Einrichtungsskript) oder hier antippen und erlauben."))
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            nm.notify(NOTIF_ID, n)
        } catch (_: SecurityException) {
            // ohne Benachrichtigungsrecht: nichts zu machen
        }
    }

    /** Regelmaessige Pruefung (alle zwei Stunden, ungenau, ueberlebt keinen Neustart: StartReceiver plant neu). */
    fun planen(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            c, NOTIF_ID, Intent(c, Pruefung::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HOUR,
            2 * AlarmManager.INTERVAL_HOUR, pi
        )
    }

    class Pruefung : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = pruefen(context)
    }
}
