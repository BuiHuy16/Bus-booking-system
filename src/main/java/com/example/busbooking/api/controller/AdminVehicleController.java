package com.example.busbooking.api.controller;

import com.example.busbooking.api.dto.vehicle.*;
import com.example.busbooking.api.mapper.VehicleDtoMapper;
import com.example.busbooking.business.service.VehicleService;
import com.example.busbooking.domain.enums.VehicleStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/admin/vehicles")
@SecurityRequirement(name = "bearerAuth")
public class AdminVehicleController {
    private final VehicleService service;
    private final VehicleDtoMapper mapper;

    public AdminVehicleController(VehicleService service, VehicleDtoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "List vehicles with optional text, operator and status filters")
    public List<VehicleResponse> list(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) Long operatorId,
                                      @RequestParam(required = false) VehicleStatus status) {
        return service.search(q, operatorId, status).stream().map(mapper::toResponse).toList();
    }

    @PostMapping
    @Operation(summary = "Create an active vehicle")
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody CreateVehicleRequest request) {
        VehicleResponse response = mapper.toResponse(service.create(request.toCommand()));
        return ResponseEntity.created(URI.create("/api/admin/vehicles/" + response.id())).body(response);
    }

    @PutMapping("/{vehicleId}")
    @Operation(summary = "Replace vehicle details and lifecycle status")
    public VehicleResponse update(@PathVariable Long vehicleId, @Valid @RequestBody VehicleRequest request) {
        return mapper.toResponse(service.update(vehicleId, request.toCommand()));
    }
}
