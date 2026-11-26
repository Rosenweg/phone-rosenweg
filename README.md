<p align="center"><img src="assets/logo/rosenweg-telefon-512.png" width="96" alt="Logo"></p>
<h1 align="center">Rosenweg Telefon</h1>
<p align="center">Telefon-App für die Smartphones im Rosenweg – normal über die SIM und mit der persönlichen Nummer über die Rosenweg-Telefonanlage.</p>
<p align="center"><a href="https://www.rosenweg4303.ch/apps">Herunterladen</a> · <a href="docs/anleitung.md">Anleitung</a></p>

## Inhalt

- [Überblick](#überblick)
- [Plattformen](#plattformen)
- [Rechte](#rechte)
- [Bedienung](#bedienung)
- [Geteilte Telefone und eigenes Handy](#geteilte-telefone-und-eigenes-handy)
- [Aufbau](#aufbau)
- [Bauen](#bauen)
- [CI und Release](#ci-und-release)
- [Konfiguration](#konfiguration)
- [Herkunft und Lizenz](#herkunft-und-lizenz)

## Überblick

Neben normalen Anrufen über die SIM telefoniert die App über die Rosenweg-Telefonanlage mit der **persönlichen Nummer** der Person und zeigt deren Anrufliste und Voicemail von der Anlage. Der SIP-Teil stammt aus Sipdroid (Modul `sipdroid`, derselbe Kern wie in Rosenweg Gerät).

Was die App kann:

- **Persönliche Nummer:** anrufen und angerufen werden über die Anlage; gehende Anrufe laufen über die persönliche Nummer, sobald sie angemeldet ist.
- **Verlauf:** Anrufe der Person aus der Telefonanlage, ohne Doppel zusammen mit der eigenen Liste der App (letzte 90 Tage).
- **Voicemail:** persönliche Voicemail direkt in der App anhören.
- **Notrufe** gehen nie über die Anlage, sondern immer über das Modem bzw. die Telefon-App des Handys.
- **Einstellungen pro Person** auf den geteilten Telefonen.
- **Rosenweg Updater:** hält auf dem eigenen Handy alle Rosenweg-Apps aktuell.

## Plattformen

| Plattform | Stand |
|---|---|
| Android 8 und neuer (API 26–36) | ja, APK auf [rosenweg4303.ch/apps](https://www.rosenweg4303.ch/apps) |
| Geteilte Rosenweg-Telefone | ja, Rosenweg Gerät installiert sie (Katalog „immer“) |
| Eigenes Handy | ja, im WLAN und über Mobilfunk (SIP über TLS) |
| Play Store, F-Droid | nein – nur mit Rosenweg-Signatur |

## Rechte

Die Rechte vergibt die Anlage bzw. das Portal; die App zeigt nur, was zur angemeldeten Person gehört.

| Wer | darf |
|---|---|
| angemeldete Person | mit der eigenen Nummer telefonieren, eigene Anrufliste und Voicemail sehen; eigene Handys auf [/smartphones](https://www.rosenweg4303.ch/smartphones) abmelden; Erreichbarkeit auf /mein-telefon |
| Technik, Präsident | geteilte Telefone und App-Katalog auf /smartphones verwalten, Zugänge abmelden; Nummern, Gruppen und Dienste der Anlage pflegen |

## Bedienung

| Schritt | So geht’s |
|---|---|
| Geteiltes Telefon | mit der Karte anmelden – der Zugang zur persönlichen Nummer kommt von Rosenweg Gerät. Eingehende Anrufe kommen immer im Vollbild. |
| Eigenes Handy anmelden | „Mit Rosenweg anmelden“ – die App holt einen eigenen Zugang von der Anlage. |
| Anrufen | wählen wie gewohnt; mit angemeldeter persönlicher Nummer geht der Anruf über die Anlage. |
| Verlauf und Voicemail | in der Anrufliste der App; Voicemail antippen zum Anhören. |
| Roter Hinweis | fehlen Benachrichtigungen, Vollbild-Recht oder die Akku-Ausnahme, führt ein Knopf zu den Einstellungen. |
| Abmelden | geteilt: mit dem Abmelden am Telefon (Anrufliste wird gelöscht); eigenes Handy: im Menü, leert nur die Liste der App. |

Ausführlich: [docs/anleitung.md](docs/anleitung.md).

## Geteilte Telefone und eigenes Handy

| | Geteiltes Telefon (mit Rosenweg Gerät) | Eigenes Handy |
|---|---|---|
| Zugang | von Rosenweg Gerät nach dem Anmelden, je Gerät und Person | „Mit Rosenweg anmelden“, eigener Zugang |
| Verbindung | im Rosenweg-WLAN | SIP über TLS, auch über 3G/4G/5G und VPN |
| Android-Telefon | Standard-Telefon-App mit eigenem Anrufkonto für die persönliche Nummer | mischt sich **nicht** ein: keine Standard-App, kein Anrufkonto, sieht keine Mobilfunk-Anrufe, schreibt nichts ins Anrufprotokoll |
| Anrufe | Android-Anruffenster | eigenes Anruffenster und Vollbild-Benachrichtigung in der App |
| Einstellungen | gehören der Person, liegen über Rosenweg Gerät auf der Anlage | bleiben auf dem Handy |
| Abmelden | Konto abgemeldet, Anrufliste gelöscht | im Menü oder im Portal; die App fragt regelmässig nach, ob der Zugang noch gilt |

Auf dem eigenen Handy bittet die App um eine Ausnahme von der Akku-Optimierung, sonst beendet Android die Registrierung. Keepalive alle 45 s und Registrierung alle 10 Minuten, damit eine tote Verbindung schnell auffällt.

Auf einem geteilten Telefon gibt es zwei SIP-Konten: Rosenweg Gerät registriert die Nebenstelle des Geräts (Telefon suchen, Notrufe), Rosenweg Telefon während einer Sitzung den persönlichen Zugang der Person (Beschreibung in Rosenweg/Website, `pbx/GETEILTE-TELEFONE.md`).

## Aufbau

| Teil | Inhalt |
|---|---|
| `app/src/main/kotlin/org/fossify/phone/` | Telefon aus Fossify Phone (Kotlin-Namensraum wie im Original) |
| `…/helpers/PersoenlichesKonto.kt`, `RosenwegAnmeldung.kt`, `Betriebsart.kt` | persönlicher Zugang, Anmeldung auf dem eigenen Handy, geteilt oder privat |
| `…/helpers/AnlageVerlauf.kt`, `EigeneAnrufliste.kt`, `dialogs/VoicemailDialog.kt` | Anrufliste und Voicemail von der Anlage |
| `…/activities/EigenerAnrufActivity.kt`, `helpers/EigenerAnruf.kt`, `Klingeln.kt` | Anrufe ganz in der App (eigenes Handy) |
| `…/receivers/RosenwegSipEmpfaenger.kt` | Wechsel des Zugangs von Rosenweg Gerät (`SIP_PERSON_GEAENDERT`, `ABGEMELDET`) |
| `sipdroid/` | SIP-Kern aus Sipdroid als Gradle-Modul `:sipdroid`, Pakete `org.sipdroid.*` unverändert, native Codecs per NDK |
| [commons-rosenweg](https://github.com/Rosenweg/commons-rosenweg) | Fossify Commons in der Rosenweg-Fassung und `ch.rosenweg4303:updater` |
| `assets/logo/` | Logo aus dem Baukasten von rosenweg-basis: Turm, Weg und Rose mit Hörer-Plakette, dazu ein monochromes Designsymbol (Android 13+) |

Paketname `ch.rosenweg4303.phone`. Die Hinweise nach Sipdroids ADDITIONAL_TERMS stehen im Menü unter „Über Android SIP / Sipdroid“.

## Bauen

Zuerst [commons-rosenweg](https://github.com/Rosenweg/commons-rosenweg) ins lokale Maven-Repository veröffentlichen (`mavenLocal()`), dann die App bauen:

```bash
# in commons-rosenweg
./gradlew -PVERSION=7.1.3-rosenweg.1 publishToMavenLocal

# in diesem Repo
./gradlew assembleFossRelease
```

Das Modul `sipdroid` braucht das Android NDK (`ndkBuild`, Version in `sipdroid/build.gradle.kts`). Die APK liegt unter `app/build/outputs/apk/foss/release/`. Logo neu erzeugen (rosenweg-basis daneben): `sh assets/logo/erzeugen.sh`.

Mehr: [docs/entwickeln.md](docs/entwickeln.md).

## CI und Release

| Workflow | Auslöser | Ergebnis |
|---|---|---|
| `apps.yml` (Rosenweg) | GitHub-Release veröffentlicht, oder von Hand `gh workflow run apps.yml --ref <tag>` | APK geht an **[rosenweg4303.ch/apps](https://www.rosenweg4303.ch/apps)** und in den App-Katalog der Telefonanlage |
| `pr.yml`, `testing-build.yml`, `release.yml` u. a. | aus Fossify übernommen | Prüfungen und Play-Store-Release des Originals; für Rosenweg nicht nötig |

Ablauf einer neuen Version:

1. APK lokal bauen und mit dem Rosenweg-Schlüssel signieren.
2. `gh release create v<versionName> <datei.apk> --notes "Was ist neu"` – Tag = `versionName` der APK, eine APK je Release, Tag auf `main`.
3. `apps.yml` ruft `apps-release.yml` aus Rosenweg/Website auf. Anmeldung per GitHub-OIDC, kein gespeichertes Geheimnis; die Anlage prüft Tag, Prüfsumme, Rosenweg-Signatur und Paketnamen.

## Konfiguration

| Wo | Name | Wozu |
|---|---|---|
| Repo-Wurzel (nicht im Repo) | `keystore.properties` | Rosenweg-Signierschlüssel; Vorlage `keystore.properties_sample` |
| `gradle.properties` | `APP_ID`, `VERSION_NAME`, `VERSION_CODE` | Paketname und Version |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | `commons`, `ch.rosenweg4303:updater` | Versionen der Rosenweg-Bibliotheken |
| Anlage (Rosenweg/Website, pbx-api) | `PBX_SIP_TRANSPORT_PRIVAT`, `PBX_SIP_TRANSPORT` | Transport der eigenen Handys (Vorgabe TLS) bzw. der geteilten Telefone |

Für `apps.yml` braucht es kein GitHub-Secret (OIDC).

## Herkunft und Lizenz

Fork von [Fossify Phone](https://github.com/FossifyOrg/Phone). Die Rosenweg-Anpassungen sind in `main` gemergt; `main` ist der Hauptzweig. Herzlichen Dank an das Fossify-Team und alle Mitwirkenden für die solide Grundlage.

Das Modul `sipdroid` enthält den SIP-Kern von [Sipdroid](https://github.com/i-p-tel/sipdroid) (über [Rosenweg Gerät](https://github.com/Rosenweg/android-sip-rosenweg)); dafür gelten zusätzlich [sipdroid/LICENSE.TXT](sipdroid/LICENSE.TXT) und [sipdroid/ADDITIONAL_TERMS.txt](sipdroid/ADDITIONAL_TERMS.txt). Danke auch dem Sipdroid-Projekt.

Lizenz: GNU General Public License v3.0, siehe [LICENSE](LICENSE).
