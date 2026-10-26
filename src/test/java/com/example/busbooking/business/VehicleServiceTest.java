package com.example.busbooking.business;

import com.example.busbooking.business.command.SaveVehicleCommand;
import com.example.busbooking.business.port.repository.OperatorRepository;
import com.example.busbooking.business.port.repository.VehicleRepository;
import com.example.busbooking.business.service.VehicleService;
import com.example.busbooking.domain.enums.*;
import com.example.busbooking.domain.exception.*;
import com.example.busbooking.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {
    @Mock VehicleRepository vehicles;
    @Mock OperatorRepository operators;
    VehicleService service;
    LocalDateTime createdAt = LocalDateTime.of(2026, 10, 24, 10, 0);

    @BeforeEach void setUp() { service = new VehicleService(vehicles, operators); }

    Operator operator(long id, OperatorStatus status) {
        return new Operator(id, "Operator " + id, "Address", "0901234567",
                "operator" + id + "@test.test", status, createdAt);
    }

    Vehicle current(VehicleStatus status) {
        return new Vehicle(7L, new OperatorSummary(1L, "Operator 1"), "51A123.45",
                "Giường nằm", 40, status, createdAt);
    }

    SaveVehicleCommand command(Long operatorId, String plate, String type, Integer capacity, VehicleStatus status) {
        return new SaveVehicleCommand(operatorId, plate, type, capacity, status);
    }

    @Test void createNormalizesAndDefaultsToActive() {
        when(operators.findByIdForUpdate(1L)).thenReturn(Optional.of(operator(1L, OperatorStatus.ACTIVE)));
        when(vehicles.save(any())).thenAnswer(call -> call.getArgument(0));
        Vehicle created = service.create(command(1L, " 51a 123.45 ", "  Giường   nằm ", 40, null));
        assertThat(created.licensePlate()).isEqualTo("51A123.45");
        assertThat(created.vehicleType()).isEqualTo("Giường nằm");
        assertThat(created.status()).isEqualTo(VehicleStatus.ACTIVE);
        verify(vehicles).existsByLicensePlate("51A123.45", null);
    }

    @Test void createRejectsInactiveOrMissingOperator() {
        when(operators.findByIdForUpdate(1L)).thenReturn(Optional.of(operator(1L, OperatorStatus.INACTIVE)));
        assertThatThrownBy(() -> service.create(command(1L, "51A", "Bus", 40, null)))
                .isInstanceOf(InactiveOperatorException.class);
        when(operators.findByIdForUpdate(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(command(2L, "51B", "Bus", 40, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(vehicles);
    }

    @Test void createRejectsDuplicateNormalizedPlate() {
        when(operators.findByIdForUpdate(1L)).thenReturn(Optional.of(operator(1L, OperatorStatus.ACTIVE)));
        when(vehicles.existsByLicensePlate("51A12345", null)).thenReturn(true);
        assertThatThrownBy(() -> service.create(command(1L, "51a 12345", "Bus", 40, null)))
                .isInstanceOf(DuplicateVehicleException.class);
        verify(vehicles, never()).save(any());
    }

    @Test void validatesRequiredFieldsAndPositiveCapacityBeforeRepositoryCalls() {
        assertThatThrownBy(() -> service.create(command(1L, " ", "Bus", 40, null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(command(1L, "51A", " ", 40, null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(command(1L, "51A", "Bus", 0, null))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(operators, vehicles);
    }

    @Test void referencedVehicleLocksOperatorPlateAndType() {
        when(vehicles.findByIdForUpdate(7L)).thenReturn(Optional.of(current(VehicleStatus.ACTIVE)));
        when(operators.findByIdForUpdate(2L)).thenReturn(Optional.of(operator(2L, OperatorStatus.ACTIVE)));
        when(vehicles.hasAnyTrips(7L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(7L, command(2L, "51B", "Limousine", 40, VehicleStatus.ACTIVE)))
                .isInstanceOf(VehicleIdentityLockedException.class);
        verify(vehicles, never()).save(any());
    }

    @Test void operationalTripBlocksCapacityAndLeavingActive() {
        when(vehicles.findByIdForUpdate(7L)).thenReturn(Optional.of(current(VehicleStatus.ACTIVE)));
        when(operators.findByIdForUpdate(1L)).thenReturn(Optional.of(operator(1L, OperatorStatus.ACTIVE)));
        when(vehicles.hasOperationalTrips(7L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(7L,
                command(1L, "51A123.45", "Giường nằm", 44, VehicleStatus.ACTIVE)))
                .isInstanceOf(VehicleInUseException.class);
        assertThatThrownBy(() -> service.update(7L,
                command(1L, "51A123.45", "Giường nằm", 40, VehicleStatus.MAINTENANCE)))
                .isInstanceOf(VehicleInUseException.class);
        verify(vehicles, never()).save(any());
    }

    @Test void historicalTripsAllowCapacityAndStatusChanges() {
        when(vehicles.findByIdForUpdate(7L)).thenReturn(Optional.of(current(VehicleStatus.ACTIVE)));
        when(operators.findByIdForUpdate(1L)).thenReturn(Optional.of(operator(1L, OperatorStatus.ACTIVE)));
        when(vehicles.save(any())).thenAnswer(call -> call.getArgument(0));
        Vehicle updated = service.update(7L,
                command(1L, "51A123.45", "Giường nằm", 44, VehicleStatus.MAINTENANCE));
        assertThat(updated.seatCapacity()).isEqualTo(44);
        assertThat(updated.status()).isEqualTo(VehicleStatus.MAINTENANCE);
        assertThat(updated.createdAt()).isEqualTo(createdAt);
    }

    @Test void activationRequiresActiveOperatorButInactiveEditDoesNot() {
        when(vehicles.findByIdForUpdate(7L)).thenReturn(Optional.of(current(VehicleStatus.INACTIVE)));
        when(operators.findByIdForUpdate(1L)).thenReturn(Optional.of(operator(1L, OperatorStatus.INACTIVE)));
        assertThatThrownBy(() -> service.update(7L,
                command(1L, "51A123.45", "Giường nằm", 40, VehicleStatus.ACTIVE)))
                .isInstanceOf(InactiveOperatorException.class);

        when(vehicles.save(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(service.update(7L,
                command(1L, "51A123.45", "Giường nằm", 40, VehicleStatus.INACTIVE)).status())
                .isEqualTo(VehicleStatus.INACTIVE);
    }

    @Test void searchNormalizesQueryAndValidatesOperatorId() {
        service.search("  giường   nằm ", 1L, VehicleStatus.ACTIVE);
        verify(vehicles).search("giường nằm", 1L, VehicleStatus.ACTIVE);
        assertThatThrownBy(() -> service.search(null, 0L, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search("x".repeat(101), null, null)).isInstanceOf(BusinessException.class);
    }
}
