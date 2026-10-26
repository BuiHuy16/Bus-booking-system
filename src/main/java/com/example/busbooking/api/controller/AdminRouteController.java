package com.example.busbooking.api.controller;

import com.example.busbooking.api.dto.route.*;
import com.example.busbooking.api.mapper.RouteDtoMapper;
import com.example.busbooking.business.service.RouteService;
import com.example.busbooking.domain.enums.RouteStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/admin/routes")
@SecurityRequirement(name = "bearerAuth")
public class AdminRouteController {
    private final RouteService service;
    private final RouteDtoMapper mapper;

    public AdminRouteController(RouteService service, RouteDtoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "List routes with optional endpoint and status filters")
    public List<RouteResponse> list(@RequestParam(required = false) Long originId,
                                    @RequestParam(required = false) Long destinationId,
                                    @RequestParam(required = false) RouteStatus status) {
        return service.search(originId, destinationId, status).stream().map(mapper::toResponse).toList();
    }

    @PostMapping
    @Operation(summary = "Create an active transport route")
    public ResponseEntity<RouteResponse> create(@Valid @RequestBody CreateRouteRequest request) {
        RouteResponse response = mapper.toResponse(service.create(request.toCommand()));
        return ResponseEntity.created(URI.create("/api/admin/routes/" + response.id())).body(response);
    }

    @PutMapping("/{routeId}")
    @Operation(summary = "Replace a transport route")
    public RouteResponse update(@PathVariable Long routeId, @Valid @RequestBody RouteRequest request) {
        return mapper.toResponse(service.update(routeId, request.toCommand()));
    }
}
