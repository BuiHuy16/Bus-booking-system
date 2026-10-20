package com.example.busbooking.data.mapper;

import com.example.busbooking.data.entity.TripEntity;
import com.example.busbooking.domain.model.Trip;
import org.springframework.stereotype.Component;

@Component
public class TripPersistenceMapper {

    public Trip toDomain(TripEntity entity) {
        if (entity == null) {
            return null;
        }

        return new Trip(
                entity.getId(),
                entity.getRouteId(),
                entity.getVehicleId(),
                entity.getDepartureTime(),
                entity.getArrivalTime(),
                entity.getPrice(),
                entity.getAvailableSeats(),
                entity.getStatus()
        );
    }
}
