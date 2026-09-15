package at.tgm.floodmonitor.model;

import java.time.Instant;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "measurement")
public record Measurement(String stationId, Instant timestamp, double waterLevel,
        double flowRate, double rainfall, double temperature, double batteryLevel,
        StationStatus status, WarningLevel warningLevel) {
    public Measurement withWarning(WarningLevel warning) {
        return new Measurement(stationId, timestamp, waterLevel, flowRate, rainfall,
                temperature, batteryLevel, status, warning);
    }
}
