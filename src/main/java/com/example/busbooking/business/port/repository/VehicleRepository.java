package com.example.busbooking.business.port.repository;

import com.example.busbooking.domain.model.Vehicle;
import com.example.busbooking.domain.enums.VehicleStatus;
import java.util.List;
import java.util.Optional;

public interface VehicleRepository {
    List<Vehicle> search(String query, Long operatorId, VehicleStatus status);
    Optional<Vehicle> findById(Long id);
    Optional<Vehicle> findByIdForUpdate(Long id);
    boolean existsByLicensePlate(String licensePlate, Long excludedId);
    boolean hasAnyTrips(Long id);
    boolean hasOperationalTrips(Long id);
    Vehicle save(Vehicle vehicle);
}
