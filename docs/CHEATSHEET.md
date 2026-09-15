# Cheatsheet / Abgabegespräch

## 1. Hauptmerkmale von JSON

Textbasiertes, sprachunabhängiges Austauschformat mit Objekten (Name/Wert), Arrays und den Werten String, Zahl, Boolean, null, Objekt und Array. Kompakt und unmittelbar auf übliche Datenstrukturen abbildbar. JSON ist kein ausführbarer JavaScript-Code und wird hier geparst.

Quelle: [JSON.org](https://www.json.org/json-en.html).

## 2. Hauptmerkmale von XML

Textformat für hierarchische Dokumente mit Elementen und Attributen; Elementnamen sind frei definierbar. Ein wohlgeformtes Dokument hat genau ein Wurzelelement und korrekt geschachtelte Tags. XML kann über zusätzliche Schema-Technologien validiert werden. Im Projekt liefert Jackson XML die Serialisierung.

Quelle: [W3C XML 1.0](https://www.w3.org/TR/xml/).

## 3. Wie wird JSON oder XML ausgewählt?

Der Client sendet `Accept: application/json` oder `Accept: application/xml`. Spring MVC wählt anhand des Headers, der `produces`-Angabe und der vorhandenen MessageConverter das Antwortformat. `WebConfiguration.configureContentNegotiation` legt JSON als Standard fest. `Content-Type` bezeichnet dagegen das Format eines gesendeten Bodys. Nicht unterstütztes Accept führt zu 406.

Quellen: [Spring MVC Content Negotiation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/content-negotiation.html), Quellcode `config/WebConfiguration.java` und `controller/StationController.java`.

## 4. Was zeichnet REST aus?

REST ist ein Architekturstil mit Client/Server-Trennung, zustandslosen Requests, Cache-Regeln, einheitlicher Schnittstelle und Schichten. Ressourcen werden über Repräsentationen bearbeitet. HTTP-basierte REST-APIs lassen sich mit vielen Plattformen und Standardwerkzeugen konsumieren. Dieses Projekt verwendet Ressourcenpfade, HTTP-Methoden und Statuscodes; Hypermedia-Navigation ist nicht implementiert, daher ist es keine vollständige Umsetzung aller REST-Constraints.

Quelle: [Roy Fielding, Dissertation, Kapitel 5](https://ics.uci.edu/~fielding/pubs/dissertation/rest_arch_style.htm).

## 5. GET, POST, PUT, PATCH

| Methode | Semantik | Beispiel |
|---|---|---|
| GET | Repräsentation lesen; safe und idempotent | Station abrufen |
| POST | Ressourcenspezifische Verarbeitung, häufig Erstellung; nicht allgemein idempotent | Station erstellen, 201 + Location |
| PUT | Zielressource vollständig ersetzen; idempotent | Vollständige Stationsrepräsentation ersetzen; hier nicht angeboten |
| PATCH | Teiländerung; nicht allgemein idempotent | `{"isActive":false}`; diese konkrete Zuweisung ist idempotent |

Quellen: [MDN HTTP Methods](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Methods), [MDN PATCH](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Methods/PATCH).

## 6. Neue Request-Methode / neuer Pfad

Mapping und Parametertypen im `StationController` ergänzen, Logik in `MonitorService` oder einem neuen Service implementieren. Nur bei zusätzlichem Datenbedarf Repository/Modell anpassen. Neue Eingaben validieren, gegebenenfalls Fehlerstatus ergänzen; Authentisierungsregeln prüfen. API-Tests ergänzen, OpenAPI-Generator und Dokumentation aktualisieren, bei Bedarf Client erweitern.

Quelle: [Spring MVC Request Mapping](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html); projektintern `controller/StationController.java`, `service/MonitorService.java`, `scripts/generate-openapi.cjs`.

## 7. OpenAPI und Swagger

OpenAPI ist die standardisierte, sprachunabhängige Beschreibung einer HTTP-API. Swagger bezeichnet heute Werkzeuge rund um diese Beschreibung, etwa Swagger UI und Swagger Editor. Der frühere Swagger-Spezifikationsname wurde zu OpenAPI. Man kann Zweck und Zusammenhang vergleichen, aber Spezifikation und Werkzeug sind unterschiedliche Kategorien. Unsere `openapi.json` ist eine OpenAPI-3.0.3-Beschreibung und lässt sich in kompatible Werkzeuge importieren; Swagger UI ist kein benötigter Laufzeitbestandteil.

Quellen: [Swagger: What Is OpenAPI?](https://swagger.io/docs/specification/v3_0/about/), [OpenAPI 3.0.3](https://spec.openapis.org/oas/v3.0.3).

## 8. Fehler im vorgegebenen Code

Das Beispiel erstellt eine Station innerhalb eines GET-Handlers. GET soll keine vom Client angeforderte Zustandsänderung auslösen; Crawler, Prefetching oder Wiederholung könnten sonst unbeabsichtigt Daten anlegen. Die Korrektur ist ein POST auf `/stations` mit validiertem Body, Admin-Prüfung und einer 201-Antwort mit Location. Namen und Warnschwelle allein reichen außerdem nicht für das geforderte Stationsmodell.

Quellen: [MDN HTTP Methods](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Methods), projektintern `StationController.create` und `MonitorService.create`.

## Kurze Vorführung

1. `mvnw test`, dann `mvnw spring-boot:run`.
2. Stationskarte wählen, Format JSON/XML umschalten, Zeitraum und Warnstufe filtern.
3. Mit curl XML abrufen; ST-999, `limit=-1` und `Accept: text/plain` ausprobieren.
4. Admin-Schlüssel setzen, Station per PATCH deaktivieren; latest zeigt UNKNOWN.
5. Einzelprozess stoppen und vier Prozesse starten; Zentrale aggregiert die Stationen.

Alle externen Quellen wurden für die Ausarbeitung am 15.09.2026 herangezogen.
