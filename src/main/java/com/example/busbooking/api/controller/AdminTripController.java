package com.example.busbooking.api.controller;

import com.example.busbooking.api.dto.trip.*;
import com.example.busbooking.api.mapper.TripDtoMapper;
import com.example.busbooking.business.service.TripService;
import com.example.busbooking.domain.enums.TripStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/trips")
@SecurityRequirement(name = "bearerAuth")
public class AdminTripController {
    private final TripService service;
    private final TripDtoMapper mapper;

    public AdminTripController(TripService service, TripDtoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "List trips in all states with optional admin filters")
    public List<TripResponse> list(@RequestParam(required = false) Long routeId,
                                  @RequestParam(required = false) TripStatus status,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate) {
        return service.listForAdmin(routeId, status, departureDate).stream().map(mapper::toResponse).toList();
    }

    @PostMapping
    @Operation(summary = "Create a scheduled trip using the vehicle capacity")
    @ApiResponse(responseCode = "201", description = "Scheduled trip created")
    public ResponseEntity<TripResponse> create(@Valid @RequestBody TripRequest request) {
        var response = mapper.toResponse(service.create(request.toCommand()));
        return ResponseEntity.created(URI.create("/api/trips/" + response.id())).body(response);
    }

    @PutMapping("/{tripId}")
    @Operation(summary = "Update a scheduled trip while preserving confirmed booked seats")
    public TripResponse update(@PathVariable Long tripId, @Valid @RequestBody TripRequest request) {
        return mapper.toResponse(service.update(tripId, request.toCommand()));
    }

    @DeleteMapping("/{tripId}")
    @Operation(summary = "Soft cancel a trip", description = "Phase 1: confirmed bookings cause 409. "
            + "No refunds or booking cancellation. Repeated cancellation returns the cancelled trip.")
    public TripResponse cancel(@PathVariable Long tripId) {
        return mapper.toResponse(service.cancel(tripId));
    }
}
