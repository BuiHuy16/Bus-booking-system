package com.example.busbooking.api.mapper;

import com.example.busbooking.api.dto.trip.TripResponse;
import com.example.busbooking.domain.model.Trip;
import org.springframework.stereotype.Component;

@Component
public class TripDtoMapper {

    public TripResponse toResponse(Trip trip) {
        if (trip == null) {
            return null;
        }

        return new TripResponse(
                trip.getId(),
                trip.getRouteId(),
                trip.getVehicleId(),
                trip.getDepartureTime(),
                trip.getArrivalTime(),
                trip.getPrice(),
                trip.getAvailableSeats(),
                trip.getStatus()
        );
    }
}
