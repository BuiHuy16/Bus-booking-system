package com.example.busbooking.data.mapper;

import com.example.busbooking.data.entity.VehicleEntity;
import com.example.busbooking.domain.enums.VehicleStatus;
import com.example.busbooking.domain.model.OperatorSummary;
import com.example.busbooking.domain.model.Vehicle;
import org.springframework.stereotype.Component;

@Component
public class VehiclePersistenceMapper {
    public Vehicle toDomain(VehicleEntity entity) {
        return new Vehicle(entity.getId(),
                new OperatorSummary(entity.getOperator().getId(), entity.getOperator().getName()),
                entity.getLicensePlate(), entity.getVehicleType(), entity.getSeatCapacity(),
                VehicleStatus.valueOf(entity.getStatus()), entity.getCreatedAt());
    }
}
