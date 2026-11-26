package org.fossify.phone

import android.os.UserManager
import org.fossify.commons.FossifyApp
import org.fossify.phone.helpers.Betriebsart
import org.fossify.phone.helpers.EigenerAnruf
import org.fossify.phone.helpers.PersoenlichesKonto
import org.fossify.phone.helpers.Vollbildrecht

/** Rosenweg: beim Start Betriebsart (geteilt/privat) anwenden und das persoenliche SIP-Konto abgleichen. */
class App : FossifyApp() {
    override fun onCreate() {
        super.onCreate()
        // privates Handy: nicht ins Android-Telefon einklinken (Betriebsart), Anrufe in der App
        EigenerAnruf.einrichten(this)
        Betriebsart.anwenden(this)
        if (getSystemService(UserManager::class.java)?.isUserUnlocked != false) {
            PersoenlichesKonto.abgleichen(this, "app")
            PersoenlichesKonto.nachfragenPlanen(this)
        }
        Vollbildrecht.pruefen(this)
        Vollbildrecht.planen(this)
    }
}
