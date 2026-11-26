package org.fossify.phone.helpers

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Rosenweg: Klingeln fuer Rosenweg-Anrufe auf privaten Handys (EigenerAnruf, ohne Telecom).
 * Dort spielt Android keinen Klingelton - die App muss selbst klingeln. Beachtet Laut/Vibrieren/Lautlos und "Bitte nicht stoeren" (ueber den Ton-Kanal
 * des Klingeltons, den das System bei DND stummschaltet).
 */
object Klingeln {
    private var ton: Ringtone? = null
    private var vibrator: Vibrator? = null

    @Synchronized
    fun start(ctx: Context) {
        if (ton != null || vibrator != null) return
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val modus = am.ringerMode
        if (modus == AudioManager.RINGER_MODE_NORMAL) {
            try {
                val uri = RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ton = RingtoneManager.getRingtone(ctx, uri)?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    if (Build.VERSION.SDK_INT >= 28) isLooping = true
                    play()
                }
            } catch (e: Exception) {
                ton = null
            }
        }
        if (modus != AudioManager.RINGER_MODE_SILENT) {
            val v = if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
            }
            if (v != null && v.hasVibrator()) {
                vibrator = v
                v.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build()
                )
            }
        }
    }

    @Synchronized
    fun stop() {
        try { ton?.stop() } catch (_: Exception) { }
        try { vibrator?.cancel() } catch (_: Exception) { }
        ton = null
        vibrator = null
    }
}
