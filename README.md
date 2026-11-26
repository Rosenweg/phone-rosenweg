# Rosenweg Telefon

Telefon-App für die Smartphones der Siedlung Rosenweg in Kaiseraugst. Neben normalen Anrufen über die SIM telefoniert sie über die Rosenweg-Telefonanlage mit der persönlichen Nummer der Person und zeigt deren Anrufliste und Voicemail von der Anlage. Der SIP-Teil stammt aus Sipdroid (Modul `sipdroid`, derselbe Kern wie in Rosenweg Gerät).

## Im Rosenweg

- **Geteilte Telefone:** Den Zugang zur persönlichen Nummer liefert Rosenweg Gerät nach dem Anmelden; beim Abmelden wird das Konto abgemeldet und die Anrufliste gelöscht. Eingehende Anrufe kommen immer im Vollbild.
- **Eigenes Handy:** "Mit Rosenweg anmelden" holt einen eigenen Zugang von der Anlage (SIP über TLS); Abmelden im Menü. Rosenweg Telefon mischt sich dort **nicht** ins Android-Telefon ein: es wird nicht Standard-Telefon-App, meldet kein Anrufkonto an, sieht keine Mobilfunk-Anrufe und schreibt nichts ins Anrufprotokoll des Handys. Rosenweg-Anrufe laufen ganz in der App (eigenes Anruffenster, Vollbild-Benachrichtigung, eigene Anrufliste); Notrufe öffnen die Telefon-App des Handys. Die App bittet um eine Ausnahme von der Akku-Optimierung, sonst beendet Android die Registrierung. Sie fragt regelmässig bei der Anlage nach, ob der Zugang noch gilt.
- Verlauf mit den Anrufen der Person aus der Telefonanlage, persönliche Voicemail anhören. Auch auf dem eigenen Handy: die eigene Liste der App zusammen mit den Anrufen der letzten 90 Tage von der Anlage, ohne Doppel; Abmelden leert nur die Liste der App.
- Gehende Anrufe laufen über die persönliche Nummer, wenn sie angemeldet ist, Notrufe nie.
- **Einstellungen pro Person:** Auf geteilten Telefonen gehören die Einstellungen der angemeldeten Person. Sie liegen (über Rosenweg Gerät) auf der Telefonanlage und sind nach dem Anmelden an jedem geteilten Telefon wieder da. Ohne Rosenweg Gerät bleiben sie wie gewohnt auf dem Handy.
- **Rosenweg Updater:** Auf Handys ohne Rosenweg Gerät prüft die App den App-Katalog der Telefonanlage und bietet Aktualisierungen für alle installierten Rosenweg-Apps an. Jede APK wird vorher geprüft (Prüfsumme, Paketname, Version, Rosenweg-Signatur); Android lässt jede Installation bestätigen. Auf geteilten Telefonen übernimmt das Rosenweg Gerät.
- **Eigenes Handy über Mobilfunk:** Der private Zugang registriert auch über 3G/4G/5G und VPN, nicht nur im WLAN.
- **Eigenes Handy, Verbindung:** Keepalive alle 45 s und Registrierung alle 10 Minuten, damit eine tote Verbindung schnell auffällt; fehlen Benachrichtigungen, Vollbild-Recht oder die Akku-Ausnahme, zeigt die App einen roten Hinweis mit Knopf zu den Einstellungen.
- **Logo:** Rosenweg-Wappen (Turm, Weg, Rose auf Rot) mit dem Zeichen der App rechts unten, erzeugt mit dem Logo-Baukasten aus rosenweg-basis; dazu ein monochromes Designsymbol (Android 13+). Die wählbaren Symbolfarben von Fossify gibt es nicht mehr: alle zeigen dasselbe Rosenweg-Symbol.

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

Logo neu erzeugen (aus dem Repo-Wurzelverzeichnis, rosenweg-basis daneben): `sh assets/logo/erzeugen.sh`. Welches Zeichen die App trägt, steht in rosenweg-basis `werkzeug/logo/android-apps.txt`.

## Neue Version

APK wie oben bauen und signieren, dann `gh release create v<versionName> <datei.apk> --notes "Was ist neu"` (Tag = versionName der APK, eine APK je Release). Der Workflow `.github/workflows/apps.yml` (wiederverwendbarer Workflow `apps-release.yml` aus Rosenweg/Website) schickt sie an [www.rosenweg4303.ch/apps](https://www.rosenweg4303.ch/apps) und in den App-Katalog der Telefonanlage; damit bekommen sie die geteilten Telefone und der Rosenweg Updater. Anmeldung per GitHub-OIDC, kein gespeichertes Geheimnis; die Anlage prüft Tag, Prüfsumme, Rosenweg-Signatur und Paketnamen. Der Workflow liegt im Standardzweig `main`; `gh release create` ohne `--target` setzt den Tag dort hin. Erneut senden: `gh workflow run apps.yml --ref <tag>`.

## Herkunft und Lizenz

Fork von [Fossify Phone](https://github.com/FossifyOrg/Phone). Der Zweig `main` folgt dem Original, die Rosenweg-Anpassungen liegen im Zweig `rosenweg`. Herzlichen Dank an das Fossify-Team und alle Mitwirkenden für die solide Grundlage.

Das Modul `sipdroid` enthält den SIP-Kern von [Sipdroid](https://github.com/i-p-tel/sipdroid) (über [Rosenweg Gerät](https://github.com/Rosenweg/android-sip-rosenweg)); dafür gelten zusätzlich [sipdroid/LICENSE.TXT](sipdroid/LICENSE.TXT) und [sipdroid/ADDITIONAL_TERMS.txt](sipdroid/ADDITIONAL_TERMS.txt). Danke auch dem Sipdroid-Projekt.

Lizenz: GNU General Public License v3.0, siehe [LICENSE](LICENSE).
