package org.fossify.phone

import android.os.UserManager
import org.fossify.commons.FossifyApp
import org.fossify.phone.helpers.PersoenlichesKonto

/** Rosenweg: beim Start das persoenliche SIP-Konto abgleichen (PersoenlichesKonto). */
class App : FossifyApp() {
    override fun onCreate() {
        super.onCreate()
        if (getSystemService(UserManager::class.java)?.isUserUnlocked != false) {
            PersoenlichesKonto.abgleichen(this, "app")
        }
    }
}
