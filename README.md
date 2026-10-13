# Correct Chat

Clientseitige Fabric-Portierung der Funktionen aus vier LabyMod-4-Addons:

- **AntiCommandChoker 1.0.0** – erkennt Nachrichten wie `7home`, die sehr wahrscheinlich als `/home` gemeint waren.
- **ChatLog 1.1.0** – hält die letzten Chatzeilen im Speicher und exportiert sie mit `/lcl [Anzahl]`.
- **ChatTime 1.0.7** – setzt einen konfigurierbaren Zeitstempel vor Chatnachrichten.
- **Chat Utilities 2.7.0** – Textersetzungen, automatische Chat-Listener, Serverfilter, Cooldowns, Sounds, Antispam und Kopier-Button.
- **Correct-Chat-Arbeitsbereich** – frei definierbare Chat-Tabs und mehrere separat positionierbare, filterbare Chatfenster.
- **Twitch-Chat** – eigener Tab mit Live-Nachrichten und optionalem Schreiben über einen Twitch-OAuth-Token.

## Zielplattform

- Minecraft Java Edition 26.2
- Fabric Loader 0.19.5+
- Fabric API 0.160.0+26.2
- Java 25
- Mod Menu 20.x optional

Die Versionswerte liegen zentral in `gradle.properties` und können für eine spätere Minecraft-Version aktualisiert werden.

## Bedienung

Die Einstellungsoberfläche passt Navigation, Listen und Suchfelder an schmale Minecraft-Fenster an. Umfangreiche Listener-, Alarm-, Tab- und Chatbereich-Editoren sind in klar beschriftete Teilseiten gegliedert; Änderungen werden erst mit „Übernehmen“ beziehungsweise „Speichern“ übernommen.

- `F8`: Einstellungen öffnen
- Mod Menu → Correct Chat → Einstellungen (wenn Mod Menu installiert ist)

Unter **Allgemein → Sprache** kann die Oberfläche automatisch der Minecraft-Sprache folgen oder fest auf Deutsch beziehungsweise Englisch gestellt werden.
- `/lcl`: alle gespeicherten Nachrichten exportieren
- `/lcl 100`: die letzten 100 Nachrichten exportieren
- `/ignore <Spieler>`: Spieler lokal ein-/ausblenden und denselben Befehl zusätzlich an das Server-Plugin senden
- `/ignore` oder `/ignorelist`: lokal ignorierte Spieler anzeigen
- `/ignoreclear`: nur die lokale Ignore-Liste leeren

Die lokale Ignore-Liste wird dauerhaft in `config/correct-chat.json` gespeichert. Nachrichten ignorierter Spieler werden weder im normalen All-Chat noch in Tabs, Zusatzfenstern, Chatlogs oder Chat-Listenern verarbeitet. Signierter Minecraft-Spielerchat wird anhand des Absenders gefiltert; serverseitig formatierte Plugin-Chats werden über übliche Formate wie `<Spieler>`, `[Rang] Spieler:` und `Spieler »` erkannt.

Die Konfiguration wird über eine temporäre Datei atomar ersetzt. Vor jeder Änderung bleibt die letzte gültige Fassung als `config/correct-chat.json.bak` erhalten. Ist die Hauptdatei beim Start beschädigt, wird sie als `correct-chat.json.corrupt-<Zeitstempel>` archiviert und automatisch aus dem Backup wiederhergestellt.

Bei geöffnetem Chat (`T`) erscheinen die jeweils zugeordneten Tabs direkt über dem Hauptchat beziehungsweise Zusatzfenster. Ein Klick wechselt den Filter dieses Chatbereichs. Jede Tabzeile besitzt einen grünen `+`-Button zum Anlegen und Bearbeiten eines Tabs. Über `primaryTabIds` und `windows[].tabIds` in der Konfiguration können Hauptchat und Zusatzfenster getrennte Tabgruppen erhalten; leere Listen behalten aus Kompatibilitätsgründen die bisherige gemeinsame Tab-Liste bei. Das Mod-Menü enthält globale Einstellungen wie Zeitstempel, Copy-Button, Listener und die generelle Aktivierung. Zusatzfenster lassen sich an ihrer mit „↕ ziehen“ markierten Kopfzeile verschieben und am Griff unten rechts in der Größe verändern; Position und Größe werden beim Loslassen gespeichert.

Der grüne `+`-Button legt jetzt unmittelbar einen neuen Tab an, aktiviert ihn und öffnet seinen Editor. Die danebenliegende Schaltfläche `S` öffnet die durchsuchbare Chat-Historie. Dort kann nach Nachrichtentext, Spielername, Nachrichtentyp oder Server gesucht und die Suche auf einen Tab begrenzt werden. Tab-Beschriftungen zeigen ungelesene Treffer an; eine Erwähnung des eigenen Spielernamens wird zusätzlich mit `!` hervorgehoben. Beide Anzeigen lassen sich unter „Anzeige“ abschalten.

Unter „Allgemein → Serverprofile“ kann für den aktuell verbundenen Server beziehungsweise eine Einzelspielerwelt ein eigenes Arbeitsbereich-Profil angelegt werden. Das Profil erhält zunächst eine Kopie der globalen Tabs und Fenster und wird anschließend auf dem passenden Server automatisch verwendet. Änderungen über die Tab- und Fensterverwaltung betreffen dann das aktive Serverprofil; ohne passendes Profil bleibt der globale Arbeitsbereich aktiv.

Im lokalen Editor hinter dem grünen `+` können Breite und Höhe des Hauptchats sowie jedes Zusatzfensters außerdem direkt als Zahlenwerte gesetzt werden. Damit ist die Größensteuerung vollständig innerhalb von Correct Chat verfügbar und nicht vom Öffnen der Minecraft-Chat-Einstellungen abhängig.

Ein Tab kann mehrere aktive Filter enthalten. Filtertyp und Wert werden zuerst ausgewählt und anschließend mit `+ Filter hinzufügen` in die sichtbare Liste übernommen. Neben „Enthält“ steht „Entspricht“ für den exakten Vergleich der gesamten Nachricht zur Verfügung. Über „Oder-Regel“ beziehungsweise „Und-Regel“ wird festgelegt, ob mindestens ein Filter oder alle Filter passen müssen. Einzelne Filter können direkt aus der Liste entfernt werden; längere Listen werden seitenweise dargestellt. Bestehende Konfigurationen werden beim Laden automatisch als Oder-Regel übernommen.

Ein Rechtsklick auf eine sichtbare Nachricht öffnet ein kontextbezogenes Aktionsmenü. Correct Chat versucht Spielername und Nachrichtenformat zu erkennen und bietet – abhängig vom Ergebnis – lokales Ignorieren, einen neuen oder bestehenden Tab-Filter, eine automatische Listener-Regel, einen lokalen Alarm und das Kopieren der Nachricht an. Beim Alarm wird ein von Correct Chat ergänzter Zeitstempel nicht in den Auslöser übernommen. Als Alarmton können eine Minecraft-Sound-ID oder eine lokale MP3-, WAV- beziehungsweise OGG-Datei gewählt werden; die Meldung erscheint als skalierbarer Titel, Bossbar oder Actionbar in einer gewählten Farbe. Gespeicherte Alarme werden unter `F8 → Automatik → Alarme verwalten` bearbeitet oder gelöscht. Vorgeschlagene reguläre Ausdrücke können vor dem Speichern bearbeitet werden. Ignorieren und automatische Listener benötigen eine zusätzliche Bestätigung; die Mod führt aus einer erkannten Nachricht nie ungefragt eine Regel aus.

Beim Filtertyp „Mod“ kann die Liste der direkt aus dem `mods`-Ordner geladenen Fabric-Mods durchsucht werden. Correct Chat wertet dafür Mod-ID, bereitgestellte IDs, Ressourcen-Namespaces, Übersetzungsschlüssel und konservativ ausgewählte Meldungsvorlagen aus den Sprachdateien der JAR aus. Dynamische oder serverseitige Ausgaben können per Rechtsklick über „Generische Regex einem Tab zuordnen …“ einem vorhandenen oder neuen Tab hinzugefügt werden.

Chatlogs werden standardmäßig lokal nach `chatlogs/correct-chat/` geschrieben. Als Exportformat stehen JSON und lesbarer Klartext zur Auswahl. Dateinamen enthalten Millisekunden und erhalten bei Bedarf einen zusätzlichen Zähler, damit kein Export überschrieben wird. Die konfigurierbare Aufbewahrungsgrenze entfernt beim Export automatisch die ältesten lokalen Chatlog-Dateien. Der ursprüngliche externe Upload ist aus Datenschutz- und Sicherheitsgründen standardmäßig deaktiviert und akzeptiert ausschließlich HTTPS-Endpunkte. Die Oberfläche weist darauf hin, dass lokale Logs private Nachrichten enthalten können und ein externer Upload den vollständigen Inhalt an die konfigurierte URL sendet.

Der globale Link-Filter kann im Mod-Menü aktiviert werden. Nicht freigegebene URLs werden vor dem Versand angehalten und erscheinen kompakt in einer Chatzeile; über `[Ja]` wird die ursprüngliche Nachricht einmalig gesendet, `[Nein]` verwirft sie ohne weitere Meldung. Beide Aktionen sind echte lokale Client-Befehle und erzeugen keine Serverfehler oder langen Befehlsausgaben. Kommagetrennte Ausnahmen erlauben beispielsweise `twitch.tv/eigenername`. Pro Nachricht werden alle erkannten URLs geprüft.

Das Hauptchatfenster lässt sich bei geöffnetem Chat am Griff oben rechts in Breite und Höhe verändern. Da der Hauptchat unten verankert ist, wird die obere rechte Ecke gezogen; Eingabezeile und Tab-Leiste übernehmen die neue Breite automatisch. Zusätzliche Chatfenster bleiben wie bisher über ihren Griff unten rechts skalierbar.

Farbcodes in der aktuellen Eingabe werden direkt oberhalb der Eingabezeile als Vorschau dargestellt. Beispielsweise erscheint `&c&l Test` rot und fett; Befehle werden nicht als Vorschau formatiert. Die Funktion kann unter „Anzeige“ deaktiviert werden.

Beim ersten Start ohne bestehende `config/correct-chat.json` werden kompatible Werte automatisch aus `labymod-neo/configs/{anticommandchoker,chatlog,chattime,chatutilities}/settings.json` übernommen. Tabs, Fenster, Ersetzungen und Listener lassen sich anschließend im Mod-Menü bearbeiten. Der Haupttab „Chat“ ist standardmäßig aktiv; zusätzliche Spieler- und System-Tabs oder ein separates Systemfenster werden nicht mehr automatisch angelegt. DM und Twitch bleiben als optionale Nachrichtenströme verfügbar.

System-/Servermeldungen und Twitch-Nachrichten werden als eigene Nachrichtenströme behandelt. Sobald dafür ein aktiver Tab existiert, erscheinen sie nicht zusätzlich im Auffang-Tab „Alle“. Konkrete Text-, Regex- und Mod-Routen bleiben ebenfalls exklusiv; normaler Spielerchat bleibt ohne eine solche Route in „Alle“ sichtbar.

Unter „Twitch“ kann ein Kanal eingetragen und die Verbindung aktiviert werden. Der Twitch-Tab liest den Chat auch ohne Anmeldung. Eingehende Twitch-Nachrichten durchlaufen dieselben aktivierten Logs, Ersetzungen, Listener und Alarme wie Minecraft-Nachrichten; Listener-Antworten werden wieder an den auslösenden Kanal gesendet. Zum Schreiben werden der Twitch-Benutzername und ein User-Access-Token mit den IRC-Berechtigungen `chat:read` und `chat:edit` benötigt. Der Token wird lokal in `config/correct-chat.json` gespeichert, in der Oberfläche aber nicht im Klartext vorbefüllt. Settings-Exporte lassen ihn standardmäßig weg; ein Export mit Token erfordert eine ausdrückliche Bestätigung und enthält Zugangsdaten im Klartext. Solange der Twitch-Tab im Hauptchat aktiv ist, werden normale Eingaben und Twitch-Kommandos wie `/me` an Twitch statt an den Minecraft-Server gesendet.

## Bauen

```powershell
$env:JAVA_HOME='C:\path\to\jdk-25'
.\gradlew.bat build
```

Der Build führt auch die automatisierten Tests und die Prüfung des Release-JARs aus. Nur die Tests lassen sich mit `./gradlew test` starten. Das fertige Mod-JAR liegt unter `build/libs/correct-chat-1.0.0.jar`.

## Projektstruktur

- `src/client/java/de/dho/correctchat/` – eigenständige Fabric-Implementierung
- `src/main/resources/` – Fabric-Metadaten und Übersetzungen
- `reverse-engineered/<addon>/` – mit Vineflower dekompilierte Referenzquellen und Originalressourcen
- `REVERSE_ENGINEERING.md` – Befunde, Verhaltensmatrix und Portierungsentscheidungen

`reverse-engineered/` ist ausschließlich ein lokaler Analyseordner, wird von Git ignoriert und darf nicht veröffentlicht werden. Die genaue Veröffentlichungsgrenze ist in `THIRD_PARTY_NOTICE.md` dokumentiert. GitHub Actions baut das Projekt bei Pushes und Pull Requests automatisch mit Java 25.

## Herkunft und Veröffentlichung

Correct Chat 1.0 ist eine eigenständige Neuimplementierung. Der Maintainer hat die Veröffentlichungsgrenze geprüft: Der Release enthält weder Code, Binärdateien noch Assets oder Texte früherer Add-ons. Lokale Analyseartefakte sind von Versionsverwaltung, Quellarchiv und Binärpaket ausgeschlossen. Hinweise zu den separat eingebetteten Audiobibliotheken stehen in `THIRD_PARTY_NOTICE.md`.
