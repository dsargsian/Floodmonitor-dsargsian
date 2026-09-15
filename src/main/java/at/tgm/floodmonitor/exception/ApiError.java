package at.tgm.floodmonitor.exception;
import java.time.Instant;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
@JacksonXmlRootElement(localName = "error")
public record ApiError(Instant timestamp, int status, String error, String message, String path) {}
