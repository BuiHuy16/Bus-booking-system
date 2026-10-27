package com.example.busbooking.api.mapper;

import com.example.busbooking.api.dto.vehicle.VehicleResponse;
import com.example.busbooking.domain.model.Vehicle;
import org.springframework.stereotype.Component;

@Component
public class VehicleDtoMapper {
    public VehicleResponse toResponse(Vehicle vehicle) {
        return new VehicleResponse(vehicle.id(),
                new VehicleResponse.OperatorDetails(vehicle.operator().id(), vehicle.operator().name()),
                vehicle.licensePlate(), vehicle.vehicleType(), vehicle.seatCapacity(),
                vehicle.status(), vehicle.createdAt());
    }
}
