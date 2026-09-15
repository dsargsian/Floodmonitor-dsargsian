package at.tgm.floodmonitor.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final Clock clock;
    public ApiExceptionHandler(Clock clock) { this.clock = clock; }
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> api(ApiException ex, HttpServletRequest request) {
        return error(ex.status, ex.getMessage(), request);
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> badRequest(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Ungültige Eingabe. Prüfen Sie Feldtypen, Warnstufe, limit und ISO-8601-Zeitpunkte mit Zeitzone.", request);
    }
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> notAcceptable(Exception ex, HttpServletRequest request) {
        // No acceptable representation exists: use a documented JSON error fallback.
        return ResponseEntity.status(406).contentType(MediaType.APPLICATION_JSON)
            .body(body(HttpStatus.NOT_ACCEPTABLE, "Unterstützt werden application/json und application/xml", request));
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> unsupported(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type muss application/json oder application/xml sein", request);
    }
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> missing(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "Ressource nicht gefunden", request);
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(405).allow(ex.getSupportedHttpMethods().toArray(HttpMethod[]::new))
            .body(body(HttpStatus.METHOD_NOT_ALLOWED, "HTTP-Methode nicht unterstützt", request));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        LoggerFactory.getLogger(getClass()).error("Unerwarteter Serverfehler", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Interner Serverfehler", request);
    }
    private ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(body(status, message, request));
    }
    private ApiError body(HttpStatus status, String message, HttpServletRequest request) {
        return new ApiError(clock.instant(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
    }
}
