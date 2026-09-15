package at.tgm.floodmonitor;

import at.tgm.floodmonitor.model.*;
import at.tgm.floodmonitor.repository.MonitorRepository;
import at.tgm.floodmonitor.service.*;
import java.time.*;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SimulationTest {
    private final Instant now = Instant.parse("2026-05-01T10:00:00Z");
    private final Station station = new Station("ST-001", "Test", "Donau", new Location(48,16), 100,200,300,true);
    private final WarningService warnings = new WarningService(Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(2));
    private Measurement measurement(double water, double rain, double battery, StationStatus status) {
        return new Measurement("ST-001", now, water, water * 2.4, rain, 16, battery, status, warnings.calculate(station, water));
    }
    @Test void normal() { assertThat(warnings.calculate(station, 199.9)).isEqualTo(WarningLevel.NORMAL); }
    @Test void warning() { assertThat(warnings.calculate(station, 200)).isEqualTo(WarningLevel.WARNING); }
    @Test void critical() { assertThat(warnings.calculate(station, 300)).isEqualTo(WarningLevel.CRITICAL); }
    @Test void unknown() {
        assertThat(warnings.calculate(station, Double.NaN)).isEqualTo(WarningLevel.UNKNOWN);
        assertThat(warnings.calculate(station, -1)).isEqualTo(WarningLevel.UNKNOWN);
        assertThat(warnings.current(station, null)).isEqualTo(WarningLevel.UNKNOWN);
        assertThat(warnings.current(station, measurement(350, 0, 0, StationStatus.OFFLINE))).isEqualTo(WarningLevel.UNKNOWN);
        Measurement stale = new Measurement("ST-001", now.minusSeconds(121), 350, 840,0,16,90,StationStatus.ONLINE,WarningLevel.CRITICAL);
        assertThat(warnings.current(station, stale)).isEqualTo(WarningLevel.UNKNOWN);
    }
    @Test void rainIncreasesWaterAndFlow() {
        var simulation = new SimulationService(new MonitorRepository(100), warnings);
        var dry = simulation.next(station, measurement(100, 0, 90, StationStatus.ONLINE), now, new Random(71));
        var wet = simulation.next(station, measurement(100, 40, 90, StationStatus.ONLINE), now, new Random(71));
        assertThat(wet.waterLevel()).isGreaterThan(dry.waterLevel());
        assertThat(wet.flowRate()).isGreaterThan(dry.flowRate());
        assertThat(wet.waterLevel()).isBetween(100.0, 110.0);
    }
    @Test void boundsAndBatteryMaintenance() {
        var simulation = new SimulationService(new MonitorRepository(100), warnings);
        Measurement value = measurement(0, 0, 9, StationStatus.ONLINE);
        Random random = new Random(71);
        assertThat(simulation.next(station, value, now, random).status()).isEqualTo(StationStatus.MAINTENANCE);
        for (int i = 0; i < 10000; i++) {
            value = simulation.next(station, value, now.plusSeconds(i), random);
            assertThat(value.waterLevel()).isGreaterThanOrEqualTo(0);
            assertThat(value.batteryLevel()).isBetween(0.0, 100.0);
            assertThat(value.flowRate()).isGreaterThanOrEqualTo(0);
        }
        assertThat(value.status()).isEqualTo(StationStatus.OFFLINE);
    }
    @Test void inactiveAndOfflineDoNotGenerateMeasurements() {
        var repository = new MonitorRepository(100);
        repository.save(new Station("ST-001", "Test", "Donau", station.location(), 100,200,300,false));
        var simulation = new SimulationService(repository, warnings);
        simulation.tick(now, new Random(71));
        assertThat(repository.history("ST-001")).isEmpty();
        repository.save(station);
        repository.add(measurement(100, 0, 0, StationStatus.OFFLINE));
        simulation.tick(now.plusSeconds(10), new Random(71));
        assertThat(repository.history("ST-001")).hasSize(1);
    }
    @Test void retentionAndDeduplication() {
        var repository = new MonitorRepository(2); repository.save(station);
        var simulation = new SimulationService(repository, warnings);
        for (int i = 0; i < 3; i++) simulation.tick(now.plusSeconds(i), new Random(i));
        assertThat(repository.history("ST-001")).hasSize(2);
        repository.add(repository.latest("ST-001").orElseThrow());
        assertThat(repository.history("ST-001")).hasSize(2);
        assertThat(repository.history("ST-001").get(0).timestamp()).isEqualTo(now.plusSeconds(2));
    }
}
