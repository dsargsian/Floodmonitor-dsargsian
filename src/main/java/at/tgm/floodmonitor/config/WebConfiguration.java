package at.tgm.floodmonitor.config;

import at.tgm.floodmonitor.exception.ApiException;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final String adminKey;
    private final String viewerKey;
    public WebConfiguration(@Value("${security.admin-key:}") String adminKey,
                            @Value("${security.viewer-key:}") String viewerKey) {
        if (!adminKey.isEmpty() && adminKey.equals(viewerKey)) throw new IllegalArgumentException("Admin and Viewer keys must differ");
        this.adminKey = adminKey; this.viewerKey = viewerKey;
    }
    @Override public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.favorParameter(false).defaultContentType(MediaType.APPLICATION_JSON);
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("Referrer-Policy", "no-referrer");
                response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'self'");
                if (!request.getRequestURI().startsWith("/api/")) return true;
                response.setHeader("Cache-Control", "no-store");
                String key = request.getHeader("X-API-Key");
                boolean admin = matches(key, adminKey);
                boolean viewer = matches(key, viewerKey);
                if (key != null && !admin && !viewer) throw new ApiException(HttpStatus.UNAUTHORIZED, "Ungültiger API-Schlüssel");
                if (Set.of("POST", "PATCH", "DELETE", "PUT").contains(request.getMethod()) && !admin) {
                    if (viewer) throw new ApiException(HttpStatus.FORBIDDEN, "Administration erfordert Admin-Rechte");
                    throw new ApiException(HttpStatus.UNAUTHORIZED, "Administration erfordert einen X-API-Key");
                }
                return true;
            }
        });
    }
    private static boolean matches(String supplied, String expected) {
        return supplied != null && !expected.isEmpty() && MessageDigest.isEqual(
            supplied.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
