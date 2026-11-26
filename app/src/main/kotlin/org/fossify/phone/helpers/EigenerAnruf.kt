package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.CallLog.Calls
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.R
import org.fossify.phone.activities.EigenerAnrufActivity
import org.fossify.phone.activities.MainActivity
import org.fossify.phone.models.AudioRoute
import org.fossify.phone.models.Events
import org.fossify.phone.receivers.EigenerAnrufAktion
import org.greenrobot.eventbus.EventBus
import org.sipdroid.sipua.UserAgent
import org.sipdroid.sipua.ui.Receiver
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Rosenweg (Stefan 26.11.2026): Rosenweg-Anrufe auf privaten Handys ganz in der App, ohne das
 * Android-Telefon (kein Telecom, kein Anrufkonto, kein InCallService, kein Anrufprotokoll).
 *
 * Der SIP-Kern meldet seine Zustaende (Receiver.AnrufBeobachter), wenn kein Anrufkonto besteht.
 * Diese Klasse macht daraus, was sonst Telecom und das Anruffenster des Systems erledigen:
 * - Klingeln (Klingeln: Klingelton, Vibrieren, Lautlos/Nicht stoeren) und Audio-Fokus
 * - Anruf-Benachrichtigung mit Vollbild (gesperrtes Handy) bzw. Annehmen/Ablehnen
 * - Gespraech: Audiomodus "Kommunikation", Hoerer/Lautsprecher/Headset/Bluetooth, Freiton
 * - Eintrag in die eigene Anrufliste (EigeneAnrufliste), Benachrichtigung "verpasst"
 *
 * Nur ein Anruf gleichzeitig (wie der SIP-Kern). Ein Mobilfunk-Anruf waehrend eines
 * Rosenweg-Gespraechs klingelt normal in der Telefon-App des Handys.
 */
@SuppressLint("StaticFieldLeak")
object EigenerAnruf : Receiver.AnrufBeobachter {
    private const val TAG = "RosenwegAnruf"
    private const val KANAL_EINGEHEND = "rosenweg_anruf_eingehend"
    private const val KANAL_LAUFEND = "rosenweg_anruf_laufend"
    private const val KANAL_VERPASST = "rosenweg_anruf_verpasst"
    private const val NOTIF_VERPASST = 4320

    const val ANNEHMEN = "ch.rosenweg4303.phone.ANNEHMEN"
    const val AUFLEGEN = "ch.rosenweg4303.phone.AUFLEGEN"

    private lateinit var app: Context
    private val haupt = Handler(Looper.getMainLooper())
    private val beobachter = CopyOnWriteArraySet<() -> Unit>()
    private var sichtbar = 0

    // Zustand des laufenden Anrufs (geschrieben im SIP-Thread, gelesen ueberall)
    @Volatile var zustand = UserAgent.UA_STATE_IDLE; private set
    @Volatile var nummer = ""; private set
    @Volatile var name = ""; private set
    @Volatile var fotoUri = ""; private set
    @Volatile var eingehend = false; private set
    @Volatile var stumm = false; private set
    /** elapsedRealtime beim Verbinden, 0 = (noch) nicht verbunden */
    @Volatile var verbundenSeit = 0L; private set
    /** waehlen() hat den Anruf angestossen, der SIP-Kern hat ihn noch nicht gemeldet. */
    @Volatile var gleichWaehlen = false; private set
    @Volatile private var beginn = 0L
    @Volatile private var abgelehnt = false
    @Volatile private var beendetDauer = -1

    private var fokus: AudioFocusRequest? = null
    private var freiton: ToneGenerator? = null

    fun einrichten(a: Application) {
        app = a.applicationContext
        Receiver.beobachter = this
        a.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { sichtbar++ }
            override fun onActivityStopped(activity: Activity) { sichtbar-- }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    fun laeuft() = zustand != UserAgent.UA_STATE_IDLE
    fun klingelt() = zustand == UserAgent.UA_STATE_INCOMING_CALL
    fun gehalten() = zustand == UserAgent.UA_STATE_HOLD
    /** Dauer des beendeten Gespraechs in Sekunden, -1 = noch nicht beendet / nie verbunden. */
    fun beendetDauer() = beendetDauer

    fun anmelden(l: () -> Unit) { beobachter.add(l) }
    fun abmelden(l: () -> Unit) { beobachter.remove(l) }
    private fun melden() = haupt.post { beobachter.forEach { it() } }

    /** Anzeige: Name, sonst Nummer, sonst "Unbekannt". */
    fun anzeige(): String = name.ifEmpty { nummer.ifEmpty { app.getString(R.string.unknown_caller) } }

    // ---------------------------------------------------------------- SIP-Kern

    override fun zustand(state: Int, caller: String?, name: String?) {
        val alt = zustand
        Log.i(TAG, "eigen: $alt -> $state")
        when (state) {
            UserAgent.UA_STATE_INCOMING_CALL, UserAgent.UA_STATE_OUTGOING_CALL -> if (alt == UserAgent.UA_STATE_IDLE) {
                val n = adresseZuNummer(caller)
                if (state == UserAgent.UA_STATE_INCOMING_CALL || nummer.isEmpty() || !PhoneNumberUtils.compare(nummer, n)) {
                    nummer = n
                    this.name = name?.takeIf { it != n }.orEmpty()
                    fotoUri = ""
                }
                eingehend = state == UserAgent.UA_STATE_INCOMING_CALL
                beginn = System.currentTimeMillis()
                verbundenSeit = 0L
                abgelehnt = false
                stumm = false
                beendetDauer = -1
            }
            UserAgent.UA_STATE_INCALL -> if (verbundenSeit == 0L) verbundenSeit = SystemClock.elapsedRealtime()
        }
        zustand = state
        gleichWaehlen = false
        haupt.post { nebenwirkungen(alt, state) }
        melden()
    }

    override fun freiton(an: Boolean) {
        haupt.post {
            if (an && zustand == UserAgent.UA_STATE_OUTGOING_CALL) {
                if (freiton == null) freiton = try {
                    ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80).apply { startTone(ToneGenerator.TONE_SUP_RINGTONE) }
                } catch (e: Exception) {
                    null
                }
            } else {
                freitonAus()
            }
        }
    }

    private fun freitonAus() {
        try { freiton?.stopTone(); freiton?.release() } catch (_: Exception) { }
        freiton = null
    }

    override fun benachrichtigung(): Notification? = if (laeuft()) baueBenachrichtigung() else null

    private fun nebenwirkungen(alt: Int, neu: Int) {
        val nm = app.getSystemService(NotificationManager::class.java)
        when (neu) {
            UserAgent.UA_STATE_INCOMING_CALL -> {
                Vollbildrecht.pruefen(app)
                if (handyBesetzt()) {
                    // Mobilfunk-/anderes Gespraech laeuft: nicht laut klingeln, nur Anklopfton
                    anklopfen()
                } else {
                    fokusHolen(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    Klingeln.start(app)
                }
                zeigen()
                // App gerade offen: Anruffenster direkt (sonst Vollbild-Benachrichtigung bzw. Banner)
                if (sichtbar > 0) oeffnen(false)
                kontaktSuchen()
            }
            UserAgent.UA_STATE_OUTGOING_CALL -> {
                gespraechsAudio()
                zeigen()
                kontaktSuchen()
            }
            UserAgent.UA_STATE_INCALL -> {
                Klingeln.stop()
                freitonAus()
                gespraechsAudio()
                zeigen()
            }
            UserAgent.UA_STATE_HOLD -> zeigen()
            UserAgent.UA_STATE_IDLE -> {
                Klingeln.stop()
                freitonAus()
                fokusWeg()
                nm?.cancel(Receiver.CALL_NOTIFICATION)
                if (alt != UserAgent.UA_STATE_IDLE) beendet()
            }
        }
    }

    private fun beendet() {
        val dauer = if (verbundenSeit > 0) ((SystemClock.elapsedRealtime() - verbundenSeit) / 1000).toInt() else 0
        beendetDauer = if (verbundenSeit > 0) dauer else -1
        val typ = when {
            !eingehend -> Calls.OUTGOING_TYPE
            verbundenSeit > 0 -> Calls.INCOMING_TYPE
            abgelehnt -> Calls.REJECTED_TYPE
            else -> Calls.MISSED_TYPE
        }
        EigeneAnrufliste.eintragen(app, nummer, name, beginn, dauer, typ)
        if (typ == Calls.MISSED_TYPE) verpasst()
        EventBus.getDefault().post(Events.RefreshCallLog)
        melden()
    }

    // ---------------------------------------------------------------- Bedienung

    /**
     * Rosenweg-Anruf aus der App (Waehlfeld, Kontakte, Anrufliste). Notrufe gehen nie ueber SIP:
     * die Telefon-App des Handys wird mit der Nummer geoeffnet. Rueckgabe false = nicht moeglich
     * (nicht angemeldet/verbunden), Meldung macht der Aufrufer.
     */
    fun waehlen(c: Context, ziel: String, anzeigename: String?): Boolean {
        if (laeuft()) {
            oeffnen(false)
            return true
        }
        val engine = Receiver.mSipdroidEngine
        if (engine == null || !engine.isRegistered) return false
        val n = waehlbar(ziel)
        nummer = n
        name = anzeigename?.takeIf { it != ziel && it != n }.orEmpty()
        fotoUri = ""
        beendetDauer = -1
        gleichWaehlen = true
        ensureBackgroundThread {
            val ok = try {
                Receiver.engine(app).call(n, false)
            } catch (e: Exception) {
                Log.w(TAG, "waehlen: ${e.javaClass.simpleName}")
                false
            }
            if (!ok) {
                gleichWaehlen = false
                melden()
                haupt.post { app.toast("Anruf nicht möglich – Rosenweg Telefon ist nicht verbunden") }
            }
        }
        c.startActivity(Intent(c, EigenerAnrufActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun istNotruf(c: Context, n: String): Boolean {
        val z = n.filter { it.isDigit() || it == '+' }
        if (z in setOf("112", "117", "118", "143", "144", "147", "911", "999", "000", "110")) return true
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                c.getSystemService(TelephonyManager::class.java)?.isEmergencyNumber(z) == true
            } else {
                @Suppress("DEPRECATION") PhoneNumberUtils.isEmergencyNumber(z)
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Telefon-App des Handys mit der Nummer oeffnen (Notruf, oder auf Wunsch per Mobilfunk). */
    fun handyTelefonOeffnen(c: Context, n: String) {
        try {
            c.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", n, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            app.toast(R.string.no_app_found)
        }
    }

    fun annehmen() {
        if (!klingelt()) return
        Klingeln.stop()
        ensureBackgroundThread {
            try {
                Receiver.engine(app).answercall()
            } catch (e: Exception) {
                Log.w(TAG, "annehmen: ${e.javaClass.simpleName}")
            }
            // jetzt (Anruffenster im Vordergrund) darf der Anrufdienst das Mikrofon nutzen
            Receiver.startCallForeground()
        }
    }

    fun auflegen() {
        if (!laeuft()) return
        if (klingelt()) abgelehnt = true
        Klingeln.stop()
        ensureBackgroundThread {
            try {
                Receiver.engine(app).rejectcall()
            } catch (e: Exception) {
                Log.w(TAG, "auflegen: ${e.javaClass.simpleName}")
            }
        }
    }

    fun haltenUmschalten() {
        if (zustand != UserAgent.UA_STATE_INCALL && zustand != UserAgent.UA_STATE_HOLD) return
        ensureBackgroundThread { Receiver.engine(app).togglehold() }
    }

    fun stummSchalten(an: Boolean) {
        stumm = an
        ensureBackgroundThread { Receiver.mSipdroidEngine?.mute(an) }
        melden()
    }

    fun taste(c: Char) {
        ensureBackgroundThread { Receiver.mSipdroidEngine?.info(c, 250) }
    }

    /** Anruffenster oeffnen (annehmen = gleich abheben, aus der Benachrichtigung). */
    fun oeffnen(annehmen: Boolean) {
        try {
            app.startActivity(startIntent(annehmen))
        } catch (e: Exception) {
            Log.w(TAG, "oeffnen: ${e.javaClass.simpleName}")
        }
    }

    private fun startIntent(annehmen: Boolean) = Intent(app, EigenerAnrufActivity::class.java)
        .setAction(if (annehmen) ANNEHMEN else Intent.ACTION_MAIN)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

    // ---------------------------------------------------------------- Audio

    /** Laeuft auf dem Handy schon ein Gespraech (Mobilfunk oder andere App)? Ohne Telecom-Recht: Audiomodus. */
    private fun handyBesetzt(): Boolean {
        val m = am?.mode ?: return false
        return m == AudioManager.MODE_IN_CALL || m == AudioManager.MODE_IN_COMMUNICATION
    }

    private fun anklopfen() {
        try {
            val t = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 60)
            t.startTone(ToneGenerator.TONE_SUP_CALL_WAITING, 1500)
            haupt.postDelayed({ try { t.release() } catch (_: Exception) { } }, 2000)
        } catch (_: Exception) {
        }
    }

    private val am get() = app.getSystemService(AudioManager::class.java)

    private fun fokusHolen(usage: Int) {
        val a = am ?: return
        fokusWeg()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(usage)
                    .setContentType(
                        if (usage == AudioAttributes.USAGE_VOICE_COMMUNICATION) AudioAttributes.CONTENT_TYPE_SPEECH
                        else AudioAttributes.CONTENT_TYPE_SONIFICATION
                    ).build()
            ).build()
        try {
            a.requestAudioFocus(req)
            fokus = req
        } catch (e: Exception) {
            Log.w(TAG, "Audio-Fokus: ${e.javaClass.simpleName}")
        }
    }

    private fun fokusWeg() {
        val f = fokus ?: return
        try { am?.abandonAudioFocusRequest(f) } catch (_: Exception) { }
        fokus = null
    }

    /** Gespraech: Kommunikationsmodus (sonst kein Hoerer/Echo-Unterdrueckung) und Fokus. */
    private fun gespraechsAudio() {
        val a = am ?: return
        if (fokus == null || a.mode != AudioManager.MODE_IN_COMMUNICATION) {
            fokusHolen(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            try { a.mode = AudioManager.MODE_IN_COMMUNICATION } catch (_: Exception) { }
        }
    }

    /** Moegliche Audiowege (Hoerer, Lautsprecher, Kabel-Headset, Bluetooth). */
    fun audiowege(): List<AudioRoute> {
        val a = am ?: return listOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER)
        val wege = linkedSetOf<AudioRoute>()
        if (Build.VERSION.SDK_INT >= 31) {
            a.availableCommunicationDevices.mapNotNullTo(wege) { weg(it) }
        } else {
            @Suppress("DEPRECATION")
            wege += if (a.isWiredHeadsetOn) AudioRoute.WIRED_HEADSET else AudioRoute.EARPIECE
            wege += AudioRoute.SPEAKER
            if (try { org.sipdroid.media.RtpStreamReceiver.isBluetoothAvailable() } catch (_: Exception) { false }) wege += AudioRoute.BLUETOOTH
        }
        if (wege.isEmpty()) wege += AudioRoute.EARPIECE
        return wege.toList()
    }

    fun audioweg(): AudioRoute {
        val a = am ?: return AudioRoute.EARPIECE
        if (Build.VERSION.SDK_INT >= 31) {
            return a.communicationDevice?.let { weg(it) } ?: AudioRoute.EARPIECE
        }
        @Suppress("DEPRECATION")
        return when {
            a.isBluetoothScoOn -> AudioRoute.BLUETOOTH
            a.isSpeakerphoneOn -> AudioRoute.SPEAKER
            a.isWiredHeadsetOn -> AudioRoute.WIRED_HEADSET
            else -> AudioRoute.EARPIECE
        }
    }

    fun audiowegWaehlen(ziel: AudioRoute) {
        val a = am ?: return
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val dev = a.availableCommunicationDevices.firstOrNull { weg(it) == ziel }
                if (dev != null) a.setCommunicationDevice(dev) else a.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                when (ziel) {
                    AudioRoute.SPEAKER -> { a.stopBluetoothSco(); a.isBluetoothScoOn = false; a.isSpeakerphoneOn = true }
                    AudioRoute.BLUETOOTH -> { a.isSpeakerphoneOn = false; a.startBluetoothSco(); a.isBluetoothScoOn = true }
                    else -> { a.stopBluetoothSco(); a.isBluetoothScoOn = false; a.isSpeakerphoneOn = false }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audioweg: ${e.javaClass.simpleName}")
        }
        melden()
    }

    private fun weg(d: AudioDeviceInfo): AudioRoute? = when (d.type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioRoute.EARPIECE
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioRoute.SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET -> AudioRoute.WIRED_HEADSET
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioRoute.BLUETOOTH
        else -> if (Build.VERSION.SDK_INT >= 31 && d.type == AudioDeviceInfo.TYPE_BLE_HEADSET) AudioRoute.BLUETOOTH else null
    }

    // ---------------------------------------------------------------- Benachrichtigungen

    private fun kanaele() {
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(KANAL_EINGEHEND) == null) {
            nm.createNotificationChannel(
                NotificationChannel(KANAL_EINGEHEND, "Eingehende Rosenweg-Anrufe", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)   // es klingelt Klingeln (beachtet Laut/Vibrieren/Lautlos)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
        if (nm.getNotificationChannel(KANAL_LAUFEND) == null) {
            nm.createNotificationChannel(
                NotificationChannel(KANAL_LAUFEND, "Laufende Rosenweg-Anrufe", NotificationManager.IMPORTANCE_LOW)
            )
        }
        if (nm.getNotificationChannel(KANAL_VERPASST) == null) {
            nm.createNotificationChannel(
                NotificationChannel(KANAL_VERPASST, "Verpasste Rosenweg-Anrufe", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun baueBenachrichtigung(): Notification {
        kanaele()
        val oeffnen = PendingIntent.getActivity(
            app, 0, startIntent(false), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val auflegen = PendingIntent.getBroadcast(
            app, 1, Intent(app, EigenerAnrufAktion::class.java).setAction(AUFLEGEN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val klingelt = klingelt()
        val b = NotificationCompat.Builder(app, if (klingelt) KANAL_EINGEHEND else KANAL_LAUFEND)
            .setSmallIcon(R.drawable.ic_phone_vector)
            .setContentTitle(anzeige())
            .setContentText(
                when (zustand) {
                    UserAgent.UA_STATE_INCOMING_CALL -> "Rosenweg-Anruf"
                    UserAgent.UA_STATE_OUTGOING_CALL -> app.getString(R.string.dialing)
                    UserAgent.UA_STATE_HOLD -> "Gehalten"
                    else -> "Rosenweg-Gespräch"
                }
            )
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(!klingelt)
            .setContentIntent(oeffnen)
        if (klingelt) {
            val annehmen = PendingIntent.getActivity(
                app, 2, startIntent(true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(oeffnen, true)
                .addAction(R.drawable.ic_phone_down_vector, app.getString(R.string.decline_call), auflegen)
                .addAction(R.drawable.ic_phone_vector, app.getString(R.string.accept_call), annehmen)
        } else {
            b.setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.ic_phone_down_vector, app.getString(R.string.end_call), auflegen)
            if (verbundenSeit > 0) {
                b.setUsesChronometer(true).setWhen(System.currentTimeMillis() - (SystemClock.elapsedRealtime() - verbundenSeit))
            }
        }
        return b.build()
    }

    private fun zeigen() {
        if (!laeuft()) return
        try {
            app.getSystemService(NotificationManager::class.java)?.notify(Receiver.CALL_NOTIFICATION, baueBenachrichtigung())
        } catch (_: SecurityException) {
            // ohne Benachrichtigungsrecht: Anruffenster nur, wenn die App offen ist
        }
    }

    private fun verpasst() {
        kanaele()
        val pi = PendingIntent.getActivity(
            app, NOTIF_VERPASST, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(app, KANAL_VERPASST)
            .setSmallIcon(R.drawable.ic_phone_vector)
            .setContentTitle("Verpasster Rosenweg-Anruf")
            .setContentText(anzeige())
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            app.getSystemService(NotificationManager::class.java)?.notify(NOTIF_VERPASST, n)
        } catch (_: SecurityException) {
        }
    }

    // ---------------------------------------------------------------- Rechte

    class FehlendesRecht(val text: String, val einstellung: Intent)

    /**
     * Was fehlt, damit ein Rosenweg-Anruf klingelt und der Anrufschirm erscheint? null = alles da.
     * Benachrichtigungen (POST_NOTIFICATIONS bzw. App-Benachrichtigungen aus, Kanal "Eingehende
     * Rosenweg-Anrufe" aus), Vollbild-Recht (Android 14+), Akku-Ausnahme.
     */
    fun fehlendesRecht(c: Context): FehlendesRecht? {
        val nm = c.getSystemService(NotificationManager::class.java)
        val paket = Uri.fromParts("package", c.packageName, null)
        val benachrichtigungen = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, c.packageName)
        val kanalAus = nm?.getNotificationChannel(KANAL_EINGEHEND)?.importance == NotificationManager.IMPORTANCE_NONE
        if (nm == null || !nm.areNotificationsEnabled() || kanalAus) {
            return FehlendesRecht(
                "Ohne Benachrichtigungen klingeln Rosenweg-Anrufe nicht. Bitte für Rosenweg Telefon erlauben.",
                benachrichtigungen
            )
        }
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            return FehlendesRecht(
                "Ohne „Vollbild-Benachrichtigungen“ erscheint ein Rosenweg-Anruf bei gesperrtem Handy nicht als Anrufschirm. Bitte erlauben.",
                Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, paket)
            )
        }
        val pm = c.getSystemService(android.os.PowerManager::class.java)
        if (pm != null && !pm.isIgnoringBatteryOptimizations(c.packageName)) {
            return FehlendesRecht(
                "Ohne Ausnahme von der Akku-Optimierung beendet Android Rosenweg Telefon – Anrufe kommen dann nicht an.",
                Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, paket)
            )
        }
        return null
    }

    // ---------------------------------------------------------------- Nummer & Kontakt

    /** "Name" <sip:310201@host;x> / sip:310201@host / 310201 -> 310201 */
    fun adresseZuNummer(caller: String?): String {
        var a = caller.orEmpty()
        if (a.contains("<sip:")) a = a.substringAfter("<sip:").substringBefore(">")
        else if (a.startsWith("sip:")) a = a.substring(4)
        a = a.substringBefore(";").substringBefore("@").trim()
        return if (a.equals("anonymous", true)) "" else a
    }

    /** Wie SipConnectionService: Rufnummern ohne Trennzeichen, Handy-/Festnetznummern als E.164. */
    private fun waehlbar(ziel: String): String {
        val roh = ziel.removePrefix("tel:").trim()
        if (roh.startsWith("sip:") || roh.contains("@")) return roh
        val tm = app.getSystemService(TelephonyManager::class.java)
        val land = listOfNotNull(tm?.networkCountryIso, tm?.simCountryIso, Locale.getDefault().country)
            .firstOrNull { it.isNotEmpty() }?.uppercase(Locale.US)
        val e164 = if (land != null) PhoneNumberUtils.formatNumberToE164(roh, land) else null
        return e164 ?: PhoneNumberUtils.stripSeparators(roh)
    }

    private fun kontaktSuchen() {
        val n = nummer
        if (n.isEmpty()) return
        try {
            ContactsHelper(app).getContacts(getAll = true, showOnlyContactsWithNumbers = true) { kontakte ->
                val k = kontakte.firstOrNull { it.doesHavePhoneNumber(n) } ?: return@getContacts
                if (nummer != n) return@getContacts
                name = k.getNameToDisplay()
                fotoUri = k.photoUri
                haupt.post { zeigen() }
                melden()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Kontakt: ${e.javaClass.simpleName}")
        }
    }
}
