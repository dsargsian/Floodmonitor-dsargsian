package at.tgm.floodmonitor;

import at.tgm.floodmonitor.model.*;
import at.tgm.floodmonitor.repository.MonitorRepository;
import at.tgm.floodmonitor.service.WarningService;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"simulation.enabled=false", "simulation.seed-history=false",
    "security.admin-key=test-admin", "security.viewer-key=test-viewer"})
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired MonitorRepository repository;
    @Autowired WarningService warnings;
    private static final String BASE = "/api/v1/stations";
    private static final Instant TIME = Instant.parse("2026-05-01T10:00:00Z");
    private static final String NEW_STATION = """
        {"id":"ST-004","name":"Test","river":"Inn","location":{"latitude":48,"longitude":16},
        "normalWaterLevel":100,"warningWaterLevel":200,"criticalWaterLevel":300,"isActive":true}
        """;
    @BeforeEach void reset() {
        for (Station s : repository.stations()) repository.delete(s.id());
        repository.save(new Station("ST-001", "Wien", "Donau", new Location(48, 16), 100, 200, 300, true));
        repository.save(new Station("ST-002", "Krems", "Donau", new Location(48, 15), 100, 200, 300, true));
        repository.save(new Station("ST-003", "Steyr", "Enns", new Location(48, 14), 100, 200, 300, false));
        add("ST-001", TIME, 100);
        add("ST-001", TIME.plusSeconds(10), 200);
        add("ST-001", TIME.plusSeconds(20), 300);
    }
    private void add(String id, Instant time, double level) {
        repository.add(new Measurement(id, time, level, level * 2, 3, 16, 90,
            StationStatus.ONLINE, warnings.calculate(repository.station(id).orElseThrow(), level)));
    }
    @Test void allStationsDefaultJson() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.stations.length()").value(3)).andExpect(jsonPath("$.stations[0].isActive").value(true));
    }
    @Test void allStationsXml() throws Exception {
        mvc.perform(get(BASE).accept(MediaType.APPLICATION_XML)).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
            .andExpect(xpath("/stations/station").nodeCount(3)).andExpect(xpath("/stations/station[1]/id").string("ST-001"));
    }
    @Test void existingStation() throws Exception {
        mvc.perform(get(BASE + "/ST-001")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Wien"));
    }
    @Test void missingStationJsonAndXml() throws Exception {
        mvc.perform(get(BASE + "/ST-999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.path").value(BASE + "/ST-999")).andExpect(jsonPath("$.trace").doesNotExist());
        mvc.perform(get(BASE + "/ST-999").accept(MediaType.APPLICATION_XML)).andExpect(status().isNotFound())
            .andExpect(xpath("/error/status").string("404"));
    }
    @Test void latestAndEmptyStation() throws Exception {
        add("ST-001", Instant.now().minusSeconds(1), 350);
        mvc.perform(get(BASE + "/ST-001/measurements/latest")).andExpect(status().isOk())
            .andExpect(jsonPath("$.waterLevel").value(350)).andExpect(jsonPath("$.warningLevel").value("CRITICAL"));
        mvc.perform(get(BASE + "/ST-002/measurements/latest")).andExpect(status().isNoContent());
    }
    @Test void staleIsUnknown() throws Exception {
        mvc.perform(get(BASE + "/ST-001/measurements/latest")).andExpect(jsonPath("$.warningLevel").value("UNKNOWN"));
    }
    @Test void inclusiveTimeRange() throws Exception {
        mvc.perform(get(BASE + "/ST-001/measurements").param("from", TIME.plusSeconds(10).toString()).param("to", TIME.plusSeconds(20).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.measurements.length()").value(2))
            .andExpect(jsonPath("$.measurements[0].waterLevel").value(300));
    }
    @Test void reversedRange() throws Exception {
        mvc.perform(get(BASE + "/ST-001/measurements").param("from", TIME.plusSeconds(20).toString()).param("to", TIME.toString()))
            .andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings = {"-1", "0", "1001", "abc", "2147483648"})
    void invalidLimit(String limit) throws Exception {
        mvc.perform(get(BASE + "/ST-001/measurements").param("limit", limit)).andExpect(status().isBadRequest());
    }
    @Test void maximumLimitAndNewestFirst() throws Exception {
        for (int i = 0; i < 1100; i++) add("ST-001", TIME.plusSeconds(100 + i), 150);
        mvc.perform(get(BASE + "/ST-001/measurements").param("limit", "1000")).andExpect(status().isOk())
            .andExpect(jsonPath("$.measurements.length()").value(1000))
            .andExpect(jsonPath("$.measurements[0].timestamp").value(TIME.plusSeconds(1199).toString()));
    }
    @Test void invalidDateAndEnum() throws Exception {
        mvc.perform(get(BASE + "/ST-001/measurements").param("from", "yesterday")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/ST-001/measurements").param("from", "2026-05-01T10:00:00")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("warningLevel", "DANGER")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("isActive", "maybe")).andExpect(status().isBadRequest());
    }
    @Test void unsupportedFormat() throws Exception {
        mvc.perform(get(BASE).accept(MediaType.TEXT_PLAIN)).andExpect(status().isNotAcceptable()).andExpect(jsonPath("$.status").value(406));
    }
    @Test void filtersAndAlerts() throws Exception {
        add("ST-001", Instant.now().minusSeconds(1), 250);
        add("ST-002", Instant.now().minusSeconds(1), 350);
        mvc.perform(get(BASE).param("river", "Enns").param("isActive", "false")).andExpect(jsonPath("$.stations.length()").value(1));
        mvc.perform(get(BASE).param("warningLevel", "CRITICAL")).andExpect(jsonPath("$.stations[0].id").value("ST-002"));
        mvc.perform(get("/api/v1/alerts").param("river", "Donau").param("warningLevel", "WARNING"))
            .andExpect(jsonPath("$.alerts.length()").value(1)).andExpect(jsonPath("$.alerts[0].stationId").value("ST-001"));
        mvc.perform(get(BASE + "/ST-001/measurements").param("warningLevel", "NORMAL"))
            .andExpect(jsonPath("$.measurements.length()").value(1));
    }
    @Test void statisticsAndEmptyRange() throws Exception {
        mvc.perform(get(BASE + "/ST-001/statistics")).andExpect(jsonPath("$.count").value(3))
            .andExpect(jsonPath("$.minWaterLevel").value(100)).andExpect(jsonPath("$.maxWaterLevel").value(300))
            .andExpect(jsonPath("$.averageWaterLevel").value(200)).andExpect(jsonPath("$.averageFlowRate").value(400))
            .andExpect(jsonPath("$.totalRainfall").value(9)).andExpect(jsonPath("$.warningCount").value(1))
            .andExpect(jsonPath("$.criticalCount").value(1));
        mvc.perform(get(BASE + "/ST-002/statistics")).andExpect(jsonPath("$.count").value(0)).andExpect(jsonPath("$.minWaterLevel").isEmpty());
    }
    @Test void createPatchDeleteAndConflict() throws Exception {
        mvc.perform(post(BASE).header("X-API-Key", "test-admin").contentType(MediaType.APPLICATION_JSON).content(NEW_STATION))
            .andExpect(status().isCreated()).andExpect(header().string("Location", BASE + "/ST-004"));
        mvc.perform(post(BASE).header("X-API-Key", "test-admin").contentType(MediaType.APPLICATION_JSON).content(NEW_STATION))
            .andExpect(status().isConflict());
        mvc.perform(patch(BASE + "/ST-004").header("X-API-Key", "test-admin").contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(false)).andExpect(jsonPath("$.name").value("Test"));
        mvc.perform(delete(BASE + "/ST-004").header("X-API-Key", "test-admin")).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/ST-004")).andExpect(status().isNotFound());
    }
    @Test void xmlWrite() throws Exception {
        mvc.perform(post(BASE).header("X-API-Key", "test-admin").contentType(MediaType.APPLICATION_XML).accept(MediaType.APPLICATION_XML)
            .content("<station><id>ST-004</id><name>Test</name><river>Inn</river><location><latitude>48</latitude><longitude>16</longitude></location><normalWaterLevel>100</normalWaterLevel><warningWaterLevel>200</warningWaterLevel><criticalWaterLevel>300</criticalWaterLevel><isActive>true</isActive></station>"))
            .andExpect(status().isCreated()).andExpect(xpath("/station/id").string("ST-004"));
        mvc.perform(patch(BASE + "/ST-004").header("X-API-Key", "test-admin").contentType(MediaType.APPLICATION_XML)
            .content("<station><isActive>false</isActive></station>"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(false));
    }
    @Test void authorization() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(NEW_STATION)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE).header("X-API-Key", "test-viewer").contentType(MediaType.APPLICATION_JSON).content(NEW_STATION)).andExpect(status().isForbidden());
        mvc.perform(get(BASE).header("X-API-Key", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(delete(BASE + "/ST-001").header("X-API-Key", "test-viewer").accept(MediaType.APPLICATION_XML))
            .andExpect(status().isForbidden()).andExpect(xpath("/error/status").string("403"));
    }
    @Test void validationAndMediaType() throws Exception {
        mvc.perform(post(BASE).header("X-API-Key", "test-admin").contentType(MediaType.TEXT_PLAIN).content(NEW_STATION)).andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(BASE).header("X-API-Key", "test-admin").contentType(MediaType.APPLICATION_JSON).content(NEW_STATION.replace("300", "100"))).andExpect(status().isBadRequest());
        mvc.perform(post(BASE).header("X-API-Key", "test-admin").contentType(MediaType.APPLICATION_JSON).content(NEW_STATION.replace("48", "100"))).andExpect(status().isBadRequest());
        mvc.perform(patch(BASE + "/ST-001").header("X-API-Key", "test-admin").contentType(MediaType.APPLICATION_JSON).content("{\"warningLevel\":\"NORMAL\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(patch(BASE + "/ST-001").header("X-API-Key", "test-admin").contentType(MediaType.APPLICATION_JSON).content("{\"criticalWaterLevel\":100}"))
            .andExpect(status().isBadRequest());
    }
    @Test void missingOrNullRequiredFields() throws Exception {
        for (String body : new String[] {NEW_STATION.replace(",\"isActive\":true", ""),
                NEW_STATION.replace("\"normalWaterLevel\":100", "\"normalWaterLevel\":null"),
                NEW_STATION.replace("\"latitude\":48,", "")}) {
            mvc.perform(post(BASE).header("X-API-Key", "test-admin").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
    }
}
