package org.sipdroid.sipua.ui;

import android.util.Log;

/**
 * Rosenweg (26.11.2026): knappes Ereignis-Log des SIP-Kerns unter dem Tag "RosenwegSip",
 * auch im Release-Build (dort ist "SipUA" wegen Sipdroid.release stumm). Nur Ereignisse
 * (Registrierung, Verbindung, Anruf) - nie Passwoerter oder ganze SIP-Nachrichten.
 */
public final class RosenwegLog {
	private static final String TAG = "RosenwegSip";

	private RosenwegLog() {}

	public static void i(String text) {
		Log.i(TAG, text);
	}

	public static void w(String text) {
		Log.w(TAG, text);
	}
}
