package com.example.busbooking.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class ApiErrorController implements ErrorController {
    private final ApiErrorWriter errors;
    public ApiErrorController(ApiErrorWriter errors) { this.errors = errors; }

    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        Object originalStatus = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = originalStatus instanceof Integer value && value >= 400 && value <= 599 ? value : 500;
        String code = switch (status) {
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 429 -> "RATE_LIMITED";
            default -> status >= 500 ? "INTERNAL_ERROR" : "INVALID_REQUEST";
        };
        Object originalPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = originalPath instanceof String value ? value.split("\\?", 2)[0] : request.getRequestURI();
        var response = ResponseEntity.status(status).cacheControl(CacheControl.noStore());
        if (status == 401) { response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer"); }
        return response.body(errors.body(path, status, code,
                status >= 500 ? "An internal error occurred" : "Request cannot be processed", Map.of()));
    }
}
