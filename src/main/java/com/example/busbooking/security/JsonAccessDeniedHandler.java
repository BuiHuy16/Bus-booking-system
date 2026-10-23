package com.example.busbooking.security;

import com.example.busbooking.exception.ApiErrorWriter;
import jakarta.servlet.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import java.io.IOException;

public final class JsonAccessDeniedHandler implements AccessDeniedHandler {
    private final ApiErrorWriter errors;
    public JsonAccessDeniedHandler(ApiErrorWriter errors) { this.errors = errors; }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) throws IOException {
        errors.write(request, response, 403, "FORBIDDEN", "You do not have permission to access this resource");
    }
}
