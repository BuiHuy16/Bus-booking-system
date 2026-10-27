package com.example.busbooking.business;

import com.example.busbooking.business.command.SaveTripCommand;
import com.example.busbooking.business.port.repository.*;
import com.example.busbooking.business.service.TripService;
import com.example.busbooking.domain.enums.*;
import com.example.busbooking.domain.exception.*;
import com.example.busbooking.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminTripServiceTest {
    @Mock TripRepository trips;
    @Mock RouteRepository routes;
    @Mock VehicleRepository vehicles;
    @Mock BookingRepository bookings;
    @InjectMocks TripService service;
    final LocalDateTime departure = LocalDateTime.of(2026, 11, 10, 8, 0);

    SaveTripCommand command() {
        return new SaveTripCommand(1L, 2L, departure, departure.plusHours(2), new BigDecimal("150000"));
    }

    Route route(RouteStatus status) {
        return new Route(1L,
                new Location(10L, "Origin", "Province", "Address"),
                new Location(11L, "Destination", "Province", "Address"),
                120, status);
    }

    Vehicle vehicle(VehicleStatus status) {
        return new Vehicle(2L, new OperatorSummary(20L, "Operator"), "51A12345",
                "SLEEPER", 30, status, LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    void activeReferences() {
        when(routes.findByIdForUpdate(1L)).thenReturn(Optional.of(route(RouteStatus.ACTIVE)));
        when(vehicles.findByIdForUpdate(2L)).thenReturn(Optional.of(vehicle(VehicleStatus.ACTIVE)));
    }

    @Test void createInitializesScheduledTripAndVehicleCapacity() {
        activeReferences();
        when(trips.save(any())).thenAnswer(call -> call.getArgument(0));
        Trip trip = service.create(command());
        assertThat(trip.getId()).isNull();
        assertThat(trip.getAvailableSeats()).isEqualTo(30);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(trip.getPrice()).isEqualByComparingTo("150000");
    }

    @Test void createRejectsMissingRoute() {
        assertThatThrownBy(() -> service.create(command())).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(vehicles, trips);
    }

    @Test void createRejectsMissingVehicle() {
        when(routes.findByIdForUpdate(1L)).thenReturn(Optional.of(route(RouteStatus.ACTIVE)));
        assertThatThrownBy(() -> service.create(command())).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(trips);
    }

    @Test void createRejectsInactiveRoute() {
        when(routes.findByIdForUpdate(1L)).thenReturn(Optional.of(route(RouteStatus.INACTIVE)));
        when(vehicles.findByIdForUpdate(2L)).thenReturn(Optional.of(vehicle(VehicleStatus.ACTIVE)));
        assertThatThrownBy(() -> service.create(command())).isInstanceOf(BusinessException.class);
        verifyNoInteractions(trips);
    }

    @Test void createRejectsIneligibleVehicles() {
        when(routes.findByIdForUpdate(1L)).thenReturn(Optional.of(route(RouteStatus.ACTIVE)));
        for (VehicleStatus status : new VehicleStatus[]{VehicleStatus.INACTIVE, VehicleStatus.MAINTENANCE}) {
            when(vehicles.findByIdForUpdate(2L)).thenReturn(Optional.of(vehicle(status)));
            assertThatThrownBy(() -> service.create(command())).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(trips);
    }

    @Test void createRejectsInvalidTimesAndPricesBeforePersistence() {
        for (BigDecimal price : new BigDecimal[]{null, new BigDecimal("-1"), new BigDecimal("1.001"), new BigDecimal("10000000000")}) {
            assertThatThrownBy(() -> service.create(new SaveTripCommand(1L, 2L, departure,
                    departure.plusHours(1), price))).isInstanceOf(BusinessException.class);
        }
        for (LocalDateTime arrival : new LocalDateTime[]{null, departure, departure.minusSeconds(1)}) {
            assertThatThrownBy(() -> service.create(new SaveTripCommand(1L, 2L, departure,
                    arrival, BigDecimal.ZERO))).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(routes, vehicles, trips);
    }

    @Test void listDelegatesAdminFilters() {
        service.listForAdmin(1L, TripStatus.CANCELLED, departure.toLocalDate());
        verify(trips).findForAdmin(1L, TripStatus.CANCELLED, departure.toLocalDate());
    }

    Trip existing(Long vehicleId, TripStatus status) {
        return new Trip(3L, 1L, vehicleId, departure, departure.plusHours(2),
                new BigDecimal("100000"), 7, status);
    }

    @Test void updateSameVehiclePreservesAvailableSeats() {
        activeReferences();
        when(trips.findByIdForUpdate(3L)).thenReturn(Optional.of(existing(2L, TripStatus.SCHEDULED)));
        when(trips.save(any())).thenAnswer(call -> call.getArgument(0));
        Trip updated = service.update(3L, command());
        assertThat(updated.getAvailableSeats()).isEqualTo(7);
        assertThat(updated.getPrice()).isEqualByComparingTo("150000");
        verifyNoInteractions(bookings);
    }

    @Test void vehicleChangeSubtractsConfirmedSeatsUnderTripLock() {
        activeReferences();
        when(trips.findByIdForUpdate(3L)).thenReturn(Optional.of(existing(4L, TripStatus.SCHEDULED)));
        when(bookings.countConfirmedSeats(3L)).thenReturn(15L);
        when(trips.save(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(service.update(3L, command()).getAvailableSeats()).isEqualTo(15);
        var order = inOrder(trips, bookings);
        order.verify(trips).findByIdForUpdate(3L);
        order.verify(bookings).countConfirmedSeats(3L);
        order.verify(trips).save(any());
    }

    @Test void vehicleChangeRejectsInsufficientCapacityWithoutSaving() {
        activeReferences();
        when(trips.findByIdForUpdate(3L)).thenReturn(Optional.of(existing(4L, TripStatus.SCHEDULED)));
        when(bookings.countConfirmedSeats(3L)).thenReturn(31L);
        assertThatThrownBy(() -> service.update(3L, command())).isInstanceOf(TripConflictException.class);
        verify(trips, never()).save(any());
    }

    @Test void updateMissingTripIs404BeforeReferenceLookups() {
        assertThatThrownBy(() -> service.update(3L, command())).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(routes, vehicles, bookings);
    }

    @Test void updateRejectsEveryNonScheduledState() {
        for (TripStatus status : new TripStatus[]{TripStatus.CANCELLED, TripStatus.COMPLETED, TripStatus.IN_PROGRESS}) {
            when(trips.findByIdForUpdate(3L)).thenReturn(Optional.of(existing(2L, status)));
            assertThatThrownBy(() -> service.update(3L, command())).isInstanceOf(TripConflictException.class);
        }
        verify(trips, never()).save(any());
        verifyNoInteractions(routes, vehicles, bookings);
    }

    @Test void cancelWithoutConfirmedBookingsSoftCancelsAndPreservesSeats() {
        when(trips.findByIdForUpdate(3L)).thenReturn(Optional.of(existing(2L, TripStatus.SCHEDULED)));
        when(trips.save(any())).thenAnswer(call -> call.getArgument(0));
        Trip cancelled = service.cancel(3L);
        assertThat(cancelled.getStatus()).isEqualTo(TripStatus.CANCELLED);
        assertThat(cancelled.getAvailableSeats()).isEqualTo(7);
        var order = inOrder(trips, bookings);
        order.verify(trips).findByIdForUpdate(3L);
        order.verify(bookings).countConfirmedSeats(3L);
        order.verify(trips).save(any());
    }

    @Test void cancelWithConfirmedBookingsConflictsWithoutSaving() {
        when(trips.findByIdForUpdate(3L)).thenReturn(Optional.of(existing(2L, TripStatus.SCHEDULED)));
        when(bookings.countConfirmedSeats(3L)).thenReturn(1L);
        assertThatThrownBy(() -> service.cancel(3L)).isInstanceOf(TripConflictException.class);
        verify(trips, never()).save(any());
    }

    @Test void repeatedCancellationReturnsCurrentTripWithoutSaving() {
        Trip current = existing(2L, TripStatus.CANCELLED);
        when(trips.findByIdForUpdate(3L)).thenReturn(Optional.of(current));
        assertThat(service.cancel(3L)).isSameAs(current);
        verify(trips, never()).save(any());
        verifyNoInteractions(bookings);
    }

    @Test void cancelMissingTripIs404() {
        assertThatThrownBy(() -> service.cancel(3L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(bookings);
    }

    @Test void cancelRejectsStartedAndCompletedTrips() {
        for (TripStatus status : new TripStatus[]{TripStatus.IN_PROGRESS, TripStatus.COMPLETED}) {
            when(trips.findByIdForUpdate(3L)).thenReturn(Optional.of(existing(2L, status)));
            assertThatThrownBy(() -> service.cancel(3L)).isInstanceOf(TripConflictException.class);
        }
        verify(trips, never()).save(any());
    }
}
