package at.tgm.floodmonitor.service;

import at.tgm.floodmonitor.exception.ApiException;
import at.tgm.floodmonitor.model.*;
import at.tgm.floodmonitor.model.Responses.Statistics;
import at.tgm.floodmonitor.repository.MonitorRepository;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MonitorService {
    public static final int MAX_LIMIT = 1000;
    private final MonitorRepository repository;
    private final WarningService warnings;
    private final Validator validator;
    private final ObjectMapper mapper;
    private final String mode;
    public MonitorService(MonitorRepository repository, WarningService warnings, Validator validator,
                          ObjectMapper mapper, @Value("${monitor.mode:local}") String mode) {
        this.repository = repository; this.warnings = warnings; this.validator = validator;
        this.mapper = mapper; this.mode = mode;
    }
    public Station station(String id) {
        return repository.station(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Station " + id + " wurde nicht gefunden"));
    }
    public List<Station> stations(String river, Boolean active, WarningLevel level) {
        return repository.stations().stream()
            .filter(s -> river == null || s.river().equalsIgnoreCase(river))
            .filter(s -> active == null || s.isActive() == active)
            .filter(s -> level == null || warnings.current(s, repository.latest(s.id()).orElse(null)) == level).toList();
    }
    public Measurement latest(String id) {
        Station s = station(id);
        return repository.latest(id).map(m -> m.withWarning(warnings.current(s, m))).orElse(null);
    }
    public List<Measurement> history(String id, Instant from, Instant to, Integer limit, WarningLevel level) {
        station(id); validateRange(from, to);
        if (limit != null && (limit < 1 || limit > MAX_LIMIT))
            throw new ApiException(HttpStatus.BAD_REQUEST, "limit muss zwischen 1 und " + MAX_LIMIT + " liegen");
        return repository.history(id).stream()
            .filter(m -> from == null || !m.timestamp().isBefore(from))
            .filter(m -> to == null || !m.timestamp().isAfter(to))
            .filter(m -> level == null || m.warningLevel() == level)
            .limit(limit == null ? MAX_LIMIT : limit).toList();
    }
    public List<Measurement> alerts(String river, WarningLevel level) {
        return stations(river, null, null).stream().map(s -> latest(s.id())).filter(Objects::nonNull)
            .filter(m -> m.warningLevel() == WarningLevel.WARNING || m.warningLevel() == WarningLevel.CRITICAL)
            .filter(m -> level == null || m.warningLevel() == level).toList();
    }
    public Statistics statistics(String id, Instant from, Instant to, WarningLevel level) {
        station(id); validateRange(from, to);
        var rows = repository.history(id).stream()
            .filter(m -> from == null || !m.timestamp().isBefore(from))
            .filter(m -> to == null || !m.timestamp().isAfter(to))
            .filter(m -> level == null || m.warningLevel() == level).toList();
        var water = rows.stream().mapToDouble(Measurement::waterLevel).summaryStatistics();
        return new Statistics(id, rows.size(), rows.isEmpty() ? null : water.getMin(),
            rows.isEmpty() ? null : water.getMax(), rows.isEmpty() ? null : water.getAverage(),
            rows.isEmpty() ? null : rows.stream().mapToDouble(Measurement::flowRate).average().orElse(0),
            rows.stream().mapToDouble(Measurement::rainfall).sum(),
            rows.stream().filter(m -> m.warningLevel() == WarningLevel.WARNING).count(),
            rows.stream().filter(m -> m.warningLevel() == WarningLevel.CRITICAL).count());
    }
    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to))
            throw new ApiException(HttpStatus.BAD_REQUEST, "from darf nicht nach to liegen");
    }
    private void writable() {
        if (mode.equals("central")) throw new ApiException(HttpStatus.CONFLICT,
            "Stationen im verteilten Betrieb am jeweiligen Stationsprozess verwalten");
    }
    private void validate(Station station) {
        if (!validator.validate(station).isEmpty() || !Double.isFinite(station.normalWaterLevel())
            || !Double.isFinite(station.warningWaterLevel()) || !Double.isFinite(station.criticalWaterLevel())
            || station.location() == null || !Double.isFinite(station.location().latitude())
            || !Double.isFinite(station.location().longitude())
            || station.normalWaterLevel() >= station.warningWaterLevel()
            || station.warningWaterLevel() >= station.criticalWaterLevel())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Stationsdaten ungültig: 0 <= Normalwert < Warnwert < kritischer Wert, gültiger Name und Standort erforderlich");
    }
    public Station create(Station station) {
        writable(); validate(station);
        synchronized (repository) {
            if (repository.station(station.id()).isPresent()) throw new ApiException(HttpStatus.CONFLICT, "Stations-ID existiert bereits");
            repository.save(station);
        }
        return station;
    }
    public Station patch(String id, JsonNode patch) {
        writable();
        synchronized (repository) {
            Station old = station(id);
            if (!patch.isObject() || patch.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "PATCH benötigt ein Objekt mit mindestens einem Feld");
            Set<String> allowed = Set.of("name", "river", "location", "normalWaterLevel", "warningWaterLevel", "criticalWaterLevel", "isActive");
            var merged = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(old);
            patch.fields().forEachRemaining(entry -> {
                if (!allowed.contains(entry.getKey()) || entry.getValue().isNull())
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Unbekanntes, unveränderliches oder leeres Feld: " + entry.getKey());
                merged.set(entry.getKey(), entry.getValue());
            });
            try {
                Station updated = mapper.treeToValue(merged, Station.class);
                validate(updated); repository.save(updated); return updated;
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Ungültige Feldtypen");
            }
        }
    }
    public void delete(String id) {
        writable(); synchronized (repository) { station(id); repository.delete(id); }
    }
}
