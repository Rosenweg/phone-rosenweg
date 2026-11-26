# Rosenweg Telefon – Anleitung

## Was im Rosenweg anders ist

- **Geteilte Telefone:** Den Zugang zur persönlichen Nummer liefert Rosenweg Gerät nach dem Anmelden; beim Abmelden wird das Konto abgemeldet und die Anrufliste gelöscht. Eingehende Anrufe kommen immer im Vollbild.
- **Eigenes Handy:** "Mit Rosenweg anmelden" holt einen eigenen Zugang von der Anlage (SIP über TLS); Abmelden im Menü. Rosenweg Telefon mischt sich dort **nicht** ins Android-Telefon ein: es wird nicht Standard-Telefon-App, meldet kein Anrufkonto an, sieht keine Mobilfunk-Anrufe und schreibt nichts ins Anrufprotokoll des Handys. Rosenweg-Anrufe laufen ganz in der App (eigenes Anruffenster, Vollbild-Benachrichtigung, eigene Anrufliste); Notrufe öffnen die Telefon-App des Handys. Die App bittet um eine Ausnahme von der Akku-Optimierung, sonst beendet Android die Registrierung. Sie fragt regelmässig bei der Anlage nach, ob der Zugang noch gilt.
- Verlauf mit den Anrufen der Person aus der Telefonanlage, persönliche Voicemail anhören. Auch auf dem eigenen Handy: die eigene Liste der App zusammen mit den Anrufen der letzten 90 Tage von der Anlage, ohne Doppel; Abmelden leert nur die Liste der App.
- Gehende Anrufe laufen über die persönliche Nummer, wenn sie angemeldet ist, Notrufe nie.
- **Einstellungen pro Person:** Auf geteilten Telefonen gehören die Einstellungen der angemeldeten Person. Sie liegen (über Rosenweg Gerät) auf der Telefonanlage und sind nach dem Anmelden an jedem geteilten Telefon wieder da. Ohne Rosenweg Gerät bleiben sie wie gewohnt auf dem Handy.
- **Rosenweg Updater:** Auf Handys ohne Rosenweg Gerät prüft die App den App-Katalog der Telefonanlage und bietet Aktualisierungen für alle installierten Rosenweg-Apps an. Jede APK wird vorher geprüft (Prüfsumme, Paketname, Version, Rosenweg-Signatur); Android lässt jede Installation bestätigen. Auf geteilten Telefonen übernimmt das Rosenweg Gerät.
- **Eigenes Handy über Mobilfunk:** Der private Zugang registriert auch über 3G/4G/5G und VPN, nicht nur im WLAN.
- **Eigenes Handy, Verbindung:** Keepalive alle 45 s und Registrierung alle 10 Minuten, damit eine tote Verbindung schnell auffällt; fehlen Benachrichtigungen, Vollbild-Recht oder die Akku-Ausnahme, zeigt die App einen roten Hinweis mit Knopf zu den Einstellungen.

## Installation

- **Geteilte Rosenweg-Telefone:** Rosenweg Gerät installiert die App automatisch aus dem App-Katalog der Telefonanlage und hält sie aktuell.
- **Eigenes Handy:** APK von [www.rosenweg4303.ch/apps](https://www.rosenweg4303.ch/apps) herunterladen und installieren. Danach hält der Rosenweg Updater die Rosenweg-Apps aktuell.

Die App gibt es nicht im Play Store und nicht auf F-Droid. Sie ist mit dem Rosenweg-Schlüssel signiert; Fassungen anderer Herkunft lassen sich nicht darüber installieren.
