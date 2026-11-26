package org.fossify.phone.dialogs

import androidx.appcompat.app.AlertDialog
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.text.format.DateFormat
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.helpers.AnlageVerlauf
import org.fossify.phone.models.RecentCall
import java.util.Date

/**
 * Rosenweg: persoenliche Voicemail aus der Telefonanlage anhoeren. Die Aufnahme
 * kommt ueber die Geraete-App (AnlageProvider), abgespielt wird direkt hier.
 */
class VoicemailDialog(private val activity: SimpleActivity, private val call: RecentCall) {
    private var player: MediaPlayer? = null
    private var dialog: AlertDialog? = null

    init {
        val wann = DateFormat.getDateFormat(activity).format(Date(call.startTS)) + " " +
            DateFormat.getTimeFormat(activity).format(Date(call.startTS))
        val text = buildString {
            append("Voicemail von ${call.name}\n$wann")
            if (call.voicemailText.isNotBlank()) append("\n\n${call.voicemailText}")
        }
        dialog = activity.getAlertDialogBuilder()
            .setTitle("Voicemail")
            .setMessage(text)
            .setPositiveButton("Abspielen", null)
            .setNegativeButton("Schliessen", null)
            .setOnDismissListener { stop() }
            .show()
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener { umschalten() }
    }

    private fun umschalten() {
        val p = player
        when {
            p == null -> laden()
            p.isPlaying -> {
                p.pause()
                knopf("Weiter")
            }
            else -> {
                p.start()
                knopf("Pause")
            }
        }
    }

    private fun laden() {
        knopf("Lädt …")
        ensureBackgroundThread {
            try {
                // blockiert, bis die Geraete-App die Aufnahme von der Anlage geholt hat
                val pfd = activity.contentResolver.openFileDescriptor(AnlageVerlauf.voicemailUri(call.anlageId), "r")
                    ?: throw IllegalStateException("Aufnahme nicht verfuegbar")
                val mp = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                    pfd.use { setDataSource(it.fileDescriptor) }
                    prepare()
                    setOnCompletionListener { activity.runOnUiThread { knopf("Nochmal") } }
                }
                activity.runOnUiThread {
                    if (dialog?.isShowing != true) {
                        mp.release()
                        return@runOnUiThread
                    }
                    player = mp
                    mp.start()
                    knopf("Pause")
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    knopf("Abspielen")
                    activity.showErrorToast(e)
                }
            }
        }
    }

    private fun knopf(text: String) {
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.text = text
    }

    private fun stop() {
        player?.release()
        player = null
    }
}
