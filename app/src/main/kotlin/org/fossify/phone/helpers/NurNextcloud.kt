package org.fossify.phone.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact

/**
 * Rosenweg (Stefan 27.11.2026: "Telefon und Kontakte das gleiche, nix Lokales auf privaten
 * Smartphones"): auf privaten Handys (ohne Rosenweg Geraet) zeigt Rosenweg Telefon nur die
 * Kontakte aus der Rosenweg-Nextcloud - keine Google-, Samsung-, SIM- oder sonstigen
 * Telefonbuch-Kontakte des Handys, auch nicht als Name in der Anrufliste oder beim Anruf.
 *
 * Die Nextcloud-Adressbuecher gleicht Rosenweg Kontakte (ch.rosenweg4303.contacts) per CardDAV ins
 * Android-Adressbuch ab, unter dem eigenen Konto [KONTOTYP]. Rosenweg Telefon liest nur dieses
 * Konto (Entscheid Stefan, Variante A: kein zweiter Abgleich in Telefon). Ohne Rosenweg Kontakte
 * bleibt die Kontaktliste leer, mit Hinweis - waehlen per Nummer geht trotzdem.
 *
 * Geteilte Telefone (mit Rosenweg Geraet): unveraendert, alle Quellen wie bisher.
 * Lokale Kontakte werden nie geloescht, nur nicht angezeigt.
 */
// Systemaufrufe (Adressbuch, PackageManager, startActivity) koennen vieles werfen; hier zaehlt
// nur: ohne Ergebnis gilt "nichts da", die App bleibt bedienbar.
@Suppress("TooManyFunctions", "TooGenericExceptionCaught", "SwallowedException")
object NurNextcloud {
    private const val TAG = "RosenwegNurNextcloud"

    /** Konto von Rosenweg Kontakte fuer die Nextcloud-Adressbuecher (RosenwegNextcloud.KONTOTYP dort). */
    const val KONTOTYP = "ch.rosenweg4303.contacts.nextcloud"
    const val KONTAKTE_APP = "ch.rosenweg4303.contacts"
    const val APPS_SEITE = "https://rosenweg4303.ch/apps.html"

    /** Nur Nextcloud-Kontakte zeigen? = privates Handy. */
    fun aktiv(c: Context): Boolean = Betriebsart.privat(c)

    /** Kontakte aus Fossifys eigenem Speicher ("Telefonspeicher, fuer andere Apps unsichtbar") zeigen? */
    fun lokaleZeigen(c: Context, ignoriert: Set<String>): Boolean = !aktiv(c) && SMT_PRIVATE !in ignoriert

    /**
     * Wie ContactsHelper.getContacts(showOnlyContactsWithNumbers = true), auf privaten Handys aber
     * nur Kontakte des Nextcloud-Kontos. [callback] im UI-Thread (wie bei ContactsHelper).
     */
    fun kontakte(c: Context, getAll: Boolean = false, callback: (ArrayList<Contact>) -> Unit) {
        ContactsHelper(c).getContacts(getAll = getAll, showOnlyContactsWithNumbers = true) { alle ->
            if (!aktiv(c)) {
                callback(alle)
                return@getContacts
            }
            ensureBackgroundThread {
                filtern(c, alle)
                Handler(Looper.getMainLooper()).post { callback(alle) }
            }
        }
    }

    /** Entfernt (in der Liste, nicht im Adressbuch) alles, was nicht aus der Nextcloud kommt. Blockierend. */
    fun filtern(c: Context, kontakte: MutableList<Contact>) {
        if (!aktiv(c)) return
        val unsere = rohIds(c)
        kontakte.retainAll { it.source != SMT_PRIVATE && it.id in unsere }
    }

    /** IDs der Roh-Kontakte des Nextcloud-Kontos (Contact.id ist bei Fossify die RAW_CONTACT_ID). */
    private fun rohIds(c: Context): Set<Int> {
        if (!c.hasPermission(PERMISSION_READ_CONTACTS)) return emptySet()
        val raw = ContactsContract.RawContacts.CONTENT_URI
        val wo = "${ContactsContract.RawContacts.ACCOUNT_TYPE}=? AND ${ContactsContract.RawContacts.DELETED}=0"
        return try {
            c.contentResolver.query(raw, arrayOf(ContactsContract.RawContacts._ID), wo, arrayOf(KONTOTYP), null)
                ?.use { cur -> generateSequence { if (cur.moveToNext()) cur.getInt(0) else null }.toHashSet() }
                ?: emptySet()
        } catch (e: Exception) {
            Log.w(TAG, "Nextcloud-Kontakte: ${e.javaClass.simpleName}")
            emptySet()
        }
    }

    enum class Zustand {
        /** Rosenweg Kontakte ist nicht installiert. */
        KEINE_APP,

        /** Rosenweg Kontakte da, aber (noch) keine Nextcloud-Adressbuecher abgeglichen. */
        NICHT_VERBUNDEN,

        /** Nextcloud-Konto da (die Liste ist leer, weil es keine Kontakte mit Nummer gibt). */
        VERBUNDEN,
    }

    /** Fuer den leeren Zustand. Blockierend (Abfrage im Adressbuch). */
    fun zustand(c: Context): Zustand {
        if (!kontakteAppDa(c)) return Zustand.KEINE_APP
        if (!c.hasPermission(PERMISSION_READ_CONTACTS)) return Zustand.NICHT_VERBUNDEN
        val wo = "${ContactsContract.RawContacts.ACCOUNT_TYPE}=?"
        val da = listOf(ContactsContract.RawContacts.CONTENT_URI, ContactsContract.Groups.CONTENT_URI).any { uri ->
            try {
                c.contentResolver.query(uri, arrayOf("_id"), wo, arrayOf(KONTOTYP), null)?.use { it.count > 0 } == true
            } catch (e: Exception) {
                false
            }
        }
        return if (da) Zustand.VERBUNDEN else Zustand.NICHT_VERBUNDEN
    }

    fun kontakteAppDa(c: Context): Boolean = try {
        c.packageManager.getPackageInfo(KONTAKTE_APP, 0)
        true
    } catch (e: Exception) {
        false
    }

    /** Erklaerung fuer eine leere Kontaktliste auf dem privaten Handy. */
    fun leerText(z: Zustand): String = when (z) {
        Zustand.KEINE_APP -> "Hier erscheinen deine Kontakte aus der Rosenweg-Nextcloud – nicht das Telefonbuch " +
            "des Handys. Dafür braucht es die App Rosenweg Kontakte. Wählen per Nummer geht auch ohne."
        Zustand.NICHT_VERBUNDEN -> "Hier erscheinen deine Kontakte aus der Rosenweg-Nextcloud – nicht das " +
            "Telefonbuch des Handys. Öffne Rosenweg Kontakte und melde dich dort an, dann sind sie auch hier."
        Zustand.VERBUNDEN -> "In deinen Adressbüchern der Rosenweg-Nextcloud gibt es noch keine Kontakte mit " +
            "Telefonnummer. Das Telefonbuch des Handys wird hier nicht angezeigt."
    }

    /** Knopf unter dem Text. */
    fun leerKnopf(z: Zustand): String = when (z) {
        Zustand.KEINE_APP -> "Rosenweg Kontakte holen"
        Zustand.NICHT_VERBUNDEN -> "Rosenweg Kontakte öffnen"
        Zustand.VERBUNDEN -> "Neuer Kontakt"
    }

    /** Knopf ausfuehren. */
    fun leerAktion(a: Activity, z: Zustand) {
        try {
            when (z) {
                Zustand.KEINE_APP -> a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APPS_SEITE)))
                Zustand.NICHT_VERBUNDEN ->
                    a.packageManager.getLaunchIntentForPackage(KONTAKTE_APP)?.let { a.startActivity(it) }
                Zustand.VERBUNDEN -> a.startActivity(neuerKontakt(a))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Aktion: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Neuer Kontakt: auf privaten Handys direkt in Rosenweg Kontakte (speichert nur in die Nextcloud),
     * nicht in die Kontakte-App des Handys.
     */
    fun neuerKontakt(c: Context): Intent =
        nachRosenwegKontakte(c, Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI))

    /** Fuer "Nummer zu Kontakt hinzufuegen" u.ae.: privat in Rosenweg Kontakte lenken (falls installiert). */
    fun nachRosenwegKontakte(c: Context, i: Intent): Intent {
        if (aktiv(c) && kontakteAppDa(c)) i.`package` = KONTAKTE_APP
        return i
    }
}
