package at.tgm.floodmonitor.service;

import at.tgm.floodmonitor.model.*;
import at.tgm.floodmonitor.repository.MonitorRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MonitorJobs {
    private static final Logger LOG = LoggerFactory.getLogger(MonitorJobs.class);
    private final MonitorRepository repository;
    private final SimulationService simulation;
    private final WarningService warnings;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Random random = new Random();
    private final Map<String, Set<String>> sourceStations = new HashMap<>();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    @Value("${simulation.enabled:true}") private boolean enabled;
    @Value("${simulation.seed-history:true}") private boolean seedHistory;
    @Value("${simulation.interval:10000}") private long interval;
    @Value("${monitor.mode:local}") private String mode;
    @Value("${monitor.station-id:}") private String stationId;
    @Value("${monitor.sources:}") private List<String> sources;
    public MonitorJobs(MonitorRepository repository, SimulationService simulation, WarningService warnings,
                       ObjectMapper mapper, Clock clock) {
        this.repository = repository; this.simulation = simulation; this.warnings = warnings;
        this.mapper = mapper; this.clock = clock;
    }
    @PostConstruct
    void initialize() {
        if (!Set.of("local", "station", "central").contains(mode) || interval < 1)
            throw new IllegalArgumentException("Invalid monitor mode or simulation interval");
        var initial = List.of(
            new Station("ST-001", "Wien Nussdorf", "Donau", new Location(48.259, 16.367), 250, 320, 400, true),
            new Station("ST-002", "Krems Stein", "Donau", new Location(48.401, 15.587), 280, 350, 430, true),
            new Station("ST-003", "Steyr Zentrum", "Enns", new Location(48.040, 14.421), 160, 230, 300, true));
        if (mode.equals("station") && initial.stream().noneMatch(s -> s.id().equals(stationId)))
            throw new IllegalArgumentException("station mode requires ST-001, ST-002 or ST-003");
        for (int index = 0; index < initial.size(); index++) {
            Station s = initial.get(index);
            if (mode.equals("station") && !s.id().equals(stationId)) continue;
            repository.save(s);
            if (!seedHistory || mode.equals("central")) continue;
            double start = index == 0 ? s.normalWaterLevel() : index == 1 ? s.warningWaterLevel() + 25 : s.criticalWaterLevel() + 40;
            Instant now = clock.instant();
            Measurement previous = new Measurement(s.id(), now.minusMillis(36 * interval), start,
                start * 2.4, index * 7, 16, 95, StationStatus.ONLINE, warnings.calculate(s, start));
            Random seeded = new Random(71 + index);
            for (int i = 35; i >= 0; i--) {
                previous = simulation.next(s, previous, now.minusMillis(i * interval), seeded);
                repository.add(previous);
            }
        }
    }
    @Scheduled(fixedDelayString = "${simulation.interval:10000}", initialDelayString = "${simulation.interval:10000}")
    public void scheduledTick() {
        if (!enabled) return;
        if (mode.equals("central")) poll();
        else simulation.tick(clock.instant(), random);
    }
    public void poll() {
        for (String source : sources) {
            try {
                var stations = mapper.readValue(get(source + "/api/v1/stations"), Responses.Stations.class);
                Set<String> present = new HashSet<>();
                for (Station station : stations.stations()) present.add(station.id());
                for (String previous : sourceStations.getOrDefault(source, Set.of())) {
                    if (!present.contains(previous)) repository.delete(previous);
                }
                sourceStations.put(source, present);
                for (Station station : stations.stations()) {
                    repository.save(station);
                    // Retrieve recent history as well: missed polls do not silently lose readings.
                    var history = mapper.readValue(get(source + "/api/v1/stations/" + station.id() + "/measurements?limit=1000"), Responses.Measurements.class);
                    for (Measurement m : history.measurements()) {
                        if (!station.id().equals(m.stationId()) || m.timestamp() == null || m.status() == null
                            || m.timestamp().isAfter(clock.instant()) || !Double.isFinite(m.waterLevel()) || m.waterLevel() < 0
                            || !Double.isFinite(m.flowRate()) || m.flowRate() < 0 || !Double.isFinite(m.rainfall()) || m.rainfall() < 0
                            || !Double.isFinite(m.batteryLevel()) || m.batteryLevel() < 0 || m.batteryLevel() > 100
                            || !Double.isFinite(m.temperature())) continue;
                        repository.add(m.withWarning(m.status() == StationStatus.OFFLINE ? WarningLevel.UNKNOWN : warnings.calculate(station, m.waterLevel())));
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); return;
            } catch (Exception ex) {
                LOG.warn("Stationsprozess {} nicht erreichbar oder ungültige Antwort ({})", source, ex.getClass().getSimpleName());
            }
        }
    }
    private String get(String url) throws java.io.IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3))
            .header("Accept", "application/json").GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new java.io.IOException("HTTP " + response.statusCode());
        return response.body();
    }
}
