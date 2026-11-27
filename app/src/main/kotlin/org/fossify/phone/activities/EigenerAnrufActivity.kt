package org.fossify.phone.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.setPadding
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.LOWER_ALPHA_INT
import org.fossify.commons.helpers.MINUTE_SECONDS
import org.fossify.commons.helpers.PERMISSION_RECORD_AUDIO
import org.fossify.commons.models.SimpleListItem
import org.fossify.phone.R
import org.fossify.phone.databinding.ActivityCallBinding
import org.fossify.phone.dialogs.DynamicBottomSheetChooserDialog
import org.fossify.phone.extensions.addCharacter
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.disableKeyboard
import org.fossify.phone.extensions.getKeyEvent
import org.fossify.phone.helpers.EigenerAnruf
import org.fossify.phone.models.AudioRoute
import org.sipdroid.sipua.UserAgent

/**
 * Rosenweg (26.11.2026): Anruffenster fuer Rosenweg-Anrufe auf privaten Handys (EigenerAnruf,
 * ohne Telecom). Gleiches Aussehen wie das Anruffenster der geteilten Telefone (activity_call),
 * aber bewusst einfach: Annehmen/Ablehnen per Antippen, im Gespraech Stumm, Lautsprecher bzw.
 * Audioweg (Bluetooth), Tastenfeld, Halten und Auflegen. Keine Konferenz/zweiter Anruf.
 */
class EigenerAnrufActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityCallBinding::inflate)
    private val takt = Handler(Looper.getMainLooper())
    private var proximity: PowerManager.WakeLock? = null
    private var beendetAngezeigt = false
    private var wegeDialog: DynamicBottomSheetChooserDialog? = null
    private var fotoGeladen: String? = null

    private val aenderung: () -> Unit = { aktualisieren() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        if (!EigenerAnruf.laeuft() && !EigenerAnruf.gleichWaehlen) {
            finish()
            return
        }
        setupEdgeToEdge(padTopSystem = listOf(binding.callHolder), padBottomSystem = listOf(binding.callHolder))
        updateTextColors(binding.callHolder)
        sperrbildschirm()
        knoepfe()
        EigenerAnruf.anmelden(aenderung)
        aktualisieren()
        aktion(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        aktion(intent)
        aktualisieren()
    }

    override fun onDestroy() {
        super.onDestroy()
        EigenerAnruf.abmelden(aenderung)
        takt.removeCallbacksAndMessages(null)
        naeheAus()
    }

    /** Aus der Benachrichtigung "Annehmen": gleich abheben. */
    private fun aktion(i: Intent?) {
        if (i?.action == EigenerAnruf.ANNEHMEN) {
            i.action = Intent.ACTION_MAIN
            annehmen()
        }
    }

    private fun annehmen() {
        handlePermission(PERMISSION_RECORD_AUDIO) { erlaubt ->
            if (!erlaubt) toast("Ohne Mikrofon-Recht hört dich niemand")
            EigenerAnruf.annehmen()
        }
    }

    private fun knoepfe() = binding.apply {
        // einfach: Antippen statt Wischen
        arrayOf(callDraggable, callDraggableBackground, callLeftArrow, callRightArrow).forEach { it.beGone() }
        callDecline.setOnClickListener { EigenerAnruf.auflegen() }
        callAccept.setOnClickListener { annehmen() }
        callEnd.setOnClickListener {
            if (EigenerAnruf.laeuft()) EigenerAnruf.auflegen() else if (!EigenerAnruf.gleichWaehlen) finish()
        }
        callToggleMicrophone.setOnClickListener { EigenerAnruf.stummSchalten(!EigenerAnruf.stumm) }
        callToggleSpeaker.setOnClickListener { audiowegWechseln() }
        callDialpad.setOnClickListener { dialpadWrapper.beVisibleIf(!dialpadWrapper.isVisible()) }
        dialpadClose.setOnClickListener { dialpadWrapper.beGone() }
        callToggleHold.setOnClickListener { EigenerAnruf.haltenUmschalten() }

        // nur ein Anruf: kein zweiter Anruf, Makeln, Konferenz
        controlsTwoCalls.beGone()
        controlsSingleCall.referencedIds = intArrayOf(R.id.call_toggle_hold)
        arrayOf(callAdd, callSwap, callMerge, callManage).forEach { it.beGone() }

        dialpadInclude.apply {
            val tasten = mapOf(
                dialpad0Holder to '0', dialpad1Holder to '1', dialpad2Holder to '2', dialpad3Holder to '3',
                dialpad4Holder to '4', dialpad5Holder to '5', dialpad6Holder to '6', dialpad7Holder to '7',
                dialpad8Holder to '8', dialpad9Holder to '9', dialpadAsteriskHolder to '*', dialpadHashtagHolder to '#'
            )
            tasten.forEach { (v, c) ->
                v.setOnClickListener { taste(c) }
                v.background = ResourcesCompat.getDrawable(resources, R.drawable.pill_background, theme)
                v.background?.alpha = LOWER_ALPHA_INT
            }
            dialpadPlusHolder.beGone()
        }
        dialpadClearChar.setOnClickListener {
            dialpadInput.dispatchKeyEvent(dialpadInput.getKeyEvent(KeyEvent.KEYCODE_DEL))
        }
        dialpadInput.disableKeyboard()
        dialpadWrapper.setBackgroundColor(
            if (isSystemInDarkMode()) getProperBackgroundColor().lightenColor(2) else getProperBackgroundColor()
        )
        arrayOf(dialpadClose, dialpadClearChar).forEach { it.applyColorFilter(getProperTextColor()) }
        dialpadWrapper.beGone()

        val bg = getProperBackgroundColor()
        arrayOf(callToggleMicrophone, callToggleSpeaker, callDialpad, callToggleHold).forEach {
            it.applyColorFilter(bg.getContrastColor())
            it.background.applyColorFilter(getProperTextColor().adjustAlpha(0.10f))
            it.setOnLongClickListener { v ->
                if (!v.contentDescription.isNullOrEmpty()) toast(v.contentDescription.toString())
                true
            }
        }
    }

    private fun taste(c: Char) {
        EigenerAnruf.taste(c)
        binding.dialpadInput.addCharacter(c)
    }

    private fun aktualisieren() = binding.apply {
        val z = EigenerAnruf.zustand
        callerNameLabel.text = EigenerAnruf.anzeige()
        val nummer = EigenerAnruf.nummer
        callerNumber.text = nummer
        callerNumber.beVisibleIf(nummer.isNotEmpty() && nummer != EigenerAnruf.anzeige())
        foto(EigenerAnruf.fotoUri)

        if (z == UserAgent.UA_STATE_IDLE && !EigenerAnruf.gleichWaehlen) {
            beendet()
            return@apply
        }
        if (beendetAngezeigt) {
            // naechster Anruf, waehrend das Fenster noch "beendet" zeigte
            beendetAngezeigt = false
            takt.removeCallbacksAndMessages(null)
            listOf(callToggleMicrophone, callToggleSpeaker, callEnd).forEach { aktiv(it, true) }
        }
        val klingelt = z == UserAgent.UA_STATE_INCOMING_CALL
        incomingCallHolder.beVisibleIf(klingelt)
        ongoingCallHolder.beVisibleIf(!klingelt)
        callEnd.beVisibleIf(!klingelt)
        holdStatusLabel.beInvisibleIf(z != UserAgent.UA_STATE_HOLD)
        onHoldStatusHolder.beGone()
        controlsSingleCall.beVisible()

        val verbunden = z == UserAgent.UA_STATE_INCALL || z == UserAgent.UA_STATE_HOLD
        aktiv(callToggleHold, verbunden)
        aktiv(callDialpad, verbunden)
        farbe(callToggleHold, z == UserAgent.UA_STATE_HOLD)
        callToggleHold.contentDescription = getString(if (z == UserAgent.UA_STATE_HOLD) R.string.resume_call else R.string.hold_call)
        farbe(callToggleMicrophone, EigenerAnruf.stumm)
        callToggleMicrophone.contentDescription =
            getString(if (EigenerAnruf.stumm) R.string.turn_microphone_on else R.string.turn_microphone_off)
        audiowegAnzeigen()

        when (z) {
            UserAgent.UA_STATE_INCOMING_CALL -> callStatusLabel.text = getString(R.string.is_calling)
            UserAgent.UA_STATE_OUTGOING_CALL, UserAgent.UA_STATE_IDLE -> callStatusLabel.text = getString(R.string.dialing)
            else -> dauerLaufen()
        }
        if (klingelt) naeheAus() else naeheAn()
    }

    private fun dauerLaufen() {
        takt.removeCallbacksAndMessages(null)
        val tick = object : Runnable {
            override fun run() {
                val seit = EigenerAnruf.verbundenSeit
                if (!EigenerAnruf.laeuft() || seit == 0L) return
                binding.callStatusLabel.text = ((SystemClock.elapsedRealtime() - seit) / 1000).toInt().getFormattedDuration()
                takt.postDelayed(this, 1000)
            }
        }
        tick.run()
    }

    private fun beendet() {
        if (beendetAngezeigt) return
        beendetAngezeigt = true
        takt.removeCallbacksAndMessages(null)
        naeheAus()
        wegeDialog?.dismissAllowingStateLoss()
        binding.incomingCallHolder.beGone()
        (listOf(binding.callToggleMicrophone, binding.callToggleSpeaker, binding.callDialpad, binding.callToggleHold, binding.callEnd))
            .forEach { aktiv(it, false) }
        val dauer = EigenerAnruf.beendetDauer()
        @SuppressLint("SetTextI18n")
        binding.callStatusLabel.text =
            if (dauer > 0) "${dauer.getFormattedDuration()} (${getString(R.string.call_ended)})" else getString(R.string.call_ended)
        takt.postDelayed({ finishAndRemoveTask() }, if (dauer > 0) 2500 else 1200)
    }

    // ---------------------------------------------------------------- Audioweg

    private fun audiowegWechseln() {
        val wege = EigenerAnruf.audiowege()
        if (wege.contains(AudioRoute.BLUETOOTH) || wege.contains(AudioRoute.WIRED_HEADSET)) {
            val jetzt = EigenerAnruf.audioweg()
            val items = wege.map {
                SimpleListItem(id = it.route, textRes = it.stringRes, imageRes = it.iconRes, selected = it == jetzt)
            }.toTypedArray()
            wegeDialog = DynamicBottomSheetChooserDialog.createChooser(
                fragmentManager = supportFragmentManager,
                title = R.string.choose_audio_route,
                items = items
            ) { item ->
                wegeDialog = null
                AudioRoute.fromRoute(item.id)?.let { EigenerAnruf.audiowegWaehlen(it) }
            }
        } else {
            EigenerAnruf.audiowegWaehlen(if (EigenerAnruf.audioweg() == AudioRoute.SPEAKER) AudioRoute.EARPIECE else AudioRoute.SPEAKER)
        }
    }

    private fun audiowegAnzeigen() = binding.callToggleSpeaker.apply {
        val weg = EigenerAnruf.audioweg()
        val auswahl = EigenerAnruf.audiowege().let { it.contains(AudioRoute.BLUETOOTH) || it.contains(AudioRoute.WIRED_HEADSET) }
        setImageResource(if (weg == AudioRoute.WIRED_HEADSET) R.drawable.ic_volume_down_vector else weg.iconRes)
        contentDescription = when {
            auswahl -> getString(R.string.choose_audio_route)
            weg == AudioRoute.SPEAKER -> getString(R.string.turn_speaker_off)
            else -> getString(R.string.turn_speaker_on)
        }
        farbe(this, weg != AudioRoute.EARPIECE && weg != AudioRoute.WIRED_HEADSET)
        if (weg == AudioRoute.SPEAKER) naeheAus() else if (!EigenerAnruf.klingelt() && EigenerAnruf.laeuft()) naeheAn()
    }

    // ---------------------------------------------------------------- Anzeige

    private fun foto(uri: String) {
        if (fotoGeladen == uri) return
        fotoGeladen = uri
        binding.callerAvatar.apply {
            if (uri.isEmpty()) {
                val bg = getProperPrimaryColor()
                setBackgroundResource(R.drawable.circle_background)
                setImageResource(R.drawable.ic_person_vector)
                setPadding(resources.getDimensionPixelSize(R.dimen.activity_margin))
                applyColorFilter(bg.getContrastColor())
                background.applyColorFilter(bg)
            } else if (!isFinishing && !isDestroyed) {
                Glide.with(this).load(uri).apply(RequestOptions.circleCropTransform()).into(this)
            }
        }
    }

    private fun aktiv(v: View, an: Boolean) {
        v.isEnabled = an
        v.alpha = if (an) 1f else org.fossify.commons.helpers.LOWER_ALPHA
    }

    private fun farbe(v: ImageView, an: Boolean) {
        if (an) {
            val c = getProperPrimaryColor()
            v.background.applyColorFilter(c)
            v.applyColorFilter(c.getContrastColor())
        } else {
            v.background.applyColorFilter(getProperTextColor().adjustAlpha(0.10f))
            v.applyColorFilter(getProperBackgroundColor().getContrastColor())
        }
    }

    private fun sperrbildschirm() {
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        // Rosenweg (27.11.2026): bewusst KEIN requestDismissKeyguard - das legte bei PIN-Sperre sofort
        // die PIN-Abfrage ueber den Anruf. Annehmen/Ablehnen und alles im Gespraech geht ueber dem
        // Sperrbildschirm, wie bei der Telefon-App des Handys.
    }

    private fun naeheAn() {
        if (config.disableProximitySensor || proximity?.isHeld == true) return
        proximity = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "ch.rosenweg4303.phone:naehe")
            .apply { acquire(60 * MINUTE_SECONDS * 1000L) }
    }

    private fun naeheAus() {
        if (proximity?.isHeld == true) proximity?.release()
    }
}
