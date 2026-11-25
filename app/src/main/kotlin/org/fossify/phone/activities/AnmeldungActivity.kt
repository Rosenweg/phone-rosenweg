package org.fossify.phone.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import org.fossify.phone.helpers.PersoenlichesKonto
import org.fossify.phone.helpers.RosenwegAnmeldung

/** Ruecksprung der Rosenweg-Anmeldung (ch.rosenweg4303.phone:/anmeldung), keine Oberflaeche. */
class AnmeldungActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val antwort = intent?.data
        if (antwort == null || PersoenlichesKonto.geraetDa(this)) {
            finish()
            return
        }
        val app = applicationContext
        Toast.makeText(app, "Anmelden …", Toast.LENGTH_SHORT).show()
        Thread {
            val meldung = try {
                val nummer = RosenwegAnmeldung.abschliessen(app, antwort)
                "Angemeldet mit deiner Nummer $nummer"
            } catch (e: RosenwegAnmeldung.Fehler) {
                e.message ?: "Anmeldung fehlgeschlagen"
            } catch (e: Exception) {
                "Keine Verbindung zum Rosenweg"
            }
            runOnUiThread {
                Toast.makeText(app, meldung, Toast.LENGTH_LONG).show()
                startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }
        }.start()
    }
}
