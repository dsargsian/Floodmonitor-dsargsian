package at.tgm.floodmonitor.service;

import at.tgm.floodmonitor.model.*;
import at.tgm.floodmonitor.repository.MonitorRepository;
import java.time.*;
import java.util.Random;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {
    private final MonitorRepository repository;
    private final WarningService warnings;
    public SimulationService(MonitorRepository repository, WarningService warnings) {
        this.repository = repository; this.warnings = warnings;
    }
    /** Pure transition: injectable time and randomness make simulation reproducible in tests. */
    public Measurement next(Station station, Measurement previous, Instant time, Random random) {
        double oldWater = previous == null ? station.normalWaterLevel() : previous.waterLevel();
        double oldRain = previous == null ? 2 : previous.rainfall();
        double rain = Math.clamp(oldRain + random.nextDouble() * 4 - 1.8, 0, 60);
        double water = Math.max(0, oldWater + rain * 0.12
            - (oldWater - station.normalWaterLevel()) * 0.015 + random.nextDouble() * 2 - 1);
        double battery = Math.max(0, (previous == null ? 100 : previous.batteryLevel()) - 0.02);
        double temperature = Math.clamp((previous == null ? 16 : previous.temperature()) + random.nextDouble() - 0.5, -20, 40);
        StationStatus status = battery <= 0 ? StationStatus.OFFLINE : battery < 10 ? StationStatus.MAINTENANCE : StationStatus.ONLINE;
        water = round(water);
        return new Measurement(station.id(), time, water, round(water * 2.4), round(rain),
            round(temperature), Math.round(battery * 100.0) / 100.0, status,
            status == StationStatus.OFFLINE ? WarningLevel.UNKNOWN : warnings.calculate(station, water));
    }
    public void tick(Instant time, Random random) {
        synchronized (repository) {
            for (Station station : repository.stations()) {
                Measurement previous = repository.latest(station.id()).orElse(null);
                if (station.isActive() && (previous == null || previous.status() != StationStatus.OFFLINE))
                    repository.add(next(station, previous, time, random));
            }
        }
    }
    private double round(double value) { return Math.round(value * 10.0) / 10.0; }
}
