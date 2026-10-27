package com.example.busbooking.api.controller;

import com.example.busbooking.api.dto.location.LocationResponse;
import com.example.busbooking.api.mapper.LocationDtoMapper;
import com.example.busbooking.business.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/locations")
public class LocationController {
    private final LocationService service;
    private final LocationDtoMapper mapper;

    public LocationController(LocationService service, LocationDtoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "List locations for trip search")
    public List<LocationResponse> list(@RequestParam(required = false) String q) {
        return service.search(q).stream().map(mapper::toResponse).toList();
    }
}
