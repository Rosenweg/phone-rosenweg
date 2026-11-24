package org.fossify.phone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.phone.helpers.PersoenlichesKonto

/**
 * Rosenweg: An-/Abmelden am geteilten Telefon und Wechsel des persoenlichen SIP-Zugangs
 * (Broadcasts der Geraete-App, nur mit der Rosenweg-Signatur: Manifest-Permission ANLAGE).
 */
class RosenwegSipEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PersoenlichesKonto.ABGEMELDET -> PersoenlichesKonto.abgemeldet(context)
            PersoenlichesKonto.ANGEMELDET, PersoenlichesKonto.SIP_PERSON_GEAENDERT ->
                PersoenlichesKonto.abgleichen(context, intent.action.orEmpty().substringAfterLast('.'))
        }
    }
}

/** Nach einem Update von Rosenweg Telefon: Konto wieder anmelden (falls jemand angemeldet ist). */
class RosenwegSipStart : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED || intent.action == Intent.ACTION_BOOT_COMPLETED) {
            PersoenlichesKonto.abgleichen(context, "start")
        }
    }
}
