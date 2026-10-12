# Reverse-Engineering- und Portierungsbericht

## Methodik

Die vier bereitgestellten JARs wurden als ZIP-Archive inventarisiert, ihre `addon.json`- und Sprachressourcen gelesen und alle Klassen mit Vineflower 1.12.0 dekompiliert. Eingebettete Inhalte wurden ausschließlich als Programmdaten analysiert. Die neue Implementierung verwendet keine LabyMod-API und wird gegen die Fabric-/Minecraft-26.2-APIs gebaut.

## AntiCommandChoker 1.0.0

Originalverhalten:

- Lauscht auf ausgehende Chatnachrichten.
- Greift bei Texten, die mit `7` beginnen, aber nicht mit `7 `, und länger als ein Zeichen sind.
- Unterdrückt die Nachricht und zeigt zwei klickbare Aktionen: als Slash-Befehl senden oder unverändert senden.
- Eine temporäre Ausnahmeliste verhindert, dass „trotzdem senden“ erneut blockiert wird.

Portierung:

- Über `ClientSendMessageEvents` umgesetzt.
- „Trotzdem senden“ verwendet einen internen, Base64-kodierten Clientbefehl und einen Einmal-Bypass; Nutztext wird nicht in Logs geschrieben.
- Der Originalcode bildet den korrigierten Befehl mit `substring(1, length - 1)` und verliert dadurch das letzte Zeichen. Die Portierung verwendet bewusst `substring(1)`.

## ChatLog 1.1.0

Originalverhalten:

- Speichert bis zu 1000 empfangene Nachrichten mit Legacy-Text, Servername und Zeitstempel im Arbeitsspeicher.
- `/lcl [Anzahl]` sendet JSON per unverschlüsseltem HTTP POST an `chatlog-api.hdskins.de`.
- Bei HTTP 200 wird die Antwort-ID als Link angezeigt und der Speicher geleert.
- Eine Anzahl größer als die vorhandene Nachrichtenmenge verursacht im Original einen Indexfehler.

Portierung:

- Begrenzung ist konfigurierbar (1–10.000); die angeforderte Anzahl wird sicher geklemmt.
- Standard ist ein lokaler, UTF-8-kodierter JSON-Export unter `chatlogs/correct-chat/`.
- Der historische Upload bleibt als explizite Opt-in-Kompatibilitätsfunktion vorhanden und ist standardmäßig deaktiviert. Der konfigurierte Standardendpunkt bleibt wegen der Originalkompatibilität HTTP; deshalb sollte er nur bewusst verwendet oder durch einen vertrauenswürdigen HTTPS-Endpunkt ersetzt werden.
- Netzwerk- und Dateiarbeit läuft außerhalb des Client-Threads.

## ChatTime 1.0.7

Originalverhalten:

- Standardstil: `&e┃ &6%time% &8» &r`
- Standardformat: `HH:mm:ss`
- Ersetzt `%time%` durch die aktuelle lokale Zeit und setzt das formatierte Präfix vor jede Chatnachricht.

Portierung:

- Stil und Datumsformat bleiben konfigurierbar.
- `&`- und `§`-Farbcodes werden in native Minecraft-Komponenten übersetzt.
- Ungültige Datumsformate fallen zur Laufzeit sicher auf `HH:mm:ss` zurück; das Menü validiert sie bereits beim Speichern.

## Chat Utilities 2.7.0

Originalverhalten:

- Ausgehende Textersetzungen mit optionaler Serverbindung.
- Optionale eingehende Textersetzungen.
- Chat-Listener mit `EQUALS`, `CONTAINS`, `REGEX` und `SIMPLIFIED_REGEX`.
- Platzhalter `&player`; Regex-Gruppen werden als `%1`, `%2`, … in die Antwort eingesetzt.
- Antwortverzögerung, Nachricht unterdrücken, Sound, fünf Blacklist-Wörter, globale oder gruppenbezogene Cooldowns.
- Optionales Antispamfenster pro Listener.
- Klickbarer Kopier-Button an jeder Nachricht.
- Separates HUD-Widget zur Vorschau von Farbcodes im gerade eingegebenen Chattext.

Portierung:

- Alle nachrichtenbezogenen Funktionen, Serverfilter, Cooldowns, Sounds und Antispam sind implementiert.
- Zusätzlich zur Originalfunktionalität besitzt die Fabric-Portierung persistente Tabs und mehrere unabhängige Chatfenster. Jeder Bereich kann auf Spieler-, System-, Text- oder Regex-Nachrichten gefiltert werden; der Haupt-Chat behält Minecrafts native Klick- und Hover-Interaktion.
- Zusatzfenster sind in Position und Größe konfigurierbar. Bei geschlossenem Chat zeigen sie nur neue Nachrichten für zehn Sekunden, bei geöffnetem Chat den gespeicherten Verlauf und ihre Tab-Leiste.
- Die ursprüngliche feste Fünfer-Blacklist wurde zu einer beliebig langen, kommaseparierten Liste erweitert.
- Ersetzungen an empfangenen Komponenten erhalten vorhandene Stile segmentweise.
- Regeln und Listener können vollständig über die Mod-Menu-Seiten angelegt, bearbeitet und gelöscht werden.
- Die Eingabe-Vorschau für `&`- und `§`-Farbcodes ist als eigener Layer direkt im geöffneten Minecraft-Chat umgesetzt und kann in den Anzeigeeinstellungen deaktiviert werden.

## Ereignisfluss der Fabric-Portierung

1. Ausgehende Chattexte werden auf versehentliche `7...`-Befehle geprüft.
2. Freigegebene Chattexte und Befehle durchlaufen aktive Textersetzungen.
3. Eingehende Nachricht wird in den begrenzten In-Memory-Log aufgenommen.
4. Aktive Listener prüfen Original-Klartext, Serverfilter, Blacklist, Match und Cooldown.
5. Danach folgen eingehende Textersetzungen, Zeitpräfix und Kopier-Button.
6. Geplante Antworten laufen zurück auf dem Minecraft-Clientthread.

## Noch vor öffentlicher Veröffentlichung

- Lizenzfreigaben der vier Originalautoren einholen.
- Den historischen Uploaddienst prüfen oder ganz entfernen; HTTP überträgt Chatinhalt unverschlüsselt.
- Einen manuellen Spieltest mit signiertem Spielerchat, Systemchat, Einzelspieler und mindestens einem externen Server durchführen.
