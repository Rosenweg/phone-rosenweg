# Rosenweg Telefon – Für Entwickler

## Bauen

Die App baut gegen den Rosenweg-Fork der Fossify Commons und die Rosenweg-Module `cloud`/`updater` (`ch.rosenweg4303:*`). Diese kommen aus dem lokalen Maven-Repository (`mavenLocal()`), also zuerst [commons-rosenweg](https://github.com/Rosenweg/commons-rosenweg) veröffentlichen:

```bash
# in commons-rosenweg, Zweig main
./gradlew -PVERSION=7.1.3-rosenweg.1 publishToMavenLocal

# in diesem Repo, Zweig main
./gradlew assembleFossRelease
```

Die APK liegt danach unter `app/build/outputs/apk/foss/release/`. Signiert wird mit `keystore.properties` (Vorlage: `keystore.properties_sample`); die Datei und der Schlüssel liegen nicht im Repo. Paketname: `ch.rosenweg4303.phone`.

Das Modul `sipdroid` baut native Codecs mit dem Android NDK (`ndkBuild`, Version siehe `sipdroid/build.gradle.kts`).

Logo: Rosenweg-Wappen (Turm, Weg, Rose auf Rot) mit dem Zeichen der App rechts unten, erzeugt mit dem Logo-Baukasten aus rosenweg-basis; dazu ein monochromes Designsymbol (Android 13+). Die wählbaren Symbolfarben von Fossify gibt es nicht mehr: alle zeigen dasselbe Rosenweg-Symbol.

Logo neu erzeugen (aus dem Repo-Wurzelverzeichnis, rosenweg-basis daneben): `sh assets/logo/erzeugen.sh`. Welches Zeichen die App trägt, steht in rosenweg-basis `werkzeug/logo/android-apps.txt`.

## Neue Version

APK wie oben bauen und signieren, dann `gh release create v<versionName> <datei.apk> --notes "Was ist neu"` (Tag = versionName der APK, eine APK je Release). Der Workflow `.github/workflows/apps.yml` (wiederverwendbarer Workflow `apps-release.yml` aus Rosenweg/Website) schickt sie an [www.rosenweg4303.ch/apps](https://www.rosenweg4303.ch/apps) und in den App-Katalog der Telefonanlage; damit bekommen sie die geteilten Telefone und der Rosenweg Updater. Anmeldung per GitHub-OIDC, kein gespeichertes Geheimnis; die Anlage prüft Tag, Prüfsumme, Rosenweg-Signatur und Paketnamen. Der Workflow liegt im Standardzweig `main`; `gh release create` ohne `--target` setzt den Tag dort hin. Erneut senden: `gh workflow run apps.yml --ref <tag>`.

## Zweige und Herkunft

Fork von [Fossify Phone](https://github.com/FossifyOrg/Phone). Die Rosenweg-Anpassungen (früher Zweig `rosenweg`) sind in `main` gemergt; `main` ist der Hauptzweig. Herzlichen Dank an das Fossify-Team und alle Mitwirkenden für die solide Grundlage.

Das Modul `sipdroid` enthält den SIP-Kern von [Sipdroid](https://github.com/i-p-tel/sipdroid) (über [Rosenweg Gerät](https://github.com/Rosenweg/android-sip-rosenweg)); dafür gelten zusätzlich [sipdroid/LICENSE.TXT](../sipdroid/LICENSE.TXT) und [sipdroid/ADDITIONAL_TERMS.txt](../sipdroid/ADDITIONAL_TERMS.txt). Danke auch dem Sipdroid-Projekt.

Lizenz: GNU General Public License v3.0, siehe [LICENSE](../LICENSE).
