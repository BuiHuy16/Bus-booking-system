package com.example.busbooking.business.port.repository;

import com.example.busbooking.domain.model.Vehicle;
import java.util.Optional;

public interface VehicleRepository {
    Optional<Vehicle> findById(Long id);
}
