package com.example.busbooking.api.controller;

import com.example.busbooking.api.dto.user.*;
import com.example.busbooking.api.mapper.UserDtoMapper;
import com.example.busbooking.business.service.UserService;
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
@RequestMapping("/api/users/me")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User profile")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid profile fields", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class UserController {
    private final UserService users;
    private final UserDtoMapper mapper;
    public UserController(UserService users, UserDtoMapper mapper) { this.users = users; this.mapper = mapper; }

    @GetMapping
    @Operation(summary = "Read your own profile")
    public ResponseEntity<UserResponse> profile(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mapper.user(users.getProfile(principal.userId())));
    }

    @PutMapping
    @Operation(summary = "Replace your full name and Vietnamese contact phone")
    public ResponseEntity<UserResponse> update(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                               @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(mapper.user(users.updateProfile(principal.userId(), mapper.command(request))));
    }
}
