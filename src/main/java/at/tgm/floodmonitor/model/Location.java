package at.tgm.floodmonitor.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
public record Location(@JsonProperty(required = true) @DecimalMin("-90") @DecimalMax("90") double latitude,
                       @JsonProperty(required = true) @DecimalMin("-180") @DecimalMax("180") double longitude) {}
