// Regenerate the static, dependency-free OpenAPI document: node scripts/generate-openapi.cjs
const fs = require('node:fs');
const ref = name => ({$ref: '#/components/schemas/' + name});
const number = {type: 'number', format: 'double'};
const text = {type: 'string'};
const fields = {
    id: {type: 'string', pattern: '^[A-Za-z0-9-]{1,40}$'}, name: {type: 'string', minLength: 1, maxLength: 100},
    river: {type: 'string', minLength: 1, maxLength: 100}, location: ref('Location'),
    normalWaterLevel: {...number, minimum: 0}, warningWaterLevel: {...number, minimum: 0, exclusiveMinimum: true},
    criticalWaterLevel: {...number, minimum: 0, exclusiveMinimum: true}, isActive: {type: 'boolean'}
};
const schemas = {
    WarningLevel: {type: 'string', enum: ['NORMAL', 'WARNING', 'CRITICAL', 'UNKNOWN']},
    StationStatus: {type: 'string', enum: ['ONLINE', 'MAINTENANCE', 'OFFLINE']},
    Location: {type: 'object', additionalProperties: false, required: ['latitude', 'longitude'], properties: {
        latitude: {...number, minimum: -90, maximum: 90}, longitude: {...number, minimum: -180, maximum: 180}}},
    Station: {type: 'object', additionalProperties: false, xml: {name: 'station'}, required: Object.keys(fields), properties: fields,
        description: '0 <= normalWaterLevel < warningWaterLevel < criticalWaterLevel. Wasserstände in cm.'},
    StationPatch: {type: 'object', additionalProperties: false, minProperties: 1, xml: {name: 'station'},
        properties: Object.fromEntries(Object.entries(fields).filter(([key]) => key !== 'id')),
        description: 'Teilaktualisierung; ausgelassene Felder bleiben erhalten. null und id sind unzulässig. location wird vollständig ersetzt.'},
    Measurement: {type: 'object', xml: {name: 'measurement'}, properties: {
        stationId: text, timestamp: {type: 'string', format: 'date-time'}, waterLevel: {...number, description: 'cm'},
        flowRate: {...number, description: 'm³/s'}, rainfall: {...number, description: 'Stundenwert in mm/h'}, temperature: {...number, description: '°C'},
        batteryLevel: {...number, minimum: 0, maximum: 100}, status: ref('StationStatus'), warningLevel: ref('WarningLevel')}},
    Statistics: {type: 'object', xml: {name: 'statistics'}, properties: {
        stationId: text, count: {type: 'integer'}, minWaterLevel: {...number, nullable: true}, maxWaterLevel: {...number, nullable: true},
        averageWaterLevel: {...number, nullable: true}, averageFlowRate: {...number, nullable: true},
        totalRainfall: {...number, description: 'Arithmetische Summe der Stundenwerte (mm/h), keine zeitliche Integration.'},
        warningCount: {type: 'integer', description: 'Nur WARNING'}, criticalCount: {type: 'integer', description: 'Nur CRITICAL'}}},
    ApiError: {type: 'object', xml: {name: 'error'}, properties: {
        timestamp: {type: 'string', format: 'date-time'}, status: {type: 'integer'}, error: text, message: text, path: text}}
};
for (const [name, field, item] of [['Stations', 'stations', 'Station'], ['Measurements', 'measurements', 'Measurement'], ['Alerts', 'alerts', 'Measurement']]) {
    schemas[name] = {type: 'object', xml: {name: field}, properties: {
        [field]: {type: 'array', xml: {wrapped: false}, items: {...ref(item), xml: {name: item.toLowerCase()}}}
    }};
}
const content = schema => ({'application/json': {schema: ref(schema)}, 'application/xml': {schema: ref(schema)}});
const param = (name, schema, description) => ({name, in: 'query', required: false, schema, description});
const river = param('river', text, 'Flussname, Groß-/Kleinschreibung egal');
const warning = param('warningLevel', ref('WarningLevel'), 'Exakte Warnstufe, Großbuchstaben');
const range = ['from', 'to'].map(name => param(name, {type: 'string', format: 'date-time'}, 'Inklusive Grenze, ISO-8601 mit Zeitzone; from <= to'));
const errors = {
    400: 'Ungültige Felder, Datumsformate, Zeiträume, Warnstufen oder Limits',
    401: 'API-Schlüssel fehlt für Schreibzugriff oder ist ungültig', 403: 'Viewer darf nicht administrieren',
    404: 'Station oder Pfad nicht gefunden', 406: 'Accept nicht unterstützt; Fehlerantwort ausnahmsweise JSON',
    409: 'Doppelte ID oder Verwaltung an Zentrale statt Stationsprozess',
    415: 'Content-Type nicht unterstützt', 500: 'Interner Fehler ohne Stacktrace im Response'
};
function operation(summary, schema, parameters = [], write = false) {
    const responses = {'200': {description: 'Erfolgreich', content: content(schema)}};
    for (const code of write ? [400,401,403,404,406,409,415,500] : [400,401,404,406,500]) {
        responses[code] = {description: errors[code], content: code === 406 ? {'application/json': {schema: ref('ApiError')}} : content('ApiError')};
    }
    return {summary, parameters, security: write ? [{ApiKey: []}] : [], responses};
}
const paths = {
    '/stations': {get: operation('Stationen auflisten', 'Stations', [river, param('isActive', {type:'boolean'}, 'Aktivitätsfilter'), warning]),
        post: operation('Station anlegen (Admin)', 'Station', [], true)},
    '/stations/{stationId}': {get: operation('Station abrufen', 'Station'), patch: operation('Station teilweise ändern (Admin)', 'Station', [], true), delete: operation('Station samt Historie löschen (Admin)', 'Station', [], true)},
    '/stations/{stationId}/measurements/latest': {get: operation('Neueste Messung; veraltet, offline oder inaktiv ergibt UNKNOWN', 'Measurement')},
    '/stations/{stationId}/measurements': {get: operation('Historie, absteigend nach Zeitpunkt', 'Measurements', [...range, param('limit', {type:'integer', minimum:1, maximum:1000, default:100}, 'Maximale Ergebnisanzahl'), warning])},
    '/stations/{stationId}/statistics': {get: operation('Statistik über alle gespeicherten Treffer; unabhängig vom Historienlimit', 'Statistics', [...range, warning])},
    '/alerts': {get: operation('Nur aktuelle WARNING- und CRITICAL-Messungen; NORMAL/UNKNOWN als Filter liefern leere Liste', 'Alerts', [river, warning])}
};
for (const [path, value] of Object.entries(paths)) {
    if (path.includes('{stationId}')) value.parameters = [{name:'stationId', in:'path', required:true, schema:text, description:'Zum Beispiel ST-001'}];
}
paths['/stations'].post.requestBody = {required:true, content:content('Station')};
paths['/stations'].post.responses['201'] = {description:'Erstellt', headers:{Location:{description:'URL der neuen Station',schema:text}}, content:content('Station')};
delete paths['/stations'].post.responses['200'];
paths['/stations/{stationId}'].patch.requestBody = {required:true, content:content('StationPatch')};
paths['/stations/{stationId}'].delete.responses['204'] = {description:'Gelöscht, kein Body'};
delete paths['/stations/{stationId}'].delete.responses['200'];
paths['/stations/{stationId}/measurements/latest'].get.responses['204'] = {description:'Station vorhanden, aber noch keine Messung; Warnzustand UNKNOWN'};
fs.writeFileSync('src/main/resources/static/openapi.json', JSON.stringify({openapi:'3.0.3', info:{title:'Floodmonitor REST API',version:'1.0.0',
    description:'JSON standardmäßig, XML via Accept. Öffentlicher Lesezugriff als Viewer; X-API-Key für Admin-Schreibzugriffe. Daten sind simuliert und im Arbeitsspeicher. Fehler im selben Format, außer 406 mit JSON-Fallback.'},
    servers:[{url:'/api/v1'}], paths, components:{schemas,securitySchemes:{ApiKey:{type:'apiKey',in:'header',name:'X-API-Key',description:'Admin-Schlüssel aus FLOOD_ADMIN_KEY; optionaler Viewer-Schlüssel aus FLOOD_VIEWER_KEY besitzt keine Schreibrechte.'}}}}, null, 2) + '\n');
