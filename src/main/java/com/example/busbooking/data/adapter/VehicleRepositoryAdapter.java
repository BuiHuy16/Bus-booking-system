package com.example.busbooking.data.adapter;

import com.example.busbooking.business.port.repository.VehicleRepository;
import com.example.busbooking.data.entity.VehicleEntity;
import com.example.busbooking.data.mapper.VehiclePersistenceMapper;
import com.example.busbooking.data.repository.SpringDataVehicleRepository;
import com.example.busbooking.domain.enums.VehicleStatus;
import com.example.busbooking.domain.exception.DuplicateVehicleException;
import com.example.busbooking.domain.exception.ResourceNotFoundException;
import com.example.busbooking.domain.model.Vehicle;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class VehicleRepositoryAdapter implements VehicleRepository {
    private final SpringDataVehicleRepository repository;
    private final VehiclePersistenceMapper mapper;
    private final EntityManager entityManager;

    public VehicleRepositoryAdapter(SpringDataVehicleRepository repository,
                                    VehiclePersistenceMapper mapper,
                                    EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public List<Vehicle> search(String query, Long operatorId, VehicleStatus status) {
        return repository.search(query, operatorId, status == null ? null : status.name()).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Vehicle> findById(Long id) {
        return repository.findDetailedById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Vehicle> findByIdForUpdate(Long id) {
        return repository.findByIdForUpdate(id).map(entity -> repository.findDetailedById(id)
                .map(mapper::toDomain).orElseThrow());
    }

    @Override
    public boolean existsByLicensePlate(String licensePlate, Long excludedId) {
        return excludedId == null ? repository.existsByLicensePlate(licensePlate)
                : repository.existsLicensePlateExcluding(licensePlate, excludedId);
    }

    @Override
    public boolean hasAnyTrips(Long id) { return repository.hasAnyTrips(id); }

    @Override
    public boolean hasOperationalTrips(Long id) { return repository.hasOperationalTrips(id); }

    @Override
    public Vehicle save(Vehicle vehicle) {
        VehicleEntity entity = vehicle.id() == null
                ? new VehicleEntity(vehicle.operator().id(), vehicle.licensePlate(), vehicle.vehicleType(),
                        vehicle.seatCapacity(), vehicle.status().name())
                : repository.findById(vehicle.id()).orElseThrow(() ->
                        new ResourceNotFoundException("Vehicle not found with id: " + vehicle.id()));
        entity.updateDetails(vehicle.operator().id(), vehicle.licensePlate(), vehicle.vehicleType(),
                vehicle.seatCapacity(), vehicle.status().name());
        try {
            repository.saveAndFlush(entity);
            entityManager.refresh(entity);
            return repository.findDetailedById(entity.getId()).map(mapper::toDomain).orElseThrow();
        } catch (DataIntegrityViolationException error) {
            if (hasConstraint(error, "uk_vehicles_license_plate")) throw new DuplicateVehicleException();
            throw error;
        }
    }

    private boolean hasConstraint(Throwable error, String constraint) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(constraint)) return true;
        }
        return false;
    }
}
