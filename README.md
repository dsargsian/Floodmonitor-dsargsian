# Flusswache – Floodmonitor REST

Spring-Boot-Anwendung zur [Aufgabe DEZSYS GK71](https://github.com/ThomasMicheler/DEZSYS_GK71_FLOODMONITOR_REST/blob/main/TASK.md): drei simulierte Pegelstationen, REST mit JSON/XML, Webclient und optional drei eigenständige Stationsprozesse mit einer abfragenden Zentrale.

## Start

Voraussetzung: **JDK 21**, `JAVA_HOME` auf das JDK setzen. Der Maven Wrapper lädt Maven 3.9.9 beim ersten Aufruf; dafür und für den ersten Build ist Internet erforderlich. Danach benötigt der Webclient keine externen CDNs. Maven ab 3.6.3 kann auch direkt verwendet werden.

```powershell
# Windows PowerShell
$env:JAVA_HOME='C:\Pfad\zum\jdk-21'
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

```sh
# Linux / macOS
export JAVA_HOME=/path/to/jdk-21
./mvnw test
./mvnw spring-boot:run
```

Webclient: **http://127.0.0.1:8080** · OpenAPI: **http://127.0.0.1:8080/openapi.json**

Alternativ `mvn spring-boot:run`. Ausführbares Paket:

```sh
./mvnw package
java -jar target/floodmonitor-1.0.0.jar
```

Mit Strg+C beenden. Ein Neustart setzt die Daten zurück: bewusst speicherbasierte Speicherung, maximal 10.000 Messungen pro Station. Beim Start entstehen 36 gekennzeichnete synthetische Historienwerte je Station. `simulation.seed-history=false` deaktiviert diese Demo-Historie. Die Anwendung ist ein Lernprojekt mit simulierten Daten.

## Bedienung

1. Stationskarte oder Station im Auswahlfeld wählen.
2. Optional Zeitraum und Warnstufe setzen; Datumseingaben verwenden die lokale Zeitzone und werden als ISO-8601 mit UTC an die API gesendet.
3. JSON oder XML auswählen. Beide Formate werden tatsächlich abgerufen und separat verarbeitet.
4. Neueste Messung, Statistik und Historie ablesen. Die Historie zeigt höchstens 100 Zeilen, neueste zuerst. Die Statistik berücksichtigt alle gespeicherten Treffer im gewählten Zeitraum.

Der Client aktualisiert alle zehn Sekunden, solange der Tab sichtbar ist. Lade- und Fehlerzustände erscheinen oberhalb der Messdaten. Der aktuelle Messwert bleibt von den Historienfiltern unabhängig. Grün = NORMAL, Orange = WARNING, Rot = CRITICAL, Grau = UNKNOWN.

## API

Basis `/api/v1`. Ohne `Accept` bzw. mit `*/*`: JSON. XML mit `Accept: application/xml`. Nicht unterstütztes Format: 406 mit dokumentiertem JSON-Fehler-Fallback, da keine akzeptable Darstellung existiert.

| Methode | Pfad | Parameter / Bedeutung |
|---|---|---|
| GET | `/stations` | `river`, `isActive`, `warningLevel` |
| GET | `/stations/{id}` | Stammdaten |
| GET | `/stations/{id}/measurements/latest` | 204, solange noch keine Messung existiert |
| GET | `/stations/{id}/measurements` | `from`, `to`, `warningLevel`, `limit` (1–1000, Standard 100) |
| GET | `/stations/{id}/statistics` | `from`, `to`, `warningLevel`; alle gespeicherten Treffer |
| GET | `/alerts` | `river`, `warningLevel`; nur aktuelle WARNING/CRITICAL |
| POST | `/stations` | Admin: Station erstellen; 201 und Location |
| PATCH | `/stations/{id}` | Admin: Teiländerung einschließlich `isActive` |
| DELETE | `/stations/{id}` | Admin: Station und Historie löschen; 204 |

Zeitgrenzen sind inklusive. `from > to`, ungültige ISO-Zeitpunkte, Warnstufen oder Limits ergeben 400. Warnstufen sind case-sensitive. Flussnamen werden ohne Unterscheidung der Groß-/Kleinschreibung verglichen. Listen sind JSON-Objekte mit `stations`, `measurements` bzw. `alerts`; in XML heißen die Wurzeln gleich und enthalten `station`- bzw. `measurement`-Elemente. Alle Einzelobjekte enthalten dieselben fachlichen Felder.

```sh
curl -H 'Accept: application/json' http://127.0.0.1:8080/api/v1/stations
curl -H 'Accept: application/xml' http://127.0.0.1:8080/api/v1/stations/ST-001/measurements/latest
curl 'http://127.0.0.1:8080/api/v1/stations/ST-001/measurements?from=2026-09-15T00:00:00Z&to=2026-09-15T23:59:59Z&limit=100'
curl 'http://127.0.0.1:8080/api/v1/alerts?river=Donau&warningLevel=CRITICAL'
curl -H 'Accept: application/xml' http://127.0.0.1:8080/api/v1/stations/ST-999
```

In Windows PowerShell `curl.exe` verwenden. Bei leeren Ergebnissen den Zeitraum an das aktuelle Datum anpassen. Vollständige Schemas und Fehlerantworten: [openapi.json](src/main/resources/static/openapi.json). Regeneration nach API-Änderungen: `node scripts/generate-openapi.cjs`.

## Sicherheitskonzept

Lesende Abfragen sind öffentlich und entsprechen der Rolle **Viewer**. Schreibende Methoden benötigen den Header `X-API-Key` mit dem Wert von `FLOOD_ADMIN_KEY`. Ohne konfigurierten Admin-Schlüssel ist Administration gesperrt. Optional akzeptiert `FLOOD_VIEWER_KEY` einen expliziten Viewer-Schlüssel: Schreiben damit ergibt 403; fehlender oder falscher Schlüssel ergibt 401. Beide Schlüssel müssen unterschiedlich sein. Es gibt keine eingebauten produktiven Passwörter und keinen Admin-Schlüssel im Webclient.

```powershell
$env:FLOOD_ADMIN_KEY='<eigenen-langen-zufaelligen-Schluessel-einsetzen>'
.\mvnw.cmd spring-boot:run
# In einem zweiten Terminal mit demselben Schlüssel:
Invoke-RestMethod -Method Patch -Uri 'http://127.0.0.1:8080/api/v1/stations/ST-001' -Headers @{'X-API-Key'=$env:FLOOD_ADMIN_KEY} -ContentType 'application/json' -Body '{"isActive":false}'
```

Header-Schlüssel werden zeitkonstant verglichen. Keine Cookie-Anmeldung, keine Freigabe für fremde Origins. Sicherheitsheader setzen unter anderem CSP und `nosniff`; Servertexte gelangen im Client über `.text()` in die Seite. Die Bindung ist standardmäßig ausschließlich Loopback. Für einen externen Betrieb wären HTTPS, Schlüsselrotation und Zugriffsbegrenzung zusätzlich einzurichten; die Demo startet bewusst lokal.

## Verteilte Erweiterung

Nach `package`:

```powershell
.\scripts\start-distributed.ps1 -Java "$env:JAVA_HOME\bin\java.exe"
```

```sh
sh scripts/start-distributed.sh
```

Vier JVM-Prozesse: ST-001 auf 8081, ST-002 auf 8082, ST-003 auf 8083, Zentrale auf 8080. Port 8080 muss frei sein, vorher den lokalen Einzelprozess beenden. Enter stoppt die gestarteten Prozesse. Logs liegen unter `logs/`.

Die Zentrale fragt die Stationen alle zehn Sekunden per HTTP ab, übernimmt Stationsdaten und bis zu 1000 jüngste Messungen und führt sie in derselben Repository-Struktur zusammen. Zeitstempel deduplizieren Messungen. Warnstufen werden in der Zentrale nochmals berechnet. Ausfälle blockieren nicht dauerhaft: Requests haben Timeouts; Messungen werden nach zwei Minuten zu UNKNOWN. Historie bleibt lesbar. Die erste Abfrage erfolgt nach dem ersten Intervall.

Stammdaten werden **am jeweiligen Stationsprozess** administriert; Schreibzugriffe auf die Zentrale ergeben 409. Deaktivierung über PATCH wird beim nächsten Poll übernommen. Offline-Stationen nach Akkuausfall müssen für diese Demo neu gestartet werden. Die Quellenliste ist in `monitor.sources` konfigurierbar. Die verteilte Variante ist eine lokale Prozessdemo ohne Service Discovery: Löschungen bereits bekannter Stationen werden nach einer erfolgreichen Quellenabfrage auch in der Zentrale nachvollzogen. Bei einer Unterbrechung von mehr als 1000 Messintervallen können nicht mehr alle fehlenden Messungen nachgeholt werden.

## Dokumentation und Abnahme

- [Analyse, Architektur, Datenmodell, Regeln und Reflexion](docs/ARCHITECTURE.md)
- [Cheatsheet mit beantworteten Fragestellungen und Quellen](CHEATSHEET.md)
- [AI-Prompt-Protokoll](docs/AI-PROMPTS.md)
- [Konfigurationsbeispiel ohne Zugangsdaten](config/application-example.properties)

## Tests

`./mvnw test` bzw. `.\mvnw.cmd test` prüft die API mit MockMvc und die Simulation mit festem Zufallsseed/fester Uhr. Die zeitgesteuerte Simulation und Demo-Historie sind in API-Tests deaktiviert. Testergebnisse stehen unter `target/surefire-reports/`.

## Bibliotheken

Java 21, Spring Boot 3.5.6, Jackson XML, Jakarta Validation, jQuery 3.7.1 (MIT; Lizenzhinweis im lokal mitgelieferten Skript). Build-Versionen sind festgelegt; dies ist keine Behauptung, dass sie jeweils die neueste Version darstellen.
