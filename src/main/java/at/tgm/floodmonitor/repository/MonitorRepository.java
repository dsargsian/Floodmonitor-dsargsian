package at.tgm.floodmonitor.repository;

import at.tgm.floodmonitor.model.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class MonitorRepository {
    private final Map<String, Station> stations = new TreeMap<>();
    private final Map<String, NavigableMap<java.time.Instant, Measurement>> measurements = new HashMap<>();
    private final int maxHistory;
    public MonitorRepository(@Value("${measurements.max-history:10000}") int maxHistory) {
        if (maxHistory < 1) throw new IllegalArgumentException("max-history must be positive");
        this.maxHistory = maxHistory;
    }
    public synchronized List<Station> stations() { return List.copyOf(stations.values()); }
    public synchronized Optional<Station> station(String id) { return Optional.ofNullable(stations.get(id)); }
    public synchronized void save(Station station) { stations.put(station.id(), station); }
    public synchronized void delete(String id) { stations.remove(id); measurements.remove(id); }
    public synchronized void add(Measurement measurement) {
        if (!stations.containsKey(measurement.stationId())) return;
        var history = measurements.computeIfAbsent(measurement.stationId(), key -> new TreeMap<>());
        history.put(measurement.timestamp(), measurement);
        while (history.size() > maxHistory) history.pollFirstEntry();
    }
    public synchronized List<Measurement> history(String id) {
        var history = measurements.get(id);
        return history == null ? List.of() : List.copyOf(history.descendingMap().values());
    }
    public synchronized Optional<Measurement> latest(String id) {
        var history = measurements.get(id);
        return history == null || history.isEmpty() ? Optional.empty() : Optional.of(history.lastEntry().getValue());
    }
}
