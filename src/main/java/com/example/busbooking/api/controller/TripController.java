package com.example.busbooking.api.controller;

import com.example.busbooking.api.dto.trip.TripResponse;
import com.example.busbooking.api.mapper.TripDtoMapper;
import com.example.busbooking.business.service.TripService;
import com.example.busbooking.domain.model.Trip;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;
    private final TripDtoMapper tripDtoMapper;

    public TripController(
            TripService tripService,
            TripDtoMapper tripDtoMapper) {
        this.tripService = tripService;
        this.tripDtoMapper = tripDtoMapper;
    }

    @GetMapping
    public ResponseEntity<List<TripResponse>> searchTrips(
            @RequestParam(required = false) Long originId,
            @RequestParam(required = false) Long destinationId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate departureDate) {

        List<TripResponse> responses = tripService
                .searchTrips(originId, destinationId, departureDate)
                .stream()
                .map(tripDtoMapper::toResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{tripId}")
    public ResponseEntity<TripResponse> getTripById(
            @PathVariable Long tripId) {

        Trip trip = tripService.getTripById(tripId);

        return ResponseEntity.ok(
                tripDtoMapper.toResponse(trip)
        );
    }
}
