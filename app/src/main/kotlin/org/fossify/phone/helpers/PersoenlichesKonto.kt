package org.fossify.phone.helpers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import org.sipdroid.sipua.ui.OneShotAlarm
import org.sipdroid.sipua.ui.Receiver
import org.sipdroid.sipua.ui.RegisterService
import org.sipdroid.sipua.ui.Settings
import java.util.concurrent.Executors

/**
 * Rosenweg (Stefan 25.11.2026): persoenliches SIP-Konto der angemeldeten Person.
 *
 * Rosenweg Telefon registriert mit dem Sipdroid-Kern (Modul :sipdroid) den persoenlichen
 * Zugang <nummer>-<geraet> (z.B. 310201-2102). Die Nebenstelle des Geraets (2102,
 * "Telefon suchen", Sperrbildschirm) bleibt in Rosenweg Geraet.
 *
 * Geteiltes Telefon (Rosenweg Geraet installiert): den Zugang liefert die Geraete-App
 * (AnlageProvider.call("sip_person"), nur Rosenweg-Signatur). Er gilt nur fuer die
 * Sitzung: beim Abmelden meldet sich Rosenweg Telefon ab und vergisst ihn. Kommt kein
 * Zugang (niemand angemeldet, alte Geraete-App, keine persoenliche Nummer), wird nicht
 * registriert.
 *
 * Privates Telefon (keine Geraete-App): Server, Benutzer und Passwort von Hand in den
 * Sipdroid-Einstellungen (Menue "SIP-Konto").
 *
 * Nie den Zugang loggen.
 */
object PersoenlichesKonto {
    private const val TAG = "RosenwegSip"
    const val SIP_PERSON_GEAENDERT = "ch.rosenweg4303.SIP_PERSON_GEAENDERT"
    const val ANGEMELDET = "ch.rosenweg4303.ANGEMELDET"
    const val ABGEMELDET = "ch.rosenweg4303.ABGEMELDET"

    /** true: der Zugang in den Sipdroid-Einstellungen kam von der Geraete-App (Sitzung). */
    private const val VERWALTET = "rosenweg_sip_verwaltet"
    private const val KONTO_ID = "SipdroidCallProvider"
    private val ANLAGE = Uri.parse("content://${AnlageVerlauf.AUTHORITY}")
    private val arbeit = Executors.newSingleThreadExecutor()

    /** Dieselbe Datei wie PreferenceManager.getDefaultSharedPreferences (liest der Sipdroid-Kern). */
    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(ctx.packageName + "_preferences", Context.MODE_PRIVATE)

    /** Rosenweg Geraet installiert (geteiltes Telefon)? */
    fun geraetDa(ctx: Context): Boolean = try {
        ctx.packageManager.resolveContentProvider(AnlageVerlauf.AUTHORITY, 0) != null
    } catch (e: Exception) {
        false
    }

    /** Anrufkonto dieser App (Telecom), auch wenn es gerade offline ist. */
    fun handle(ctx: Context) = PhoneAccountHandle(
        ComponentName(ctx.packageName, "org.sipdroid.sipua.ui.SipConnectionService"), KONTO_ID
    )

    /**
     * Konto fuer einen gehenden Anruf: das persoenliche, wenn es registriert ist und es
     * kein Notruf ist (Notrufe gehen nie ueber SIP, sondern ueber das Modem). Sonst null.
     */
    fun fuerAnruf(ctx: Context, nummer: String): PhoneAccountHandle? {
        if (istNotruf(ctx, nummer)) return null
        return try {
            val tm = ctx.getSystemService(TelecomManager::class.java)
            val eigenes = handle(ctx)
            if (tm.callCapablePhoneAccounts.contains(eigenes)) eigenes else null
        } catch (e: SecurityException) {
            null
        }
    }

    private val NOTRUFE = setOf("112", "117", "118", "143", "144", "147", "911", "999", "000", "110")

    private fun istNotruf(ctx: Context, nummer: String): Boolean {
        val n = nummer.filter { it.isDigit() || it == '+' }
        if (n in NOTRUFE) return true
        return try {
            ctx.getSystemService(TelephonyManager::class.java).isEmergencyNumber(n)
        } catch (e: Exception) {
            false
        }
    }

    /** Beim Start der App, nach An-/Abmelden und wenn die Geraete-App einen Wechsel meldet. */
    fun abgleichen(ctx: Context, grund: String) {
        val app = ctx.applicationContext
        arbeit.execute {
            try {
                abgleichenJetzt(app, grund)
            } catch (e: Exception) {
                Log.w(TAG, "Abgleich ($grund): ${e.javaClass.simpleName}")
            }
        }
    }

    /** Abgemeldet: sofort abmelden und vergessen (ohne die Geraete-App zu fragen). */
    fun abgemeldet(ctx: Context) {
        val app = ctx.applicationContext
        arbeit.execute {
            try {
                if (geraetDa(app) || prefs(app).getBoolean(VERWALTET, false)) vergessen(app)
                // Geteiltes Telefon: die Anrufliste gehoert der Person, die sich abmeldet
                if (geraetDa(app)) anruflisteLoeschen(app)
            } catch (e: Exception) {
                Log.w(TAG, "Abmelden: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun anruflisteLoeschen(app: Context) {
        try {
            val n = app.contentResolver.delete(android.provider.CallLog.Calls.CONTENT_URI, null, null)
            Log.i(TAG, "Anrufliste geloescht ($n)")
        } catch (e: Exception) {
            Log.w(TAG, "Anrufliste loeschen: ${e.javaClass.simpleName}")
        }
    }

    private fun abgleichenJetzt(app: Context, grund: String) {
        if (!geraetDa(app)) {
            // Privates Telefon: ein Zugang aus einer frueheren Sitzung gilt nicht mehr
            if (prefs(app).getBoolean(VERWALTET, false)) {
                vergessen(app)
            } else if (manuellEingerichtet(app)) {
                Receiver.engine(app).register()
            }
            return
        }
        val b: Bundle? = try {
            app.contentResolver.call(ANLAGE, "sip_person", app.packageName, null)
        } catch (e: Exception) { // SecurityException: andere Signatur
            Log.i(TAG, "sip_person ($grund): ${e.javaClass.simpleName}")
            null
        }
        when {
            b == null -> vergessen(app)
            b.containsKey("fehler") -> Log.i(TAG, "Anlage nicht erreichbar ($grund), Zustand bleibt")
            // leer = alte Geraete-App ohne sip_person; false = niemand angemeldet / keine Nummer
            !b.getBoolean("angemeldet", false) || b.getString("password").isNullOrEmpty() -> vergessen(app)
            else -> uebernehmen(app, b)
        }
    }

    private fun manuellEingerichtet(app: Context): Boolean {
        val p = prefs(app)
        return p.getString(Settings.PREF_USERNAME, "").orEmpty().isNotEmpty() &&
            p.getString(Settings.PREF_SERVER, "").orEmpty().isNotEmpty()
    }

    private fun uebernehmen(app: Context, b: Bundle) {
        val p = prefs(app)
        val neu = linkedMapOf(
            Settings.PREF_USERNAME to b.getString("auth_user").orEmpty().ifEmpty { b.getString("user").orEmpty() },
            Settings.PREF_PASSWORD to b.getString("password").orEmpty(),
            Settings.PREF_SERVER to b.getString("server").orEmpty(),
            Settings.PREF_PORT to b.getInt("port", 5060).toString(),
            Settings.PREF_PROTOCOL to b.getString("transport").orEmpty().ifEmpty { "tcp" },
        )
        if (neu.getValue(Settings.PREF_USERNAME).isEmpty() || neu.getValue(Settings.PREF_SERVER).isEmpty()) {
            vergessen(app)
            return
        }
        Receiver.personName = b.getString("name")
        val gleich = p.getBoolean(VERWALTET, false) && neu.all { (k, v) -> p.getString(k, "") == v }
        if (gleich) {
            Receiver.engine(app).register()
        } else {
            p.edit().apply {
                neu.forEach { (k, v) -> putString(k, v) }
                putString(Settings.PREF_DOMAIN, "")
                putString(Settings.PREF_FROMUSER, "")
                putBoolean(Settings.PREF_ENABLED, true)
                putBoolean(Settings.PREF_WLAN, true)
                remove(Settings.PREF_DNS + "0")
                putBoolean(VERWALTET, true)
            }.commit()
            // wie nach einer Aenderung in den Sipdroid-Einstellungen
            val e = Receiver.engine(app)
            e.halt()
            e.StartEngine()
            e.updateDNS()
            Log.i(TAG, "persoenliches Konto uebernommen")
        }
        Receiver.updatePhoneAccount(app)
    }

    private fun vergessen(app: Context) {
        val p = prefs(app)
        Receiver.personName = null
        val hatte = p.getString(Settings.PREF_USERNAME, "").orEmpty().isNotEmpty()
        try {
            Receiver.mSipdroidEngine?.halt()   // abmelden (der Zugang ist auf der Anlage meist schon weg)
        } catch (e: Exception) {
            Log.w(TAG, "halt: ${e.javaClass.simpleName}")
        }
        p.edit().apply {
            remove(Settings.PREF_USERNAME)
            remove(Settings.PREF_PASSWORD)
            remove(Settings.PREF_DNS + "0")
            if (p.getBoolean(VERWALTET, false)) {
                remove(Settings.PREF_SERVER)
                remove(Settings.PREF_PORT)
                remove(Settings.PREF_PROTOCOL)
            }
            putBoolean(VERWALTET, false)
        }.commit()
        try {
            if (Receiver.mContext == null) Receiver.mContext = app
            Receiver.alarm(0, OneShotAlarm::class.java)
            app.stopService(Intent(app, RegisterService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Dienst stoppen: ${e.javaClass.simpleName}")
        }
        // Konto bleibt bei Telecom eingetragen (sonst ginge die Freigabe per adb verloren),
        // aber ohne CALL_PROVIDER: nicht waehlbar.
        Receiver.updatePhoneAccount(app)
        if (hatte) Log.i(TAG, "persoenliches Konto abgemeldet und vergessen")
    }
}
