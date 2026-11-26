package org.fossify.phone.helpers

import android.content.Context
import android.provider.CallLog.Calls
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.models.RecentCall
import org.json.JSONArray
import org.json.JSONObject

/**
 * Rosenweg (26.11.2026): Anrufliste von Rosenweg Telefon auf privaten Handys.
 *
 * Dort schreibt Rosenweg Telefon nichts ins Anrufprotokoll von Android und liest es auch nicht:
 * die Liste enthaelt nur die Rosenweg-Anrufe, die ueber diese App liefen. Sie ist ein
 * Zwischenspeicher (Grundregel: Daten liegen im Rosenweg-System) und wird beim Abmelden
 * geloescht. Hoechstens [MAX] Eintraege, neueste zuerst.
 */
object EigeneAnrufliste {
    private const val PREFS = "rosenweg_anrufliste"
    private const val LISTE = "liste"
    private const val NAECHSTE_ID = "naechste_id"
    private const val MAX = 300

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun lesen(c: Context): JSONArray = try {
        JSONArray(prefs(c).getString(LISTE, "[]"))
    } catch (e: Exception) {
        JSONArray()
    }

    /** typ = CallLog.Calls.*_TYPE (INCOMING, OUTGOING, MISSED, REJECTED). */
    @Synchronized
    fun eintragen(c: Context, nummer: String, name: String?, beginn: Long, dauerSek: Int, typ: Int) {
        val p = prefs(c)
        val id = p.getInt(NAECHSTE_ID, 1)
        val alt = lesen(c)
        val neu = JSONArray().put(
            JSONObject().put("id", id).put("nummer", nummer).put("name", name.orEmpty())
                .put("beginn", beginn).put("dauer", dauerSek).put("typ", typ)
        )
        for (i in 0 until minOf(alt.length(), MAX - 1)) neu.put(alt.get(i))
        p.edit().putString(LISTE, neu.toString()).putInt(NAECHSTE_ID, id + 1).apply()
    }

    fun laden(c: Context, contacts: List<Contact>): List<RecentCall> {
        val a = lesen(c)
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val nummer = o.optString("nummer")
            val kontakt = if (nummer.isEmpty()) null else contacts.firstOrNull { it.doesHavePhoneNumber(nummer) }
            RecentCall(
                id = o.optInt("id"),
                phoneNumber = nummer,
                name = kontakt?.getNameToDisplay() ?: o.optString("name").ifEmpty { nummer },
                photoUri = kontakt?.photoUri.orEmpty(),
                startTS = o.optLong("beginn"),
                duration = o.optInt("dauer"),
                type = o.optInt("typ", Calls.INCOMING_TYPE),
                simID = -1,
                simColor = -1,
                specificNumber = "",
                specificType = "",
                isUnknownNumber = nummer.isEmpty(),
            )
        }
    }

    @Synchronized
    fun entfernen(c: Context, ids: Collection<Int>) {
        val a = lesen(c)
        val neu = JSONArray()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (o.optInt("id") !in ids) neu.put(o)
        }
        prefs(c).edit().putString(LISTE, neu.toString()).apply()
    }

    /** Rueckgaengig machen (Loeschen in der Liste) bzw. Import. */
    @Synchronized
    fun wiederherstellen(c: Context, anrufe: List<RecentCall>) {
        anrufe.sortedBy { it.startTS }.forEach {
            eintragen(c, it.phoneNumber, it.name, it.startTS, it.duration, it.type)
        }
    }

    @Synchronized
    fun leeren(c: Context) {
        prefs(c).edit().remove(LISTE).apply()
    }
}
