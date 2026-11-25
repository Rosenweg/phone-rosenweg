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
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Rosenweg Telefon auf dem eigenen Handy (Stefan 25.11.2026): Anmeldung mit dem
 * Rosenweg-Konto statt SIP-Daten von Hand.
 *
 * 1. Browser mit der Authentik-Anmeldung (Anwendung "rosenweg-apps", oeffentlicher Client,
 *    PKCE S256), Ruecksprung ch.rosenweg4303.phone:/anmeldung -> AnmeldungActivity.
 * 2. Code gegen ein Access-Token tauschen (nur einmal gebraucht, wird nicht gespeichert).
 * 3. Mit dem Token bei der Anlage einen festen Zugang <nummer>-p<id> holen
 *    (POST /api/telefon/privat/anmelden) und wie ein persoenliches Konto registrieren.
 * Abmelden: POST /api/telefon/privat/abmelden mit dem Zugang selbst.
 *
 * Auf geteilten Telefonen (Rosenweg Geraet) gibt es das nicht - dort kommt der Zugang
 * aus der Sitzung. Nie Token, Code oder Passwort loggen.
 */
object RosenwegAnmeldung {
    private const val TAG = "RosenwegAnmeldung"
    private const val AUTHENTIK = "https://authentik.rosenweg4303.ch/application/o"
    private const val CLIENT_ID = "rosenweg-apps"
    private const val ANLAGE = "https://pbx.rosenweg4303.ch"
    const val RUECKSPRUNG = "ch.rosenweg4303.phone:/anmeldung"

    private const val PREFS = "rosenweg_anmeldung"
    private const val P_VERIFIER = "verifier"
    private const val P_STATE = "state"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun zufall(bytes: Int): String {
        val b = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Schritt 1: Anmeldeseite im Browser oeffnen. */
    fun starten(a: Activity) {
        val verifier = zufall(48)
        val state = zufall(24)
        prefs(a).edit().putString(P_VERIFIER, verifier).putString(P_STATE, state).commit()
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val url = "$AUTHENTIK/authorize/?response_type=code&client_id=$CLIENT_ID" +
            "&redirect_uri=${enc(RUECKSPRUNG)}&scope=${enc("openid email profile")}" +
            "&state=$state&code_challenge=$challenge&code_challenge_method=S256"
        a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    class Fehler(meldung: String) : Exception(meldung)

    /** Schritt 2+3 (blockierend, nicht im UI-Thread). Gibt die persoenliche Nummer zurueck. */
    fun abschliessen(c: Context, antwort: Uri): String {
        val p = prefs(c)
        val verifier = p.getString(P_VERIFIER, null)
        val state = p.getString(P_STATE, null)
        p.edit().clear().commit()   // nur einmal verwendbar
        antwort.getQueryParameter("error")?.let { throw Fehler("Anmeldung abgebrochen") }
        val code = antwort.getQueryParameter("code") ?: throw Fehler("Anmeldung abgebrochen")
        if (verifier == null || state == null || antwort.getQueryParameter("state") != state) {
            throw Fehler("Anmeldung abgelaufen, bitte nochmals")
        }
        val token = post(
            "$AUTHENTIK/token/",
            "application/x-www-form-urlencoded",
            "grant_type=authorization_code&code=${enc(code)}&redirect_uri=${enc(RUECKSPRUNG)}" +
                "&client_id=$CLIENT_ID&code_verifier=${enc(verifier)}",
            null
        ).let { (status, text) ->
            if (status != 200) throw Fehler("Rosenweg-Anmeldung fehlgeschlagen ($status)")
            JSONObject(text).optString("access_token").ifEmpty { throw Fehler("Rosenweg-Anmeldung fehlgeschlagen") }
        }
        val geraet = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.distinct().joinToString(" ")
        val (status, text) = post(
            "$ANLAGE/api/telefon/privat/anmelden", "application/json",
            JSONObject().put("geraet", geraet).toString(), token
        )
        val j = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
        if (status != 200) throw Fehler(j.optString("error").ifEmpty { "Anlage nicht erreichbar ($status)" })
        PersoenlichesKonto.privatUebernehmen(c, j)
        Log.i(TAG, "angemeldet (eigenes Handy)")
        return j.optString("nummer")
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
