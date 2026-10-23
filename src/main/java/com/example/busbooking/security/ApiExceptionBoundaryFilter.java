package com.example.busbooking.security;

import com.example.busbooking.exception.ApiErrorWriter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public final class ApiExceptionBoundaryFilter extends OncePerRequestFilter {
    private final ApiErrorWriter errors;
    public ApiExceptionBoundaryFilter(ApiErrorWriter errors) { this.errors = errors; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try { chain.doFilter(request, response); }
        catch (RuntimeException failure) {
            if (response.isCommitted()) { throw failure; }
            errors.write(request, response, 500, "INTERNAL_ERROR", "An internal error occurred");
        }
    }
}
