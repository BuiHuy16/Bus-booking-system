package com.example.busbooking.exception;

import com.example.busbooking.domain.exception.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private final ApiErrorWriter errors;
    public GlobalExceptionHandler(ApiErrorWriter errors) { this.errors = errors; }

    @ExceptionHandler(InvalidAuthInputException.class)
    public ResponseEntity<Object> invalidInput(InvalidAuthInputException error, WebRequest request) {
        return reply(request, 400, "VALIDATION_ERROR", "Request validation failed", Map.of(error.getField(), error.getMessage()), new HttpHeaders());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Object> duplicate(WebRequest request) {
        return reply(request, 409, "EMAIL_ALREADY_EXISTS", "Email is already registered", Map.of(), new HttpHeaders());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Object> unauthorized(WebRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return reply(request, 401, "UNAUTHORIZED", "Authentication is required", Map.of(), headers);
    }

    @ExceptionHandler(InvalidVerificationCodeException.class)
    public ResponseEntity<Object> invalidCode(WebRequest request) {
        return reply(request, 400, "INVALID_VERIFICATION_CODE", "Verification code is invalid or expired", Map.of(), new HttpHeaders());
    }

    @ExceptionHandler(VerificationRateLimitException.class)
    public ResponseEntity<Object> cooldown(WebRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "60");
        return reply(request, 429, "RATE_LIMITED", "Please wait before requesting another code", Map.of(), headers);
    }

    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<Object> delivery(WebRequest request) {
        return reply(request, 503, "EMAIL_DELIVERY_UNAVAILABLE",
                "Account is pending verification; request another code later", Map.of(), new HttpHeaders());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<Object> forbidden(WebRequest request) {
        return reply(request, 403, "FORBIDDEN", "You do not have permission to perform this operation", Map.of(), new HttpHeaders());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> notFound(ResourceNotFoundException error, WebRequest request) {
        return reply(request, 404, "NOT_FOUND", error.getMessage(), Map.of(), new HttpHeaders());
    }

    @ExceptionHandler(AccountNotEligibleException.class)
    public ResponseEntity<Object> ineligible(WebRequest request) {
        return reply(request, 409, "ACCOUNT_NOT_ELIGIBLE", "Target account must be active and email-verified before promotion", Map.of(), new HttpHeaders());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> business(BusinessException error, WebRequest request) {
        return reply(request, 400, "BUSINESS_ERROR", "Request cannot be processed", Map.of(), new HttpHeaders());
    }

    @ExceptionHandler(InsufficientSeatsException.class)
    public ResponseEntity<Object> insufficientSeats(InsufficientSeatsException error, WebRequest request) {
        return reply(request, 409, "INSUFFICIENT_SEATS", error.getMessage(), Map.of(), new HttpHeaders());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(WebRequest request) {
        // Do not serialize SQL, credentials, rejected values or exception messages.
        return reply(request, 500, "INTERNAL_ERROR", "An internal error occurred", Map.of(), new HttpHeaders());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> details = new TreeMap<>();
        error.getBindingResult().getFieldErrors().forEach(field ->
                details.putIfAbsent(field.getField(), Objects.requireNonNullElse(field.getDefaultMessage(), "Invalid value")));
        return reply(request, 400, "VALIDATION_ERROR", "Request validation failed", details, headers);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception error, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String code = switch (status.value()) {
            case 400 -> "INVALID_REQUEST";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "INVALID_REQUEST";
        };
        String message = status.is5xxServerError() ? "An internal error occurred" : "Request cannot be processed";
        return reply(request, status.value(), code, message, Map.of(), headers);
    }

    private ResponseEntity<Object> reply(WebRequest request, int status, String code, String message,
                                          Map<String, String> details, HttpHeaders source) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(source);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setCacheControl(CacheControl.noStore());
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        return new ResponseEntity<>(errors.body(path, status, code, message, details), headers, HttpStatusCode.valueOf(status));
    }
}
