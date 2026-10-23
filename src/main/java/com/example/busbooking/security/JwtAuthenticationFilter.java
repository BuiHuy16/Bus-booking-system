package com.example.busbooking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

// Construct in the SecurityFilterChain in phase 3, not as an auto-registered servlet filter.
public final class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Pattern BEARER = Pattern.compile("Bearer ([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private final JwtService jwt;
    private final CurrentUserLoader users;
    private final AuthenticationEntryPoint entryPoint;

    public JwtAuthenticationFilter(JwtService jwt, CurrentUserLoader users, AuthenticationEntryPoint entryPoint) {
        this.jwt = jwt;
        this.users = users;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean ownsContext = false;
        try {
            var headers = Collections.list(request.getHeaders("Authorization"));
            if (headers.size() > 1) { throw new JwtAuthenticationException(); }
            if (!headers.isEmpty()) {
                String header = headers.getFirst();
                String candidate = header.stripLeading();
                if (candidate.matches("(?is)Bearer(?:\\s.*)?")) {
                    var bearer = BEARER.matcher(header);
                    if (!bearer.matches()) { throw new JwtAuthenticationException(); }
                    var token = jwt.verify(bearer.group(1));
                    var principal = users.loadById(token.userId());
                    if (SecurityContextHolder.getContext().getAuthentication() == null) {
                        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        var context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(authentication);
                        SecurityContextHolder.setContext(context);
                        ownsContext = true;
                    }
                }
            }
        } catch (AuthenticationException invalid) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new JwtAuthenticationException());
            return;
        }
        // Downstream exceptions must not be mistaken for invalid tokens.
        try { chain.doFilter(request, response); }
        finally {
            if (ownsContext) { SecurityContextHolder.clearContext(); }
        }
    }
}
