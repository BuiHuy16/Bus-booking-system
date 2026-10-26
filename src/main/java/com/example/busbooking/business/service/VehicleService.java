package com.example.busbooking.business.service;

import com.example.busbooking.business.command.SaveVehicleCommand;
import com.example.busbooking.business.port.repository.OperatorRepository;
import com.example.busbooking.business.port.repository.VehicleRepository;
import com.example.busbooking.domain.enums.OperatorStatus;
import com.example.busbooking.domain.enums.VehicleStatus;
import com.example.busbooking.domain.exception.*;
import com.example.busbooking.domain.model.Operator;
import com.example.busbooking.domain.model.OperatorSummary;
import com.example.busbooking.domain.model.Vehicle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;

@Service
public class VehicleService {
    private final VehicleRepository vehicles;
    private final OperatorRepository operators;

    public VehicleService(VehicleRepository vehicles, OperatorRepository operators) {
        this.vehicles = vehicles;
        this.operators = operators;
    }

    @Transactional(readOnly = true)
    public List<Vehicle> search(String query, Long operatorId, VehicleStatus status) {
        if (operatorId != null && operatorId <= 0) {
            throw new BusinessException("operatorId must be a positive number");
        }
        return vehicles.search(normalizeQuery(query), operatorId, status);
    }

    @Transactional
    public Vehicle create(SaveVehicleCommand command) {
        Vehicle vehicle = validated(command, null, VehicleStatus.ACTIVE, null, true);
        requireUnique(vehicle, null);
        return vehicles.save(vehicle);
    }

    @Transactional
    public Vehicle update(Long vehicleId, SaveVehicleCommand command) {
        if (vehicleId == null || vehicleId <= 0) {
            throw new ResourceNotFoundException("Vehicle not found with id: " + vehicleId);
        }
        Vehicle current = vehicles.findByIdForUpdate(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + vehicleId));
        if (command == null || command.status() == null) throw new BusinessException("status is required");
        boolean operatorChanged = command.operatorId() != null
                && !current.operator().id().equals(command.operatorId());
        boolean needsActiveOperator = operatorChanged || command.status() != VehicleStatus.INACTIVE;
        Vehicle replacement = validated(command, vehicleId, command.status(), current.createdAt(), needsActiveOperator);
        boolean identityChanged = operatorChanged
                || !current.licensePlate().equals(replacement.licensePlate())
                || !current.vehicleType().equals(replacement.vehicleType());
        if (identityChanged && vehicles.hasAnyTrips(vehicleId)) {
            throw new VehicleIdentityLockedException();
        }
        requireUnique(replacement, vehicleId);
        boolean capacityChanged = current.seatCapacity() != replacement.seatCapacity();
        boolean leavesActive = current.status() == VehicleStatus.ACTIVE
                && replacement.status() != VehicleStatus.ACTIVE;
        if ((capacityChanged || leavesActive) && vehicles.hasOperationalTrips(vehicleId)) {
            throw new VehicleInUseException();
        }
        return vehicles.save(replacement);
    }

    private Vehicle validated(SaveVehicleCommand command, Long id, VehicleStatus status,
                              java.time.LocalDateTime createdAt, boolean requireActiveOperator) {
        if (command == null) throw new BusinessException("Vehicle data is required");
        if (command.operatorId() == null || command.operatorId() <= 0) {
            throw new BusinessException("operatorId must be a positive number");
        }
        String licensePlate = normalizeLicensePlate(command.licensePlate());
        String vehicleType = normalizeRequired(command.vehicleType(), "vehicleType", 50);
        if (command.seatCapacity() == null || command.seatCapacity() <= 0) {
            throw new BusinessException("seatCapacity must be positive");
        }
        Operator operator = operators.findByIdForUpdate(command.operatorId())
                .orElseThrow(() -> new ResourceNotFoundException("Operator not found with id: " + command.operatorId()));
        if (requireActiveOperator && operator.status() != OperatorStatus.ACTIVE) {
            throw new InactiveOperatorException();
        }
        return new Vehicle(id, new OperatorSummary(operator.id(), operator.name()), licensePlate,
                vehicleType, command.seatCapacity(), status, createdAt);
    }

    private void requireUnique(Vehicle vehicle, Long excludedId) {
        if (vehicles.existsByLicensePlate(vehicle.licensePlate(), excludedId)) {
            throw new DuplicateVehicleException();
        }
    }

    private String normalizeLicensePlate(String value) {
        if (value == null) throw new BusinessException("licensePlate is required");
        String normalized = value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 20) {
            throw new BusinessException("licensePlate must contain between 1 and 20 characters after normalization");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String field, int maxLength) {
        if (value == null) throw new BusinessException(field + " is required");
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new BusinessException(field + " must contain between 1 and " + maxLength + " characters");
        }
        return normalized;
    }

    private String normalizeQuery(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.length() > 100) throw new BusinessException("Vehicle search query must not exceed 100 characters");
        return normalized;
    }
}
