package org.fossify.phone.helpers

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * Gemeinsame Geraetekennung der Rosenweg-Apps (Stefan 26.11.2026: im Portal soll jedes Handy
 * genau einmal erscheinen, mit allen Rosenweg-Apps darauf).
 *
 * sha256("rosenweg:" + ANDROID_ID) als Hex - genau wie KartenSitzung.geraetKennung in
 * commons-rosenweg (cloud). Seit Android 8 ist ANDROID_ID je Signaturschluessel, Handy und
 * Android-Benutzer: alle Rosenweg-Apps eines Handys liefern denselben Wert, fremde Apps einen
 * anderen. Die ANDROID_ID selbst verlaesst das Handy nie. Die Server zeigen sie im Browser nur
 * als Gruppenschluessel je Person (sha256 aus E-Mail und Kennung), nie im Klartext.
 * Sobald die App commons-rosenweg cloud >= 0.9.0 nutzt, kann sie KartenSitzung.geraetKennung nehmen.
 */
object GeraetKennung {
    fun von(c: Context): String {
        @Suppress("HardwareIds")
        val id = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID) ?: "unbekannt"
        return MessageDigest.getInstance("SHA-256").digest("rosenweg:$id".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
