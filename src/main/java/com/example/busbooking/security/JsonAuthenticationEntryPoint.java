package com.example.busbooking.security;

import com.example.busbooking.exception.ApiErrorWriter;
import jakarta.servlet.http.*;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import java.io.IOException;

public final class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ApiErrorWriter errors;
    public JsonAuthenticationEntryPoint(ApiErrorWriter errors) { this.errors = errors; }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        errors.write(request, response, 401, "UNAUTHORIZED", "Authentication is required");
    }
}
