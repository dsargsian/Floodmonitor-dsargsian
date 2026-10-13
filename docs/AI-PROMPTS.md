# AI-Prompt-Protokoll

Datum: 15.09.2026. Werkzeug: Codex im lokalen Projektordner.

## Benutzerauftrag (wörtlich, Link normalisiert)

> https://github.com/ThomasMicheler/DEZSYS_GK71_FLOODMONITOR_REST/blob/main/TASK.md lies dir das durch und analysiere diese aufgabe. du bist ein experte in deinem gebiet und wirst dir keine fehler erlauben. setzte diese aufgabe um.

## Verwendung

Dieser Auftrag wurde zur Analyse und Erstellung von Java-Quellcode, Webclient, Tests, Build-Konfiguration, Startskripten und Dokumentation verwendet. Es wurden keine weiteren fachlichen Benutzerprompts erfunden und keine Unteragenten eingesetzt.

Der Assistent las die verlinkte Aufgabenstellung, prüfte den leeren Projektordner und die vorhandenen Werkzeuge, implementierte die Lösung und führte Builds, API-/Simulationstests und Laufzeitprüfungen aus. Die einzelnen internen Tool-Aufrufe sind im zugehörigen Codex-Verlauf nachvollziehbar. Dieses Dokument enthält den Benutzerprompt und die Einsatzbeschreibung, keine internen Systemanweisungen.

## Eigenständige Implementierungsentscheidungen

- Maven, Java 21 und Spring Boot; In-Memory-Repository.
- Statistik unter `/stations/{id}/statistics`.
- Aktualitätsfenster zwei Minuten, Historienlimit 1000 und Retention 10.000 pro Station.
- JSON-Fehler-Fallback bei 406, explizite XML-Listenelemente.
- Öffentliche Viewer-Abfragen und Header-Schlüssel für Admin-Zugriffe.
- Optionaler Mehrprozessbetrieb, Administration direkt am Stationsprozess.

Die KI-Unterstützung ersetzt nicht das Verständnis des Quellcodes für das Abgabegespräch. Start, Bedienung und Grenzen stehen in `README.md`.
