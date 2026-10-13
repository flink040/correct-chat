# Correct Chat – Anleitung

Correct Chat ist eine clientseitige Fabric-Mod für Minecraft, die den Chat übersichtlicher und sicherer macht. Sie bietet unter anderem Chat-Tabs, zusätzliche Chatfenster, Zeitstempel, lokale Chatlogs, Textersetzungen, automatische Listener, einen Link-Filter und eine Twitch-Anbindung.

## Voraussetzungen

- Minecraft Java Edition **26.2**, **26.1** oder **1.21.11**
- Fabric Loader **0.19.5 oder neuer**
- eine zur Minecraft-Version passende Fabric API
- Java **25** für Minecraft 26.x oder Java **21** für Minecraft 1.21.11
- optional: eine zur Minecraft-Version passende Mod-Menu-Version

Correct Chat ist eine reine Client-Mod. Sie muss nicht auf dem Minecraft-Server installiert sein.

## Installation

1. Installiere den Fabric Loader für deine unterstützte Minecraft-Version.
2. Lade die passende Fabric API herunter.
3. Kopiere die Fabric API und das passende Correct-Chat-JAR in den Minecraft-Ordner `mods`: `correct-chat-mc26.2-1.0.0.jar`, `correct-chat-mc26.1-1.0.0.jar` oder `correct-chat-mc1.21.11-1.0.0.jar`.
4. Starte Minecraft mit dem Fabric-Profil.
5. Öffne die Einstellungen mit `F8` oder über **Mod Menu → Correct Chat → Einstellungen**.

Die Mod legt ihre Konfiguration in `config/correct-chat.json` ab. Änderungen sollten normalerweise über das Einstellungsmenü vorgenommen werden.

## Schnellstart

Nach einer neuen Installation sind standardmäßig folgende Tabs vorhanden:

- **Chat** – Auffangtab für Nachrichten ohne speziellere Zuordnung
- **DM** – erkannte private Nachrichten
- **Twitch** – Nachrichten aus einem verbundenen Twitch-Chat

Zusätzliche Spieler-, System- oder Mod-Tabs und separate Chatfenster können bei Bedarf angelegt werden. Bestehende Konfigurationen behalten ihre eigenen Tabs und Fenster.

1. Öffne den Minecraft-Chat mit `T`.
2. Klicke über der Eingabezeile auf einen Tab, um dessen Nachrichten anzuzeigen.
3. Klicke auf `+`, um einen neuen Tab anzulegen.
4. Klicke auf `S`, um die durchsuchbare Chat-Historie zu öffnen.
5. Öffne mit `F8` die vollständigen Einstellungen.

Bei kleiner Fensterbreite wird die Hauptnavigation mit Pfeilen durchlaufen. Längere Editoren teilen ihre Einstellungen auf Reiter auf, damit Speichern, Abbrechen und Listen unabhängig von GUI-Skalierung und Fensterhöhe erreichbar bleiben.

## Einstellungen

Unter **F8 → Allgemein → Sprache** stehen **Automatisch**, **Deutsch** und **English** zur Auswahl. Automatisch folgt der in Minecraft gewählten Sprache; alle anderen Minecraft-Sprachen verwenden dabei die englische Oberfläche.

### Allgemein

- **Mod aktiviert** schaltet Correct Chat vollständig ein oder aus.
- **Tippfehler-Schutz für Befehle** erkennt Eingaben wie `7home`, die wahrscheinlich als `/home` gemeint waren.
- **Chat-Tabs und Zusatzfenster** aktiviert den erweiterten Chat-Arbeitsbereich.
- Über **Tabs verwalten** lassen sich Tabs erstellen, bearbeiten und löschen.
- Über **Fenster verwalten** lassen sich zusätzliche Chatfenster einrichten.
- Unter **Serverprofile** kann für einzelne Server oder Einzelspielerwelten ein eigener Arbeitsbereich gespeichert werden.

### Anzeige

- **Zeitstempel** setzt vor jede Nachricht eine Uhrzeit.
- **Copy vor Nachricht** zeigt einen anklickbaren Kopier-Text an.
- **Formatierte Eingabe-Vorschau** zeigt Farbcodes wie `&c` und Formatierungen wie `&l` oberhalb der Eingabezeile an.
- **Ungelesen-Zähler** zeigt neue Treffer an inaktiven Tabs an.
- **Erwähnungen** hebt Tabs zusätzlich mit `!` hervor, wenn der eigene Spielername erwähnt wurde.
- **Chatlog speichern** aktiviert die lokale Chatlog-Funktion.
- **Max. Nachrichten** begrenzt die Anzahl der im Speicher gehaltenen Nachrichten.

Für den Zeitstempel kann ein Java-Zeitformat verwendet werden, zum Beispiel `HH:mm:ss` oder `HH:mm`. Der Platzhalter `%time%` wird in der Darstellung durch die aktuelle Uhrzeit ersetzt. Minecraft-Farbcodes mit `&` sind möglich.

### Automatik

#### Textersetzungen

Textersetzungen ersetzen definierte Textteile automatisch. Eine Ersetzung kann beim Senden oder beim Empfangen angewendet und auf einen bestimmten Server begrenzt werden.

Beispiel:

- Suchen: `mfg`
- Ersetzen durch: `Mit freundlichen Grüßen`

#### Chat-Listener

Listener reagieren auf passende Chatnachrichten. Für jeden Listener lassen sich unter anderem festlegen:

- Name und Suchmuster
- Vergleichsart: exakt, enthält, Regex oder vereinfachte Regex
- automatische Antwort
- Antwortverzögerung
- Ausblenden der ursprünglichen Nachricht
- Serverbindung und Blacklist
- Sound, Lautstärke und Tonhöhe
- Cooldown gegen zu häufige Antworten
- optional Groß-/Kleinschreibung für „Exakt“ und „Enthält“ beachten

In der vereinfachten Regex steht `*` für beliebigen Text, `%` für eine Gruppe und `&player` für den eigenen Spielernamen. Automatische Antworten sollten vorsichtig eingesetzt werden, damit keine Serverregeln verletzt oder Nachrichten unbeabsichtigt mehrfach gesendet werden.

Der globale **Listener-Antispam** begrenzt die Anzahl automatischer Antworten pro Listener und Zeitraum. Befehlsantworten, die mit `/` beginnen, werden von dieser Begrenzung nicht erfasst.

### Schutz

- **Links vor dem Senden prüfen** hält Nachrichten mit nicht freigegebenen URLs zunächst zurück.
- Mit `[Ja]` wird die ursprüngliche Nachricht einmalig gesendet.
- Mit `[Nein]` wird sie verworfen.
- Unter **Erlaubte URLs verwalten** können Ausnahmen wie `twitch.tv/eigenername` eingetragen werden.

Chatlogs werden standardmäßig nur lokal gespeichert. Der externe Upload ist aus Datenschutzgründen deaktiviert und erlaubt ausschließlich HTTPS-Adressen. Wird er aktiviert, wird der vollständige Log-Inhalt an die eingetragene Upload-URL gesendet. Chatlogs können private Nachrichten enthalten.

### Twitch

1. Aktiviere **Twitch-Chat verbinden**.
2. Trage den Twitch-Kanal ohne führendes `#` ein.
3. Zum reinen Lesen ist kein Token nötig.
4. Zum Schreiben werden der Twitch-Benutzername und ein User-Access-Token mit `chat:read` und `chat:edit` benötigt.
5. Speichere die Einstellungen und öffne den Tab **Twitch**.

Wenn der Twitch-Tab im Hauptchat aktiv ist, werden normale Eingaben und Twitch-Befehle wie `/me` an Twitch statt an den Minecraft-Server gesendet. Eingehende Twitch-Nachrichten verwenden ebenfalls aktivierte Chatlogs, Ersetzungen, Zeit-/Kopierformatierung, Listener und Alarme. Automatische Listener-Antworten bleiben dabei im auslösenden Kanal. Der OAuth-Token wird lokal in `config/correct-chat.json` gespeichert und sollte nicht weitergegeben werden. Settings-Exporte enthalten ihn standardmäßig nicht; vor einem Export mit Token erscheint eine ausdrückliche Warnung.

## Tabs und Filter

Ein Tab kann einen oder mehrere Filter enthalten. Verfügbare Filtertypen sind:

- **Alle** – Auffangtab für nicht anderweitig zugeordnete Nachrichten
- **Spieler** – normaler Spielerchat
- **System** – System- und Servermeldungen
- **Twitch** – Twitch-Nachrichten
- **Enthält** – Nachricht enthält den angegebenen Text
- **Entspricht** – gesamte Nachricht entspricht exakt dem Wert
- **Regex** – regulärer Ausdruck
- **Mod** – Meldungen einer ausgewählten Fabric-Mod

Bei mehreren Filtern bestimmt die Regel:

- **Oder-Regel** – mindestens ein Filter muss passen.
- **Und-Regel** – alle Filter müssen passen.

Für den Mod-Filter durchsucht Correct Chat installierte Mods und deren Sprachdateien. Serverseitige oder dynamisch erzeugte Meldungen lassen sich stattdessen über einen Text- oder Regex-Filter zuordnen.

## Chatfenster anpassen

- Hauptchat und Zusatzfenster können getrennte Tabgruppen besitzen. In der JSON-Konfiguration werden sie über `primaryTabIds` beziehungsweise `windows[].tabIds` festgelegt; eine leere Liste zeigt weiterhin alle Tabs.
- Das Hauptchatfenster kann bei geöffnetem Chat am Griff oben rechts skaliert werden.
- Zusatzfenster lassen sich an der mit **↕ ziehen** markierten Kopfzeile verschieben.
- Der Griff unten rechts ändert die Größe eines Zusatzfensters.
- Position und Größe werden beim Loslassen gespeichert.
- Breite und Höhe können zusätzlich in der Fensterverwaltung als Zahlenwerte gesetzt werden.

## Aktionen für einzelne Nachrichten

Ein Rechtsklick auf eine sichtbare Nachricht öffnet ein Aktionsmenü. Je nach erkannter Nachricht stehen folgende Aktionen zur Verfügung:

- Nachricht kopieren
- erkannten Spieler lokal ignorieren
- einen neuen Tab-Filter erstellen
- die Nachricht einem vorhandenen Tab zuordnen
- eine Listener-Regel vorschlagen lassen
- einen lokalen Alarm für diese Nachricht erstellen

Vorgeschlagene reguläre Ausdrücke können vor dem Speichern bearbeitet werden. Ignorieren und automatische Listener werden erst nach einer zusätzlichen Bestätigung übernommen.

Bei **Alarm erstellen …** wird der ursprüngliche Nachrichtentext ohne den von Correct Chat ergänzten Zeitstempel als Auslöser übernommen. Der Alarm kann exakt, als Enthält-Abgleich oder per Regex arbeiten und wahlweise als Titel, Bossbar oder Actionbar erscheinen. Meldung, Farbe, Schriftgröße, Dauer und ein Alarm-Cooldown sind einstellbar. Mehrere gleichzeitig ausgelöste Alarme werden nacheinander angezeigt. Als Ton kann entweder eine Minecraft-Sound-ID oder über **Eigene Audiodatei auswählen …** eine lokale MP3-, WAV- oder OGG-Datei verwendet werden; Lautstärke und Pitch gelten für beide Varianten. Fehlende Dateien werden rot markiert, und unter **F8 → Automatik** lassen sich eigene Sounds sofort stoppen. `%message%` setzt die tatsächlich ausgelöste Nachricht in den Meldungstext ein. Gespeicherte Alarme lassen sich unter **F8 → Automatik → Alarme verwalten** bearbeiten oder löschen.

## Befehle

| Befehl | Funktion |
| --- | --- |
| `/lcl` | Exportiert alle gespeicherten Chatnachrichten. |
| `/lcl 100` | Exportiert die letzten 100 Nachrichten. |
| `/ignore <Spieler>` | Blendet einen Spieler lokal ein oder aus und sendet den Befehl zusätzlich an das Server-Plugin. |
| `/ignore` oder `/ignorelist` | Zeigt die lokal ignorierten Spieler an. |
| `/ignoreclear` | Leert nur die lokale Ignore-Liste. |

Nachrichten lokal ignorierter Spieler werden weder im Hauptchat noch in Tabs, Zusatzfenstern, Chatlogs oder Listenern verarbeitet. Bei ungewöhnlich formatierten Plugin-Nachrichten kann die Erkennung des Spielernamens vom Serverformat abhängen.

## Chatlogs und Konfigurationsdateien

- Lokale Chatlog-Exporte liegen in `chatlogs/correct-chat/`.
- Als Format stehen JSON und lesbarer Klartext zur Verfügung.
- Die Aufbewahrungsgrenze löscht beim Export die ältesten Chatlog-Dateien.
- Die Hauptkonfiguration liegt in `config/correct-chat.json`.
- Die letzte gültige Konfiguration wird als `config/correct-chat.json.bak` gesichert.
- Eine beschädigte Hauptdatei wird beim Start archiviert und nach Möglichkeit aus dem Backup wiederhergestellt.

Beim ersten Start kann Correct Chat kompatible Einstellungen aus älteren LabyMod-Addons unter `labymod-neo/configs/` übernehmen.

## Mod selbst bauen

Zum Bauen werden ein JDK 25 und PowerShell benötigt:

```powershell
$env:JAVA_HOME='C:\Pfad\zum\jdk-25'
.\gradlew.bat build
```

Das fertige JAR liegt anschließend – abhängig vom Build-Ziel – unter `build/libs/correct-chat-mc26.2-1.0.0.jar`, `build/libs/correct-chat-mc26.1-1.0.0.jar` oder `build/libs/correct-chat-mc1.21.11-1.0.0.jar`.

## Fehlerbehebung

### Die Mod erscheint nicht

- Prüfe, ob Minecraft, Fabric Loader, Fabric API und Correct Chat dieselbe Zielversion verwenden.
- Prüfe, ob die JAR-Dateien direkt im Ordner `mods` liegen.
- Stelle sicher, dass Minecraft mit Java 25 gestartet wird.

### Die Einstellungen lassen sich nicht öffnen

- Drücke `F8`.
- Falls Mod Menu installiert ist, öffne **Mods → Correct Chat → Einstellungen**.
- Prüfe, ob eine andere Mod die Taste `F8` belegt.

### Ein Tab zeigt keine Nachrichten

- Prüfe, ob der Tab mindestens einen passenden Filter enthält.
- Prüfe bei mehreren Filtern, ob versehentlich die Und-Regel aktiv ist.
- Verwende bei dynamischen Meldungen einen allgemeineren Text- oder Regex-Filter.
- Beachte, dass konkret zugeordnete Nachrichten nicht zusätzlich im Auffang-Tab **Alle** erscheinen.

### Twitch funktioniert nur lesend

- Prüfe Benutzername und OAuth-Token.
- Der Token benötigt die Berechtigungen `chat:read` und `chat:edit`.
- Speichere einen neu eingetragenen Token, bevor du den Twitch-Tab verwendest.

### Die Konfiguration ist beschädigt

Correct Chat versucht beim nächsten Start automatisch, `correct-chat.json.bak` wiederherzustellen. Die beschädigte Datei bleibt als `correct-chat.json.corrupt-<Zeitstempel>` erhalten.
