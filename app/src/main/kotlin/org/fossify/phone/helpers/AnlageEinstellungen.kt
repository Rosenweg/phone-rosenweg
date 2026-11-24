package org.fossify.phone.helpers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Rosenweg (Regel 7): die Einstellungen von Rosenweg Telefon gehoeren der Person
 * und liegen auf der Telefonanlage (/api/telefon/einstellungen).
 *
 * Der Weg fuehrt ueber die Geraete-App (AnlageProvider.call, nur Rosenweg-Signatur),
 * die mit der Sitzung der angemeldeten Person fragt. Ohne Geraete-App oder ohne
 * Anmeldung passiert nichts. Logik wie ch.rosenweg4303.cloud.Einstellungen:
 * - beim Start: ist die Fassung auf der Anlage neuer, wird sie uebernommen, sonst
 *   die hiesige hochgeladen (es gilt die zuletzt geaenderte Fassung);
 * - jede Aenderung geht kurz danach hoch;
 * - Geraetebezogenes bleibt auf dem Geraet.
 */
object AnlageEinstellungen {
    private const val TAG = "AnlageEinstellungen"
    private val ANLAGE = Uri.parse("content://${AnlageVerlauf.AUTHORITY}")
    private const val STAND = "rosenweg_anlage_einstellungen"
    private const val LOKAL_GEAENDERT = "lokal_geaendert"
    private const val VERZOEGERUNG_MS = 3000L

    private val hand = Handler(Looper.getMainLooper())
    private var hoerer: SharedPreferences.OnSharedPreferenceChangeListener? = null // stark halten
    @Volatile private var uebernehmeGerade = false

    private val GERAET = listOf(
        "widget", "last_", "_last", "app_run_count", "was_", "tree_uri", "sd_card", "otg",
        "internal_storage", "folder", "path", "uri", "app_id", "first_", "initial", "version",
        "sideload", "rate", "donat", "hint", "shown", "_count", "password", "pin", "token", "secret",
        "sim", "contact_source", // SIM-Zuordnung und Kontaktkonten gehoeren zum Geraet
    )

    fun istGeraetebezogen(schluessel: String): Boolean {
        val k = schluessel.lowercase()
        return GERAET.any { k.contains(it) }
    }

    /** Beim Start aufrufen. [uebernommen] laeuft auf dem Haupt-Thread (z.B. recreate()). */
    fun abgleichen(ctx: Context, uebernommen: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        val prefs = app.getSharedPreferences("Prefs", Context.MODE_PRIVATE)
        beobachten(app, prefs)
        Thread {
            try {
                if (holen(app, prefs) && uebernommen != null) hand.post(uebernommen)
            } catch (e: Exception) {
                Log.w(TAG, "Abgleich: $e")
            }
        }.start()
    }

    private fun stand(ctx: Context) = ctx.getSharedPreferences(STAND, Context.MODE_PRIVATE)

    /** null: keine Geraete-App, keine Anmeldung oder Fehler */
    private fun rufen(ctx: Context, methode: String, extras: Bundle?): Bundle? = try {
        ctx.contentResolver.call(ANLAGE, methode, ctx.packageName, extras)
            ?.takeIf { !it.containsKey("fehler") && !it.isEmpty }
    } catch (e: Exception) { // SecurityException / IllegalArgumentException ohne Geraete-App
        Log.i(TAG, "$methode: $e")
        null
    }

    private fun holen(ctx: Context, prefs: SharedPreferences): Boolean {
        val b = rufen(ctx, "einstellungen_holen", null) ?: return false
        val oben = b.getLong("geaendert", 0L)
        val lokal = stand(ctx).getLong(LOKAL_GEAENDERT, 0L)
        if (oben <= lokal || oben == 0L) {
            if (oben < lokal || oben == 0L) senden(ctx, prefs)
            return false
        }
        uebernehmen(prefs, JSONObject(b.getString("werte") ?: "{}"))
        stand(ctx).edit().putLong(LOKAL_GEAENDERT, oben).apply()
        Log.i(TAG, "Einstellungen von der Anlage uebernommen")
        return true
    }

    private fun uebernehmen(prefs: SharedPreferences, werte: JSONObject) {
        uebernehmeGerade = true
        try {
            val e = prefs.edit()
            prefs.all.keys.filter { !istGeraetebezogen(it) && !werte.has(it) }.forEach { e.remove(it) }
            for (k in werte.keys()) {
                if (istGeraetebezogen(k)) continue
                val w = werte.getJSONObject(k)
                when (w.getString("t")) {
                    "b" -> e.putBoolean(k, w.getBoolean("v"))
                    "i" -> e.putInt(k, w.getInt("v"))
                    "l" -> e.putLong(k, w.getLong("v"))
                    "f" -> e.putFloat(k, w.getDouble("v").toFloat())
                    "s" -> e.putString(k, w.getString("v"))
                    "m" -> {
                        val a = w.getJSONArray("v")
                        e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                    }
                }
            }
            e.commit()
        } finally {
            uebernehmeGerade = false
        }
    }

    private fun beobachten(ctx: Context, prefs: SharedPreferences) {
        if (hoerer != null) return
        val senden = Runnable { Thread { senden(ctx, prefs) }.start() }
        hoerer = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (uebernehmeGerade || k == null || istGeraetebezogen(k)) return@OnSharedPreferenceChangeListener
            stand(ctx).edit().putLong(LOKAL_GEAENDERT, System.currentTimeMillis()).apply()
            hand.removeCallbacks(senden)
            hand.postDelayed(senden, VERZOEGERUNG_MS)
        }
        prefs.registerOnSharedPreferenceChangeListener(hoerer)
    }

    private fun senden(ctx: Context, prefs: SharedPreferences) {
        var geaendert = stand(ctx).getLong(LOKAL_GEAENDERT, 0L)
        if (geaendert == 0L) {
            geaendert = System.currentTimeMillis()
            stand(ctx).edit().putLong(LOKAL_GEAENDERT, geaendert).apply()
        }
        val werte = JSONObject()
        for ((k, v) in prefs.all) {
            if (istGeraetebezogen(k) || v == null) continue
            werte.put(k, when (v) {
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                is String -> JSONObject().put("t", "s").put("v", v)
                is Set<*> -> JSONObject().put("t", "m").put("v", JSONArray(v.map { it.toString() }))
                else -> continue
            })
        }
        val b = rufen(ctx, "einstellungen_senden", Bundle().apply {
            putLong("geaendert", geaendert)
            putString("werte", werte.toString())
        })
        if (b == null) Log.w(TAG, "Senden fehlgeschlagen, naechste Aenderung oder naechster Start versucht es wieder")
    }
}
