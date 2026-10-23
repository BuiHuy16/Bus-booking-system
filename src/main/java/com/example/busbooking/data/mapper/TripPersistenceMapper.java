package com.example.busbooking.data.mapper;

import com.example.busbooking.data.entity.TripEntity;
import com.example.busbooking.domain.model.Trip;
import com.example.busbooking.domain.model.RouteSummary;
import com.example.busbooking.domain.model.VehicleSummary;
import org.springframework.stereotype.Component;

@Component
public class TripPersistenceMapper {

    public Trip toDomain(TripEntity entity) {
        if (entity == null) {
            return null;
        }

        RouteSummary route = entity.getRoute() == null ? null : new RouteSummary(
                entity.getRoute().getOrigin().getName(),
                entity.getRoute().getDestination().getName(),
                entity.getRoute().getEstimatedDurationMinutes());
        VehicleSummary vehicle = entity.getVehicle() == null ? null : new VehicleSummary(
                entity.getVehicle().getVehicleType(),
                entity.getVehicle().getSeatCapacity(),
                entity.getVehicle().getOperator().getName());

        return new Trip(
                entity.getId(),
                entity.getRouteId(),
                entity.getVehicleId(),
                entity.getDepartureTime(),
                entity.getArrivalTime(),
                entity.getPrice(),
                entity.getAvailableSeats(),
                entity.getStatus(),
                route,
                vehicle
        );
    }
}
