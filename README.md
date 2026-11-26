<p align="center"><img src="assets/logo/rosenweg-telefon-512.png" width="96" alt="Logo"></p>
<h1 align="center">Rosenweg Telefon</h1>
<p align="center">Telefon-App für die Smartphones im Rosenweg – normal über die SIM und mit der persönlichen Nummer über die Rosenweg-Telefonanlage.</p>
<p align="center"><a href="https://www.rosenweg4303.ch/apps">Herunterladen</a> · <a href="docs/anleitung.md">Anleitung</a> · <a href="docs/entwickeln.md">Für Entwickler</a></p>

## Was die App kann

- Anrufen und angerufen werden mit der persönlichen Rosenweg-Nummer.
- Anrufliste und Voicemail der Person direkt von der Telefonanlage.
- Geteilte Telefone: Zugang kommt beim Anmelden, beim Abmelden ist alles weg.
- Eigenes Handy: „Mit Rosenweg anmelden“ – läuft ganz in der App, auch über Mobilfunk.
- Notrufe gehen immer über das normale Telefon.

## Plattformen

Android.

## Entwickeln

Android-App (Kotlin, Gradle) auf Basis von [commons-rosenweg](https://github.com/Rosenweg/commons-rosenweg), SIP-Kern im Modul `sipdroid`.
Bau, Signatur und Veröffentlichung stehen in [docs/entwickeln.md](docs/entwickeln.md).

## Herkunft und Lizenz

Basiert auf [Fossify Phone](https://github.com/FossifyOrg/Phone); der SIP-Teil stammt aus [Sipdroid](https://github.com/i-p-tel/sipdroid) (über [Rosenweg Gerät](https://github.com/Rosenweg/android-sip-rosenweg)). Danke beiden Projekten.
GPL-3.0, siehe [LICENSE](LICENSE); für `sipdroid` zusätzlich [LICENSE.TXT](sipdroid/LICENSE.TXT) und [ADDITIONAL_TERMS.txt](sipdroid/ADDITIONAL_TERMS.txt).
