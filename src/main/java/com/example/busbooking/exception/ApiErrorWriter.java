package com.example.busbooking.exception;

import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;

@Component
public class ApiErrorWriter {
    private final ObjectMapper mapper;
    private final Clock clock;

    public ApiErrorWriter(ObjectMapper mapper, Clock clock) { this.mapper = mapper; this.clock = clock; }

    public ErrorResponse body(String path, int status, String error, String message, Map<String, String> details) {
        return new ErrorResponse(clock.instant(), status, error, message, path, details);
    }

    public void write(HttpServletRequest request, HttpServletResponse response, int status,
                      String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getOutputStream().write(mapper.writeValueAsBytes(body(request.getRequestURI(), status, error, message, Map.of())));
    }
}
