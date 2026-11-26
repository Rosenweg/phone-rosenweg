package org.fossify.phone.helpers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import org.sipdroid.sipua.ui.Receiver

/**
 * Rosenweg (Stefan 26.11.2026): Rosenweg Telefon darf sich auf privaten Handys NICHT ins
 * Android-Telefon einklinken.
 *
 * - Geteiltes Telefon (Rosenweg Geraet installiert): wie bisher Standard-Telefon-App mit
 *   InCallService, Anrufkonto "Rosenweg persoenlich" (ConnectionService), Anrufsperre und den
 *   tel:-/DIAL-/CALL-Eingaengen des Systems.
 * - Privates Handy (ohne Rosenweg Geraet): nichts davon. Die Rosenweg-Telefonie laeuft ganz in
 *   der App (EigenerAnruf, EigenerAnrufActivity, EigeneAnrufliste). Mobilfunk-Anrufe, das
 *   Anrufprotokoll und die Telefon-App des Handys bleiben unberuehrt.
 *
 * Umgesetzt mit EINER App fuer beide Faelle: die Bausteine, mit denen sich die App beim System
 * als Telefon-App/Telecom-Teilnehmer ausweist, sind im Manifest eingeschaltet (geteilte Telefone
 * bleiben so ohne Luecke unveraendert, auch direkt nach einem Update) und werden hier auf privaten
 * Handys zur Laufzeit ausgeschaltet (PackageManager.setComponentEnabledSetting). Ohne die
 * DIAL-Eingaenge ist die App fuer die Rolle "Standard-Telefon-App" nicht mehr waehlbar, ohne
 * InCallService/ConnectionService kann Telecom sie nicht mehr binden.
 *
 * Aufgerufen beim Start des Prozesses (App), nach Update/Neustart (RosenwegSipStart), bei
 * Meldungen der Geraete-App und alle 30 Min. (PersoenlichesKonto.Nachfrage). Ein Wechsel
 * (Rosenweg Geraet nachtraeglich installiert/entfernt) wird so spaetestens dort uebernommen.
 */
object Betriebsart {
    private const val TAG = "RosenwegBetriebsart"
    private const val KANAL = "rosenweg_warnung"
    private const val NOTIF_ROLLE = 4311

    /** Bausteine, mit denen das System die App als Telefon-App bzw. Telecom-Teilnehmer sieht. */
    private val TELEFON_BAUSTEINE = listOf(
        "org.fossify.phone.services.CallService",                  // InCallService (Anruffenster des Systems)
        "org.fossify.phone.services.SimpleCallScreeningService",   // Anrufsperre (CallScreeningService)
        "org.sipdroid.sipua.ui.SipConnectionService",              // ConnectionService (Anrufkonto)
        "org.fossify.phone.activities.DialpadActivity.System",     // DIAL / tel: (Rolle Standard-Telefon-App)
        "org.fossify.phone.activities.DialerActivity.System",      // CALL tel:
        "org.fossify.phone.activities.MainActivity.Anrufliste",    // Anrufliste des Systems anzeigen
    )

    /** Geteiltes Rosenweg-Telefon (Rosenweg Geraet installiert)? */
    fun geteilt(c: Context): Boolean = PersoenlichesKonto.geraetDa(c)

    /** Privates Handy: Rosenweg-Telefonie nur in der App, ohne Android-Telefon. */
    fun privat(c: Context): Boolean = !geteilt(c)

    /** Bausteine passend zur Betriebsart ein-/ausschalten, auf privaten Handys Reste abbauen. */
    @Synchronized
    fun anwenden(c: Context) {
        val app = c.applicationContext
        val geteilt = geteilt(app)
        if (!geteilt && CallManager.getPhoneState() != NoCall) {
            // laeuft gerade ein Anruf ueber unseren InCallService (Update mitten im Gespraech):
            // nicht wegziehen, beim naechsten Anlass nachholen
            Log.i(TAG, "Anruf im System-Anruffenster - Umstellung spaeter")
            return
        }
        val pm = app.packageManager
        var geaendert = 0
        for (name in TELEFON_BAUSTEINE) {
            val cn = ComponentName(app.packageName, name)
            val soll = if (geteilt) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            try {
                if (pm.getComponentEnabledSetting(cn) != soll) {
                    pm.setComponentEnabledSetting(cn, soll, PackageManager.DONT_KILL_APP)
                    geaendert++
                }
            } catch (e: Exception) {
                Log.w(TAG, "$name: ${e.javaClass.simpleName}")
            }
        }
        if (geaendert > 0) Log.i(TAG, (if (geteilt) "geteiltes Telefon" else "privates Handy") + ": $geaendert Bausteine umgestellt")
        if (!geteilt) {
            Receiver.telecomAbbauen(app)
            rolleHinweis(app)
        } else {
            app.getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ROLLE)
        }
    }

    /** Haelt Rosenweg Telefon (aus einer frueheren Version) noch die Rolle Standard-Telefon-App? */
    fun haeltTelefonRolle(c: Context): Boolean = rolle(c, RoleManager.ROLE_DIALER)

    /** ... oder die Rolle Anrufsperre/Anrufer-ID (frueher fuer "Unbekannte sperren" angefragt)? */
    fun haeltSperrRolle(c: Context): Boolean = rolle(c, RoleManager.ROLE_CALL_SCREENING)

    private fun rolle(c: Context, rolle: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return rolle == RoleManager.ROLE_DIALER &&
                c.getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage == c.packageName
        }
        return try {
            c.getSystemService(RoleManager::class.java)?.isRoleHeld(rolle) == true
        } catch (e: Exception) {
            false
        }
    }

    /** Einstellungen "Standard-Apps" (dort waehlt die Person wieder die Telefon-App des Handys). */
    fun standardAppsEinstellung(): Intent =
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    const val ROLLE_TEXT =
        "Rosenweg Telefon ist auf diesem Handy noch als Standard-Telefon-App eingetragen. Das braucht es " +
            "nicht mehr: Rosenweg-Anrufe laufen jetzt ganz in Rosenweg Telefon.\n\n" +
            "Bitte unter Einstellungen › Apps › Standard-Apps › Telefon-App wieder die Telefon-App " +
            "des Handys wählen (z. B. „Telefon“ von Samsung oder Google)."

    /**
     * Rueckbau alter privater Installationen: die Rolle kann eine App nicht selbst abgeben (nur die
     * Person oder das System). Android gibt sie meist von selbst zurueck, sobald die DIAL-Eingaenge
     * aus sind; bis dahin ein Hinweis (Mobilfunk-Anrufe zeigt in der Zwischenzeit das Anruffenster
     * des Systems, weil unser InCallService aus ist).
     */
    private fun rolleHinweis(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (!haeltTelefonRolle(c) && !haeltSperrRolle(c)) {
            nm.cancel(NOTIF_ROLLE)
            return
        }
        if (nm.getNotificationChannel(KANAL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(KANAL, "Rosenweg Telefon: Einrichtung", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val pi = PendingIntent.getActivity(
            c, NOTIF_ROLLE, standardAppsEinstellung(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(c, KANAL)
            .setSmallIcon(org.fossify.commons.R.drawable.ic_phone_vector)
            .setContentTitle("Telefon-App des Handys wieder wählen")
            .setContentText("Rosenweg Telefon ist noch Standard-Telefon-App. Antippen und ändern.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(ROLLE_TEXT))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(NOTIF_ROLLE, n)
        } catch (_: SecurityException) {
        }
    }
}
