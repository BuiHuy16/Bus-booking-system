package com.example.busbooking.api.controller;

import com.example.busbooking.api.dto.user.*;
import com.example.busbooking.api.mapper.UserDtoMapper;
import com.example.busbooking.business.service.RoleManagementService;
import com.example.busbooking.domain.enums.RoleName;
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
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Super admin role management")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Only USER/ADMIN may be assigned", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Only SUPER_ADMIN may manage roles; the root role is immutable", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Target user not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Promotion requires active verified target", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class RoleManagementController {
    private final RoleManagementService roles;
    private final UserDtoMapper mapper;
    public RoleManagementController(RoleManagementService roles, UserDtoMapper mapper) { this.roles = roles; this.mapper = mapper; }

    @PutMapping("/api/admin/users/{userId}/role")
    @Operation(summary = "SUPER_ADMIN only: assign USER or ADMIN; never change the super admin")
    public ResponseEntity<UserResponse> changeRole(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable Long userId, @Valid @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(mapper.user(roles.changeRole(principal.userId(), userId, RoleName.valueOf(request.role().name()))));
    }
}
