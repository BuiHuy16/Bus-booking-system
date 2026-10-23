package com.example.busbooking.api.controller;

import com.example.busbooking.api.dto.auth.*;
import com.example.busbooking.api.dto.user.UserResponse;
import com.example.busbooking.api.mapper.AuthDtoMapper;
import com.example.busbooking.business.service.AuthService;
import com.example.busbooking.business.service.EmailVerificationService;
import com.example.busbooking.security.AuthenticatedPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.*;
import com.example.busbooking.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Email OTP registration and JWT access")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid request or verification code", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication failed", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Request limit or resend cooldown", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class AuthController {
    private final AuthService auth;
    private final EmailVerificationService verification;
    private final AuthDtoMapper mapper;

    public AuthController(AuthService auth, EmailVerificationService verification, AuthDtoMapper mapper) {
        this.auth = auth;
        this.verification = verification;
        this.mapper = mapper;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponses({
            @ApiResponse(responseCode = "409", description = "Email already registered", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "SMTP unavailable; account remains pending", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @Operation(summary = "Register a USER and send an email verification code")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(mapper.user(auth.register(mapper.command(request))));
    }

    @PostMapping("/login")
    @Operation(summary = "Login after email verification and receive an access JWT")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mapper.login(auth.login(mapper.command(request))));
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Verify a six-digit email code; does not issue a JWT")
    public ResponseEntity<Void> verify(@Valid @RequestBody VerifyEmailRequest request) {
        verification.verify(mapper.command(request));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @ApiResponse(responseCode = "503", description = "SMTP unavailable", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @Operation(summary = "Request another code if the account is eligible")
    public ResponseEntity<MessageResponse> resend(@Valid @RequestBody ResendVerificationRequest request) {
        verification.resend(request.email());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(new MessageResponse("If the account is eligible, a verification email has been sent"));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Read the current authenticated account")
    public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(mapper.currentUser(auth.currentUser(principal.userId())));
    }
}
