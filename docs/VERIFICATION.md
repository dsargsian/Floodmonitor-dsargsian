# Test- und Abnahmeprotokoll

Prüfung am 15.09.2026 auf Windows mit Microsoft JDK 21.0.10 und Maven 3.9.9.

## Automatisiert

**31 Tests bestanden, 0 Fehler, 0 übersprungene Tests.** 23 API-Testausführungen mit MockMvc und 8 isolierte Tests für Simulation/Warnung/Repository. Der finale Quellstand wurde getestet und anschließend als ausführbare Spring-Boot-JAR gebaut.

| Pflichtfall | Nachweis |
|---|---|
| Stationen JSON / XML | `allStationsDefaultJson`, `allStationsXml` |
| Existierende / unbekannte Station | `existingStation`, `missingStationJsonAndXml` |
| Neuester Messwert / noch keine Werte | `latestAndEmptyStation` |
| Zeitraumfilter / ungültiger Zeitraum | `inclusiveTimeRange`, `reversedRange` |
| NORMAL / WARNING / CRITICAL | `normal`, `warning`, `critical` (inklusive Schwellen) |
| Nicht unterstütztes Format | `unsupportedFormat` |
| Maximales Limit | `maximumLimitAndNewestFirst` (1100 Eingaben, genau 1000 Ausgaben) |

Zusätzlich geprüft: ungültige Limits inklusive Integerüberlauf, Datum ohne Zeitzone, ungültige Warnstufen/Booleans, Stations- und Warnungsfilter, Statistik und leere Statistik, POST/PATCH/DELETE, 201/204/400/401/403/404/406/409/415, XML-Schreibzugriffe und XML-Fehler, fehlende/null-Pflichtfelder, Grenzwerte, Akkuausfall, veraltete Messungen, Retention und Deduplizierung.

Die Simulation ist in API-Tests deaktiviert. Simulationstests nutzen feste Uhr und feste Seeds. Der Test `boundsAndBatteryMaintenance` prüft 10.000 Simulationsschritte.

## Vier echte Prozesse

`scripts/verify-distributed.ps1` startete drei Stations-JVMs und eine Zentral-JVM auf Ports 18081–18083/18080. Für den Test wurden 500-ms-Intervalle und ein Aktualitätsfenster von zwei Sekunden verwendet. Alle sieben Prüfungen bestanden:

1. Zentrale übernimmt die Historie.
2. Jeder Stationsprozess enthält genau die vorgesehene Station.
3. Neue Messungen kommen durch wiederholtes Polling an.
4. Deaktivierung am Stationsprozess ergibt UNKNOWN an der Zentrale.
5. Löschung am Stationsprozess entfernt die aggregierte Station.
6. Beenden eines Stationsprozesses ergibt nach Ablauf des Fensters UNKNOWN.
7. Eine andere Station bleibt über die Zentrale als XML abrufbar.

Das Skript beendet die eigenen vier Testprozesse im `finally`-Block. Ausführung:

```powershell
.\mvnw.cmd package
.\scripts\verify-distributed.ps1 -Java "$env:JAVA_HOME\bin\java.exe"
```

## Browserprüfung

Im Codex-Browser wurde die laufende Anwendung mit schmaler Ansicht und mit 1440 Pixeln Breite kontrolliert. JSON und XML laden Stationen, neueste Messung, Statistik und Tabelle. Die Auswahl von ST-003 und CRITICAL lieferte ausschließlich CRITICAL-Zeilen. Ein umgekehrter Zeitraum zeigte die verständliche Servermeldung „from darf nicht nach to liegen“. Nach Rücksetzen der Filter war der Client wieder bedienbar. Automatische Aktualisierungen waren an wechselnden Zeitstempeln und wachsenden Messungszahlen sichtbar.

Während dieser Prüfung fiel ein fehlerhaftes HTML-Optionsmarkup auf; es wurde berichtigt und die Warnstufenauswahl anschließend erneut ausgeführt. Der Build musste außerdem vor dem erneuten Paketieren von der laufenden JAR gelöst werden, da Windows diese Datei sperrt; nach Stoppen des eigenen Prozesses war das Paketieren erfolgreich.

Screenshots: [Desktop JSON](screenshots/dashboard-json.png), [Desktop XML](screenshots/dashboard-xml.png), [schmale Ansicht](screenshots/dashboard-mobile.png).

## Grenzen des Nachweises

Getestet wurde die lokale Demo unter Windows. Die POSIX-Startdatei ist mitgeliefert; ein separater Linux-Lauf wurde nicht durchgeführt. Tests beweisen die geprüften Fälle, keine allgemeine Fehlerfreiheit. In-Memory-Daten gehen beim Neustart verloren. Der Simulator ist nicht hydrologisch kalibriert. Mehrprozessbetrieb und Schlüsselkonzept sind für die lokale Übung dokumentiert; keine produktive Infrastruktur wurde veröffentlicht.
