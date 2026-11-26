package org.fossify.phone.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Rosenweg Telefon auf dem eigenen Handy (Stefan 25.11.2026): Anmeldung mit dem
 * Rosenweg-Konto statt SIP-Daten von Hand.
 *
 * Seit v47 (Stefan 26.11.2026): Die Anmeldung selbst kommt aus commons-rosenweg (cloud), mit
 * dem gemeinsamen Anmeldeschirm aller Rosenweg-Apps. Er sieht aus wie der Sperrbildschirm der
 * geteilten Telefone: Rosenweg-Karte hinten ans Telefon halten oder "Mit Rosenweg-Konto
 * anmelden". Danach holt [zugangHolen] mit dem Zugang der Anmeldung (Authentik-Token oder
 * Karten-Sitzung) bei der Anlage einen festen SIP-Zugang <nummer>-p<id>
 * (POST /api/telefon/privat/anmelden) und richtet ihn wie ein persoenliches Konto ein.
 * Abmelden: POST /api/telefon/privat/abmelden mit dem Zugang selbst, dazu die Anmeldung in
 * commons.
 *
 * Auf geteilten Telefonen (Rosenweg Geraet) gibt es das nicht - dort kommt der Zugang
 * aus der Sitzung. Nie Token, Code oder Passwort loggen.
 */
object RosenwegAnmeldung {
    private const val TAG = "RosenwegAnmeldung"
    private const val ANLAGE = "https://pbx.rosenweg4303.ch"

    class Fehler(meldung: String) : Exception(meldung)

    /**
     * Eigenes Handy ohne SIP-Zugang: muss der Anmeldeschirm (commons) kommen? Nicht, wenn ein
     * Zugang von Hand eingerichtet ist.
     */
    fun anmeldungNoetig(c: Context): Boolean =
        !PersoenlichesKonto.geraetDa(c) && PersoenlichesKonto.privatNummer(c) == null &&
            !PersoenlichesKonto.manuellDa(c) && ch.rosenweg4303.cloud.RosenwegAnmeldung.anmeldungNoetig(c)

    /** Angemeldet (commons), aber noch kein SIP-Zugang: jetzt holen. */
    fun zugangFehlt(c: Context): Boolean =
        !PersoenlichesKonto.geraetDa(c) && PersoenlichesKonto.privatNummer(c) == null &&
            !PersoenlichesKonto.manuellDa(c) && ch.rosenweg4303.cloud.RosenwegAnmeldung.angemeldet(c)

    /** SIP-Zugang bei der Anlage holen (blockierend, nicht im UI-Thread). Gibt die Nummer zurueck. */
    fun zugangHolen(c: Context): String {
        val token = try {
            ch.rosenweg4303.cloud.RosenwegAnmeldung.zugangsToken(c)
        } catch (e: java.io.IOException) {
            throw Fehler("Keine Verbindung zum Rosenweg")
        } ?: throw Fehler("Anmeldung abgelaufen, bitte neu anmelden")
        val geraet = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.distinct().joinToString(" ")
        val (status, text) = post(
            "$ANLAGE/api/telefon/privat/anmelden", "application/json",
            JSONObject().put("geraet", geraet).toString(), token
        )
        val j = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
        if (status == 401) ch.rosenweg4303.cloud.RosenwegAnmeldung.zugangUngueltig()
        if (status != 200) throw Fehler(j.optString("error").ifEmpty { "Anlage nicht erreichbar ($status)" })
        PersoenlichesKonto.privatUebernehmen(c, j)
        Log.i(TAG, "angemeldet (eigenes Handy)")
        return j.optString("nummer")
    }

    /**
     * Stand des eigenen Zugangs an der Anlage (blockierend): aktuelle Zugangsdaten, null = Zugang
     * gibt es nicht mehr (im Web auf /smartphones abgemeldet). Wirft bei Netzfehlern.
     */
    fun stand(c: Context): JSONObject? {
        val zugang = PersoenlichesKonto.privatZugang(c) ?: return null
        val (status, text) = post(
            "$ANLAGE/api/telefon/privat/stand", "application/json",
            JSONObject().put("user", zugang.first).put("password", zugang.second).toString(), null
        )
        return when (status) {
            200 -> JSONObject(text)
            401 -> null
            else -> throw java.io.IOException("Anlage: $status")
        }
    }

    /** Abmelden an der Anlage (blockierend); vergisst den Zugang auch ohne Netz. */
    fun abmelden(c: Context) {
        val zugang = PersoenlichesKonto.privatZugang(c)
        if (zugang != null) {
            try {
                val (status, _) = post(
                    "$ANLAGE/api/telefon/privat/abmelden", "application/json",
                    JSONObject().put("user", zugang.first).put("password", zugang.second).toString(), null
                )
                Log.i(TAG, "abgemeldet an der Anlage ($status)")
            } catch (e: Exception) {
                Log.w(TAG, "Abmelden an der Anlage: ${e.javaClass.simpleName}")
            }
        }
        PersoenlichesKonto.privatVergessen(c)
        ch.rosenweg4303.cloud.RosenwegAnmeldung.abmelden(c)   // Anmeldung dieser App (Browser oder Karte)
        // Grundregel: beim Abmelden die lokale Anrufliste (Rosenweg-Anrufe) loeschen
        EigeneAnrufliste.leeren(c)
        AnlageVerlauf.privatVergessen()
    }

    /**
     * Eigenes Handy: ohne Akku-Ausnahme stoppt Android den Anmeldedienst nach ein paar Minuten
     * im Hintergrund ("app idle") und laesst ihn nicht wieder starten - dann klingelt nichts.
     * Fragt mit dem System-Dialog nach, solange die Ausnahme fehlt (nur angemeldet, privat).
     */
    @android.annotation.SuppressLint("BatteryLife")
    fun akkuAusnahmeErfragen(a: Activity): Boolean {
        if (PersoenlichesKonto.privatNummer(a) == null) return false
        val pm = a.getSystemService(android.os.PowerManager::class.java) ?: return false
        if (pm.isIgnoringBatteryOptimizations(a.packageName)) return false
        return try {
            a.startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + a.packageName))
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Akku-Ausnahme: ${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * Verlauf der Anlage fuer das eigene Handy (blockierend): alle Anrufe der Person, auch an die
     * persoenliche Nummer ueber andere Geraete, der letzten [tage] Tage. Ausweis = der eigene
     * Zugang (HTTP-Basic). null = nicht angemeldet/Netzfehler/401 (dann nur die lokale Liste).
     */
    fun anlageVerlauf(c: Context, tage: Int = 90, limit: Int = 500): org.json.JSONArray? {
        val zugang = PersoenlichesKonto.privatZugang(c) ?: return null
        val v = URL("$ANLAGE/api/telefon/anrufliste?tage=$tage&limit=$limit").openConnection() as HttpURLConnection
        return try {
            v.connectTimeout = 8000
            v.readTimeout = 12000
            v.setRequestProperty("Accept", "application/json")
            val basic = Base64.encodeToString("${zugang.first}:${zugang.second}".toByteArray(), Base64.NO_WRAP)
            v.setRequestProperty("Authorization", "Basic $basic")
            if (v.responseCode != 200) {
                Log.i(TAG, "Verlauf der Anlage: ${v.responseCode}")
                null
            } else {
                JSONObject(v.inputStream.bufferedReader().use { it.readText() }).optJSONArray("anrufe")
            }
        } catch (e: Exception) {
            Log.i(TAG, "Verlauf der Anlage: ${e.javaClass.simpleName}")
            null
        } finally {
            v.disconnect()
        }
    }

    private fun post(url: String, typ: String, body: String, bearer: String?): Pair<Int, String> {
        val v = URL(url).openConnection() as HttpURLConnection
        try {
            v.requestMethod = "POST"
            v.connectTimeout = 10000
            v.readTimeout = 15000
            v.doOutput = true
            v.setRequestProperty("Content-Type", typ)
            v.setRequestProperty("Accept", "application/json")
            if (bearer != null) v.setRequestProperty("Authorization", "Bearer $bearer")
            v.outputStream.use { it.write(body.toByteArray()) }
            val status = v.responseCode
            val text = (if (status < 400) v.inputStream else v.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return status to text
        } finally {
            v.disconnect()
        }
    }
}
