package at.tgm.floodmonitor.service;

import at.tgm.floodmonitor.model.*;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WarningService {
    private final Clock clock;
    private final Duration staleAfter;
    public WarningService(Clock clock, @Value("${measurements.stale-after:PT2M}") Duration staleAfter) {
        this.clock = clock; this.staleAfter = staleAfter;
        if (staleAfter.isNegative() || staleAfter.isZero()) throw new IllegalArgumentException("stale-after must be positive");
    }
    public WarningLevel calculate(Station station, double waterLevel) {
        if (!Double.isFinite(waterLevel) || waterLevel < 0) return WarningLevel.UNKNOWN;
        if (waterLevel >= station.criticalWaterLevel()) return WarningLevel.CRITICAL;
        if (waterLevel >= station.warningWaterLevel()) return WarningLevel.WARNING;
        return WarningLevel.NORMAL;
    }
    public WarningLevel current(Station station, Measurement measurement) {
        if (!station.isActive() || measurement == null || measurement.status() == StationStatus.OFFLINE
                || measurement.timestamp().isBefore(clock.instant().minus(staleAfter))
                || measurement.timestamp().isAfter(clock.instant())) return WarningLevel.UNKNOWN;
        return calculate(station, measurement.waterLevel());
    }
}
