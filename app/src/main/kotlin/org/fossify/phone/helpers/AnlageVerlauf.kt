package org.fossify.phone.helpers

import android.content.Context
import android.net.Uri
import android.provider.CallLog.Calls
import android.telephony.PhoneNumberUtils
import android.util.Log
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.models.RecentCall

/**
 * Rosenweg: Verlauf der angemeldeten Person aus der Telefonanlage (Regel 6).
 *
 * Kommt ueber die Geraete-App (ContentProvider, nur Rosenweg-Signatur), die mit
 * den SIP-Zugangsdaten bei der Anlage fragt — Rosenweg Telefon selbst braucht
 * dafuer kein Netz. Ohne Geraete-App (privates Handy) bleibt die Liste leer.
 * Blockiert: nur im Hintergrund aufrufen.
 */
object AnlageVerlauf {
    const val AUTHORITY = "ch.rosenweg4303.telefon.anlage"
    private val ANRUFLISTE = Uri.parse("content://$AUTHORITY/anrufliste")

    fun voicemailUri(anlageId: Int): Uri = Uri.parse("content://$AUTHORITY/voicemail/$anlageId")

    fun laden(context: Context, contacts: List<Contact>): List<RecentCall> {
        val ergebnis = mutableListOf<RecentCall>()
        try {
            context.contentResolver.query(ANRUFLISTE, null, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getInt(c.getColumnIndexOrThrow("_id"))
                    val richtung = c.getString(c.getColumnIndexOrThrow("richtung"))
                    val nummer = c.getString(c.getColumnIndexOrThrow("nummer")).orEmpty()
                    val audio = c.getInt(c.getColumnIndexOrThrow("voicemail_audio")) == 1
                    val kontakt = contacts.firstOrNull { it.doesHavePhoneNumber(nummer) }
                    ergebnis += RecentCall(
                        id = -id,   // eigene Nummernreihe, kollidiert nicht mit dem Android-Anrufprotokoll
                        phoneNumber = nummer,
                        name = kontakt?.getNameToDisplay() ?: nummer,
                        photoUri = kontakt?.photoUri.orEmpty(),
                        startTS = c.getLong(c.getColumnIndexOrThrow("beginn")),
                        duration = c.getInt(c.getColumnIndexOrThrow("dauer")),
                        type = when {
                            audio -> Calls.VOICEMAIL_TYPE
                            richtung == "ausgehend" -> Calls.OUTGOING_TYPE
                            richtung == "eingehend" -> Calls.INCOMING_TYPE
                            else -> Calls.MISSED_TYPE
                        },
                        simID = -1,
                        simColor = -1,
                        specificNumber = "",
                        specificType = "",
                        isUnknownNumber = nummer.isEmpty(),
                        anlageId = id,
                        voicemailText = c.getString(c.getColumnIndexOrThrow("voicemail")).orEmpty(),
                    )
                }
            }
        } catch (e: SecurityException) {
            // keine Geraete-App mit Rosenweg-Signatur (privates Handy)
        } catch (e: Exception) {
            Log.w("RosenwegVerlauf", "laden: $e")
        }
        return ergebnis
    }

    /**
     * Lokales Protokoll + Anlage zusammenfuehren. Ein Anruf ueber dieses Geraet steht
     * in beiden: dann gilt der lokale Eintrag (gleiche Nummer, hoechstens 2 Min. Abstand),
     * ausser die Anlage hat eine Voicemail dazu.
     */
    @Suppress("DEPRECATION")
    fun zusammenfuehren(lokal: List<RecentCall>, anlage: List<RecentCall>): List<RecentCall> {
        if (anlage.isEmpty()) return lokal
        val doppelt = mutableSetOf<Int>()
        val anlageNeu = anlage.filter { a ->
            val l = lokal.firstOrNull {
                kotlin.math.abs(it.startTS - a.startTS) < 120_000 && PhoneNumberUtils.compare(it.phoneNumber, a.phoneNumber)
            }
            when {
                l == null -> true
                a.type == Calls.VOICEMAIL_TYPE -> { doppelt += l.id; true }
                else -> false
            }
        }
        return (lokal.filter { it.id !in doppelt } + anlageNeu).sortedByDescending { it.startTS }
    }
}
