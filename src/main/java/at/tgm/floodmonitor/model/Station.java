package at.tgm.floodmonitor.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@JacksonXmlRootElement(localName = "station")
public record Station(
        @JsonProperty(required = true) @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,40}") String id,
        @JsonProperty(required = true) @NotBlank @Size(max = 100) String name,
        @JsonProperty(required = true) @NotBlank @Size(max = 100) String river,
        @JsonProperty(required = true) @NotNull @Valid Location location,
        @JsonProperty(required = true) @PositiveOrZero double normalWaterLevel,
        @JsonProperty(required = true) @Positive double warningWaterLevel,
        @JsonProperty(required = true) @Positive double criticalWaterLevel,
        @JsonProperty(required = true) boolean isActive) {}
