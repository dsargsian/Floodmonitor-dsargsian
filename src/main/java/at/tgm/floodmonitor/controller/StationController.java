package at.tgm.floodmonitor.controller;

import at.tgm.floodmonitor.model.*;
import at.tgm.floodmonitor.model.Responses.*;
import at.tgm.floodmonitor.service.MonitorService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1", produces = {"application/json", "application/xml"})
public class StationController {
    private final MonitorService service;
    public StationController(MonitorService service) { this.service = service; }
    @GetMapping("/stations")
    public Stations stations(@RequestParam(required = false) String river,
            @RequestParam(required = false) Boolean isActive, @RequestParam(required = false) WarningLevel warningLevel) {
        return new Stations(service.stations(river, isActive, warningLevel));
    }
    @GetMapping("/stations/{stationId}")
    public Station station(@PathVariable String stationId) { return service.station(stationId); }
    @GetMapping("/stations/{stationId}/measurements/latest")
    public ResponseEntity<Measurement> latest(@PathVariable String stationId) {
        Measurement measurement = service.latest(stationId);
        return measurement == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(measurement);
    }
    @GetMapping("/stations/{stationId}/measurements")
    public Measurements history(@PathVariable String stationId, @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to, @RequestParam(defaultValue = "100") Integer limit,
            @RequestParam(required = false) WarningLevel warningLevel) {
        return new Measurements(service.history(stationId, from, to, limit, warningLevel));
    }
    @GetMapping("/stations/{stationId}/statistics")
    public Statistics statistics(@PathVariable String stationId, @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to, @RequestParam(required = false) WarningLevel warningLevel) {
        return service.statistics(stationId, from, to, warningLevel);
    }
    @GetMapping("/alerts")
    public Alerts alerts(@RequestParam(required = false) String river, @RequestParam(required = false) WarningLevel warningLevel) {
        return new Alerts(service.alerts(river, warningLevel));
    }
    @PostMapping(value = "/stations", consumes = {"application/json", "application/xml"})
    public ResponseEntity<Station> create(@Valid @RequestBody Station station) {
        Station result = service.create(station);
        return ResponseEntity.created(URI.create("/api/v1/stations/" + result.id())).body(result);
    }
    @PatchMapping(value = "/stations/{stationId}", consumes = {"application/json", "application/xml"})
    public Station patch(@PathVariable String stationId, @RequestBody JsonNode patch) { return service.patch(stationId, patch); }
    @DeleteMapping("/stations/{stationId}")
    public ResponseEntity<Void> delete(@PathVariable String stationId) {
        service.delete(stationId); return ResponseEntity.noContent().build();
    }
}
