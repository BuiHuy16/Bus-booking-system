package com.example.busbooking.api.controller;

import com.example.busbooking.api.dto.location.LocationRequest;
import com.example.busbooking.api.dto.location.LocationResponse;
import com.example.busbooking.api.mapper.LocationDtoMapper;
import com.example.busbooking.business.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/admin/locations")
@SecurityRequirement(name = "bearerAuth")
public class AdminLocationController {
    private final LocationService service;
    private final LocationDtoMapper mapper;

    public AdminLocationController(LocationService service, LocationDtoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "List locations with optional text search")
    public List<LocationResponse> list(@RequestParam(required = false) String q) {
        return service.search(q).stream().map(mapper::toResponse).toList();
    }

    @PostMapping
    @Operation(summary = "Create a location")
    public ResponseEntity<LocationResponse> create(@Valid @RequestBody LocationRequest request) {
        LocationResponse response = mapper.toResponse(service.create(request.toCommand()));
        return ResponseEntity.created(URI.create("/api/admin/locations/" + response.id())).body(response);
    }

    @PutMapping("/{locationId}")
    @Operation(summary = "Replace a location")
    public LocationResponse update(@PathVariable Long locationId,
                                   @Valid @RequestBody LocationRequest request) {
        return mapper.toResponse(service.update(locationId, request.toCommand()));
    }
}
