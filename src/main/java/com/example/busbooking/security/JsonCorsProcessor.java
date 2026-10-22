package com.example.busbooking.security;

import com.example.busbooking.exception.ApiErrorWriter;
import jakarta.servlet.http.*;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.*;
import java.io.IOException;

public final class JsonCorsProcessor implements CorsProcessor {
    private final ApiErrorWriter errors;
    public JsonCorsProcessor(ApiErrorWriter errors) { this.errors = errors; }

    @Override
    public boolean processRequest(CorsConfiguration config, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        var processor = new DefaultCorsProcessor() {
            @Override
            protected void rejectRequest(ServerHttpResponse serverResponse) throws IOException {
                errors.write(request, response, 403, "FORBIDDEN", "Cross-origin request is not allowed");
                serverResponse.flush();
            }
        };
        return processor.processRequest(config, request, response);
    }
}
