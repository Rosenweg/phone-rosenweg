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
 * Privates Telefon (keine Geraete-App): "Mit Rosenweg anmelden" (RosenwegAnmeldung, fester
 * Zugang <nummer>-p<id>) oder Server, Benutzer und Passwort von Hand (Menue "SIP-Konto").
 * Dort ohne Android-Telefon (Betriebsart): kein Anrufkonto, Anrufe in der App (EigenerAnruf).
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
    /** true: der Zugang kam aus "Mit Rosenweg anmelden" (eigenes Handy). */
    private const val PRIVAT = "rosenweg_sip_privat"
    private const val PRIVAT_NUMMER = "rosenweg_sip_privat_nummer"
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
        ComponentName(ctx.packageName, "org.sipdroid.sipua.ui.SipConnectionService"), Receiver.kontoId(ctx)
    )

    /**
     * Konto fuer einen gehenden Anruf: das persoenliche, wenn es registriert ist und es
     * kein Notruf ist (Notrufe gehen nie ueber SIP, sondern ueber das Modem). Sonst null.
     */
    fun fuerAnruf(ctx: Context, nummer: String): PhoneAccountHandle? {
        if (istNotruf(ctx, nummer)) return null
        // privates Handy: kein Anrufkonto bei Telecom (Anrufe laufen ueber EigenerAnruf)
        if (!geraetDa(ctx)) return null
        return try {
            val tm = ctx.getSystemService(TelecomManager::class.java)
            val eigenes = handle(ctx)
            if (tm.callCapablePhoneAccounts.contains(eigenes)) eigenes else null
        } catch (e: SecurityException) {
            null
        }
    }

    private fun istNotruf(ctx: Context, nummer: String): Boolean = EigenerAnruf.istNotruf(ctx, nummer)

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
        // privates Handy: nur die eigene Liste - das Anrufprotokoll des Handys gehoert der Person
        if (!geraetDa(app)) {
            EigeneAnrufliste.leeren(app)
            Log.i(TAG, "eigene Anrufliste geloescht")
            return
        }
        try {
            val n = app.contentResolver.delete(android.provider.CallLog.Calls.CONTENT_URI, null, null)
            Log.i(TAG, "Anrufliste geloescht ($n)")
        } catch (e: Exception) {
            Log.w(TAG, "Anrufliste loeschen: ${e.javaClass.simpleName}")
        }
    }

    private fun abgleichenJetzt(app: Context, grund: String) {
        if (!geraetDa(app)) {
            if (prefs(app).getBoolean(PRIVAT, false)) {
                mobilfunkErlauben(app)   // auch fuer Installationen von vor v43
                privatNachfragen(app, grund)
                return
            }
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

    /**
     * Eigenes Handy (Stefan 25.11.2026): bei der Anlage nachfragen. Gibt es den Zugang nicht mehr
     * (im Web abgemeldet), hier auch abmelden; sonst die aktuellen Zugangsdaten uebernehmen (z.B.
     * der Wechsel auf SIP ueber TLS 5061 fuer unterwegs). Ohne Netz bleibt alles, wie es ist.
     */
    private fun privatNachfragen(app: Context, grund: String) {
        val j = try {
            RosenwegAnmeldung.stand(app)
        } catch (e: Exception) {
            Log.i(TAG, "Anlage nicht erreichbar ($grund): ${e.javaClass.simpleName}")
            Receiver.engine(app).register()
            return
        }
        if (j == null) {
            Log.i(TAG, "im Web abgemeldet ($grund) - Zugang vergessen")
            prefs(app).edit().putBoolean(VERWALTET, true).commit()
            vergessen(app)
            prefs(app).edit().remove(PRIVAT).remove(PRIVAT_NUMMER).commit()
            anruflisteLoeschen(app)
            return
        }
        val b = Bundle().apply {
            putBoolean("angemeldet", true)
            listOf("user", "auth_user", "password", "server", "transport").forEach { putString(it, j.optString(it)) }
            putString("name", Receiver.personName)
            putInt("port", j.optInt("port", 5060))
        }
        // uebernehmen() vergleicht nur bei VERWALTET - fuer den Vergleich kurz setzen
        prefs(app).edit().putBoolean(VERWALTET, true).commit()
        uebernehmen(app, b)
        prefs(app).edit().putBoolean(VERWALTET, false).putBoolean(PRIVAT, true)
            .putString(PRIVAT_NUMMER, j.optString("nummer").ifEmpty { prefs(app).getString(PRIVAT_NUMMER, "") }).commit()
    }

    /** Alle 30 Min. nachfragen (eigenes Handy): Web-Abmeldung und geaenderte Zugangsdaten. */
    fun nachfragenPlanen(c: Context) {
        val am = c.getSystemService(android.app.AlarmManager::class.java) ?: return
        val pi = android.app.PendingIntent.getBroadcast(
            c, 4321, android.content.Intent(c, Nachfrage::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.setInexactRepeating(
            android.app.AlarmManager.ELAPSED_REALTIME, android.os.SystemClock.elapsedRealtime() + android.app.AlarmManager.INTERVAL_HALF_HOUR,
            android.app.AlarmManager.INTERVAL_HALF_HOUR, pi
        )
    }

    class Nachfrage : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: android.content.Intent) {
            Betriebsart.anwenden(context)
            if (!geraetDa(context) && privatNummer(context) != null) abgleichen(context, "nachfrage")
        }
    }

    /** Eigenes Handy, mit dem Rosenweg-Konto angemeldet: persoenliche Nummer, sonst null. */
    fun privatNummer(ctx: Context): String? =
        prefs(ctx).takeIf { it.getBoolean(PRIVAT, false) }?.getString(PRIVAT_NUMMER, null)

    internal fun privatZugang(ctx: Context): Pair<String, String>? {
        val p = prefs(ctx)
        if (!p.getBoolean(PRIVAT, false)) return null
        val u = p.getString(Settings.PREF_USERNAME, "").orEmpty()
        val pw = p.getString(Settings.PREF_PASSWORD, "").orEmpty()
        return if (u.isNotEmpty() && pw.isNotEmpty()) u to pw else null
    }

    /** Zugang von /api/telefon/privat/anmelden uebernehmen und registrieren (blockierend ok). */
    internal fun privatUebernehmen(ctx: Context, j: org.json.JSONObject) {
        val app = ctx.applicationContext
        val b = Bundle().apply {
            putBoolean("angemeldet", true)
            listOf("user", "auth_user", "password", "server", "transport", "name").forEach { putString(it, j.optString(it)) }
            putInt("port", j.optInt("port", 5060))
        }
        arbeit.submit {
            mobilfunkErlauben(app)
            uebernehmen(app, b)
            prefs(app).edit().putBoolean(VERWALTET, false).putBoolean(PRIVAT, true)
                .putString(PRIVAT_NUMMER, j.optString("nummer")).commit()
        }.get()
    }

    internal fun privatVergessen(ctx: Context) {
        val app = ctx.applicationContext
        arbeit.submit {
            // vergessen() raeumt Server/Port nur bei VERWALTET weg - der Zugang war ganz von uns
            prefs(app).edit().putBoolean(VERWALTET, true).commit()
            vergessen(app)
            prefs(app).edit().remove(PRIVAT).remove(PRIVAT_NUMMER).commit()
        }.get()
    }

    /** Privat: SIP-Zugang von Hand eingerichtet (Menue "SIP-Konto von Hand")? */
    fun manuellDa(ctx: Context): Boolean = manuellEingerichtet(ctx.applicationContext)

    /**
     * Eigenes Handy (Stefan 26.11.2026): auch ueber Mobilfunk erreichbar sein. Der Sipdroid-Kern
     * registriert ausserhalb des WLAN nur, wenn 3G/4G (5G zaehlt als 4G) bzw. VPN in seinen
     * Einstellungen an sind (Standard: aus) - sonst "isFast = false (CELLULAR)" und Abmeldung.
     * Nur fuer den Zugang aus "Mit Rosenweg anmelden"; geteilte Telefone und ein von Hand
     * eingerichtetes SIP-Konto bleiben, wie sie sind.
     */
    private fun mobilfunkErlauben(app: Context) {
        val p = prefs(app)
        val schluessel = listOf(Settings.PREF_3G, Settings.PREF_4G, Settings.PREF_VPN)
        if (schluessel.all { p.getBoolean(it, false) }) return
        p.edit().apply { schluessel.forEach { putBoolean(it, true) } }.commit()
        Log.i(TAG, "Mobilfunk/VPN fuer das eigene Handy eingeschaltet")
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
        // Geteiltes Telefon: Konto bleibt bei Telecom eingetragen (sonst ginge die Freigabe per
        // adb verloren), aber ohne CALL_PROVIDER: nicht waehlbar. Privat: gibt es keins.
        Receiver.updatePhoneAccount(app)
        if (hatte) Log.i(TAG, "persoenliches Konto abgemeldet und vergessen")
    }
}
