package com.example.busbooking.api.mapper;

import com.example.busbooking.api.dto.trip.TripResponse;
import com.example.busbooking.domain.model.Trip;
import com.example.busbooking.domain.model.RouteSummary;
import com.example.busbooking.domain.model.VehicleSummary;
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
                toRouteDetails(trip.getRoute()),
                toVehicleDetails(trip.getVehicle()),
                trip.getRoute() == null ? null : trip.getRoute().origin(),
                trip.getRoute() == null ? null : trip.getRoute().destination(),
                trip.getVehicle() == null ? null : trip.getVehicle().vehicleType(),
                trip.getVehicle() == null ? null : trip.getVehicle().operatorName(),
                trip.getDepartureTime(),
                trip.getArrivalTime(),
                trip.getPrice(),
                trip.getAvailableSeats(),
                trip.getStatus()
        );
    }

    private TripResponse.RouteDetails toRouteDetails(RouteSummary route) {
        return route == null ? null : new TripResponse.RouteDetails(
                route.origin(), route.destination(), route.estimatedDurationMinutes());
    }

    private TripResponse.VehicleDetails toVehicleDetails(VehicleSummary vehicle) {
        return vehicle == null ? null : new TripResponse.VehicleDetails(
                vehicle.vehicleType(), vehicle.seatCapacity(), vehicle.operatorName());
    }
}
