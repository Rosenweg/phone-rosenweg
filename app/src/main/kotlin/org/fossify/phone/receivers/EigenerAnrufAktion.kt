package org.fossify.phone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.phone.helpers.EigenerAnruf

/** Rosenweg: "Ablehnen"/"Auflegen" in der Anruf-Benachrichtigung (privates Handy, ohne Telecom). */
class EigenerAnrufAktion : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == EigenerAnruf.AUFLEGEN) EigenerAnruf.auflegen()
    }
}
