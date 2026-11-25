# Rosenweg Telefon

Telefon-App für die Smartphones der Siedlung Rosenweg in Kaiseraugst. Neben normalen Anrufen über die SIM telefoniert sie über die Rosenweg-Telefonanlage mit der persönlichen Nummer der Person und zeigt deren Anrufliste und Voicemail von der Anlage. Der SIP-Teil stammt aus Sipdroid (Modul `sipdroid`, derselbe Kern wie in Rosenweg Gerät).

## Im Rosenweg

- **Geteilte Telefone:** Den Zugang zur persönlichen Nummer liefert Rosenweg Gerät nach dem Anmelden; beim Abmelden wird das Konto abgemeldet und die Anrufliste gelöscht. Eingehende Anrufe kommen immer im Vollbild.
- **Eigenes Handy (Android 11+):** "Mit Rosenweg anmelden" holt einen eigenen Zugang von der Anlage (SIP über TLS); Abmelden im Menü. Das Konto verwaltet die App selbst, Anrufe erscheinen trotzdem im eigenen Anruffenster und in der Anrufliste. Die App bittet um eine Ausnahme von der Akku-Optimierung, sonst beendet Android die Registrierung. Sie fragt regelmässig bei der Anlage nach, ob der Zugang noch gilt.
- Verlauf mit den Anrufen der Person aus der Telefonanlage, persönliche Voicemail anhören.
- Gehende Anrufe laufen über die persönliche Nummer, wenn sie angemeldet ist, Notrufe nie.
- **Einstellungen pro Person:** Auf geteilten Telefonen gehören die Einstellungen der angemeldeten Person. Sie liegen (über Rosenweg Gerät) auf der Telefonanlage und sind nach dem Anmelden an jedem geteilten Telefon wieder da. Ohne Rosenweg Gerät bleiben sie wie gewohnt auf dem Handy.
- **Rosenweg Updater:** Auf Handys ohne Rosenweg Gerät prüft die App den App-Katalog der Telefonanlage und bietet Aktualisierungen für alle installierten Rosenweg-Apps an. Jede APK wird vorher geprüft (Prüfsumme, Paketname, Version, Rosenweg-Signatur); Android lässt jede Installation bestätigen. Auf geteilten Telefonen übernimmt das Rosenweg Gerät.

## Installation

- **Geteilte Rosenweg-Telefone:** Rosenweg Gerät installiert die App automatisch aus dem App-Katalog der Telefonanlage und hält sie aktuell.
- **Eigenes Handy:** APK von [www.rosenweg4303.ch/apps](https://www.rosenweg4303.ch/apps) herunterladen und installieren. Danach hält der Rosenweg Updater die Rosenweg-Apps aktuell.

Die App gibt es nicht im Play Store und nicht auf F-Droid. Sie ist mit dem Rosenweg-Schlüssel signiert; Fassungen anderer Herkunft lassen sich nicht darüber installieren.

## Bauen

Die App baut gegen den Rosenweg-Fork der Fossify Commons und die Rosenweg-Module `cloud`/`updater` (`ch.rosenweg4303:*`). Diese kommen aus dem lokalen Maven-Repository (`mavenLocal()`), also zuerst [commons-rosenweg](https://github.com/Rosenweg/commons-rosenweg) veröffentlichen:

```bash
# in commons-rosenweg, Zweig rosenweg
./gradlew -PVERSION=7.1.3-rosenweg.1 publishToMavenLocal

# in diesem Repo, Zweig rosenweg
./gradlew assembleFossRelease
```

Die APK liegt danach unter `app/build/outputs/apk/foss/release/`. Signiert wird mit `keystore.properties` (Vorlage: `keystore.properties_sample`); die Datei und der Schlüssel liegen nicht im Repo. Paketname: `ch.rosenweg4303.phone`.

Das Modul `sipdroid` baut native Codecs mit dem Android NDK (`ndkBuild`, Version siehe `sipdroid/build.gradle.kts`).

## Herkunft und Lizenz

Fork von [Fossify Phone](https://github.com/FossifyOrg/Phone). Der Zweig `main` folgt dem Original, die Rosenweg-Anpassungen liegen im Zweig `rosenweg`. Herzlichen Dank an das Fossify-Team und alle Mitwirkenden für die solide Grundlage.

Das Modul `sipdroid` enthält den SIP-Kern von [Sipdroid](https://github.com/i-p-tel/sipdroid) (über [Rosenweg Gerät](https://github.com/Rosenweg/android-sip-rosenweg)); dafür gelten zusätzlich [sipdroid/LICENSE.TXT](sipdroid/LICENSE.TXT) und [sipdroid/ADDITIONAL_TERMS.txt](sipdroid/ADDITIONAL_TERMS.txt). Danke auch dem Sipdroid-Projekt.

Lizenz: GNU General Public License v3.0, siehe [LICENSE](LICENSE).
