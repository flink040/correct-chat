# Changelog

## 1.0.0 – 2026-10-13

Erste stabile Veröffentlichung von Correct Chat.

### Neu

- Sprachauswahl für automatische Minecraft-Sprache, Deutsch und Englisch
- Mehrfachfilter mit UND-/ODER-Verknüpfung sowie exakten und modbasierten Filtern
- Serverprofile, getrennte Tabgruppen und frei positionierbare Zusatzfenster
- Durchsuchbare Chat-Historie, Ungelesen-Zähler und Erwähnungsmarkierung
- Kontextaktionen für Ignorieren, Tabregeln, Listener, lokale Alarme und Werbeblocker
- Twitch-Lese- und Schreibintegration sowie lokale MP3-, WAV- und OGG-Sounds
- Responsive, paginierte Einstellungsoberfläche für kleine GUI-Größen
- Automatisierte Tests für Konfigurationsmigration, Link-/Werbefilter, Regex-Cache und Regelvorschläge

### Verbessert

- Atomare Konfigurationsspeicherung mit Backup- und Wiederherstellungslogik
- Sichererer HTTPS-Upload, geschützte Token-Exporte und lokale Linkbestätigung
- Gepufferte Speicherung, gecachte Filter/Zeilenumbrüche und gebündelte Chat-Neuberechnung
- Bereinigte Standardkonfiguration und robustere Migration älterer Einstellungen

### Behoben

- Nachrichten bleiben beim Tabwechsel verfügbar, statt frühzeitig verworfen zu werden
- Listener-, Ignore-, Twitch- und Overlay-Nachrichten folgen konsistent demselben Verarbeitungspfad
- Copy- und Zeitpräfixe werden unabhängig von ihrer Reihenfolge korrekt normalisiert
