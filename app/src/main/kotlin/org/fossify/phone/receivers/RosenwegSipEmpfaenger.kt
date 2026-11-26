package org.fossify.phone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.phone.helpers.Betriebsart
import org.fossify.phone.helpers.PersoenlichesKonto
import org.fossify.phone.helpers.Vollbildrecht

/**
 * Rosenweg: An-/Abmelden am geteilten Telefon und Wechsel des persoenlichen SIP-Zugangs
 * (Broadcasts der Geraete-App, nur mit der Rosenweg-Signatur: Manifest-Permission ANLAGE).
 */
class RosenwegSipEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // kommt nur von Rosenweg Geraet: also geteiltes Telefon (Telefon-Bausteine an)
        Betriebsart.anwenden(context)
        when (intent.action) {
            PersoenlichesKonto.ABGEMELDET -> PersoenlichesKonto.abgemeldet(context)
            PersoenlichesKonto.ANGEMELDET, PersoenlichesKonto.SIP_PERSON_GEAENDERT -> {
                PersoenlichesKonto.abgleichen(context, intent.action.orEmpty().substringAfterLast('.'))
                Vollbildrecht.pruefen(context)
            }
        }
    }
}

/** Nach einem Update von Rosenweg Telefon: Konto wieder anmelden (falls jemand angemeldet ist). */
class RosenwegSipStart : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED || intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Update auf einem privaten Handy: Telefon-Bausteine aus, alte Anrufkonten weg
            Betriebsart.anwenden(context)
            PersoenlichesKonto.abgleichen(context, "start")
            Vollbildrecht.pruefen(context)
            Vollbildrecht.planen(context)
            PersoenlichesKonto.nachfragenPlanen(context)
        }
    }
}
