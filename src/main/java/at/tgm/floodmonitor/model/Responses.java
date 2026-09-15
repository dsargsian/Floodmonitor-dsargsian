package at.tgm.floodmonitor.model;

import java.util.List;
import com.fasterxml.jackson.dataformat.xml.annotation.*;

/** Explicit collection envelopes keep JSON and XML structurally equivalent. */
public final class Responses {
    private Responses() {}
    @JacksonXmlRootElement(localName = "stations")
    public record Stations(@JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "station") List<Station> stations) {}
    @JacksonXmlRootElement(localName = "measurements")
    public record Measurements(@JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "measurement") List<Measurement> measurements) {}
    @JacksonXmlRootElement(localName = "alerts")
    public record Alerts(@JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "measurement") List<Measurement> alerts) {}
    @JacksonXmlRootElement(localName = "statistics")
    public record Statistics(String stationId, long count, Double minWaterLevel,
        Double maxWaterLevel, Double averageWaterLevel, Double averageFlowRate,
        double totalRainfall, long warningCount, long criticalCount) {}
}
