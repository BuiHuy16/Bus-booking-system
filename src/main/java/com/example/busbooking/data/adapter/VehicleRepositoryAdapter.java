package com.example.busbooking.data.adapter;

import com.example.busbooking.business.port.repository.VehicleRepository;
import com.example.busbooking.data.repository.SpringDataVehicleRepository;
import com.example.busbooking.domain.enums.VehicleStatus;
import com.example.busbooking.domain.model.Vehicle;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
public class VehicleRepositoryAdapter implements VehicleRepository {
    private final SpringDataVehicleRepository repository;

    public VehicleRepositoryAdapter(SpringDataVehicleRepository repository) {
        this.repository = repository;
    }

    public Optional<Vehicle> findById(Long id) {
        return repository.findById(id).map(entity -> new Vehicle(entity.getId(), entity.getSeatCapacity(),
                VehicleStatus.valueOf(entity.getStatus())));
    }
}
