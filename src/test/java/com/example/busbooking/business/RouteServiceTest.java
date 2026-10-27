package com.example.busbooking.business;

import com.example.busbooking.business.command.SaveRouteCommand;
import com.example.busbooking.business.port.repository.LocationRepository;
import com.example.busbooking.business.port.repository.RouteRepository;
import com.example.busbooking.business.service.RouteService;
import com.example.busbooking.domain.enums.RouteStatus;
import com.example.busbooking.domain.exception.*;
import com.example.busbooking.domain.model.Location;
import com.example.busbooking.domain.model.Route;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {
    @Mock RouteRepository routes;
    @Mock LocationRepository locations;
    RouteService service;
    Location origin = new Location(1L, "Origin", "Province A", "Address A");
    Location destination = new Location(2L, "Destination", "Province B", "Address B");

    @BeforeEach void setUp() {
        service = new RouteService(routes, locations);
    }

    SaveRouteCommand command(Long originId, Long destinationId, Integer duration, RouteStatus status) {
        return new SaveRouteCommand(originId, destinationId, duration, status);
    }

    void locationsExist() {
        when(locations.findById(1L)).thenReturn(Optional.of(origin));
        when(locations.findById(2L)).thenReturn(Optional.of(destination));
    }

    Route current(RouteStatus status) {
        return new Route(7L, origin, destination, 120, status);
    }

    @Test void createDefaultsToActiveAndPersistsCompleteRoute() {
        locationsExist();
        when(routes.save(any())).thenAnswer(call -> call.getArgument(0));
        Route created = service.create(command(1L, 2L, 90, null));
        assertThat(created.status()).isEqualTo(RouteStatus.ACTIVE);
        assertThat(created.estimatedDurationMinutes()).isEqualTo(90);
        assertThat(created.origin()).isEqualTo(origin);
        verify(routes).existsByEndpoints(1L, 2L, null);
    }

    @Test void createRejectsDuplicateAndAllowsReverseDirection() {
        locationsExist();
        when(routes.existsByEndpoints(1L, 2L, null)).thenReturn(true);
        assertThatThrownBy(() -> service.create(command(1L, 2L, 90, null)))
                .isInstanceOf(DuplicateRouteException.class);
        verify(routes, never()).save(any());
    }

    @Test void rejectsSameEndpointInvalidDurationAndMissingLocation() {
        assertThatThrownBy(() -> service.create(command(1L, 1L, 90, null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(command(1L, 2L, 0, null)))
                .isInstanceOf(BusinessException.class);
        when(locations.findById(1L)).thenReturn(Optional.of(origin));
        assertThatThrownBy(() -> service.create(command(1L, 2L, 90, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void updateAllowsDurationAndStatusWhenEndpointsStayTheSame() {
        locationsExist();
        when(routes.findByIdForUpdate(7L)).thenReturn(Optional.of(current(RouteStatus.INACTIVE)));
        when(routes.save(any())).thenAnswer(call -> call.getArgument(0));
        Route updated = service.update(7L, command(1L, 2L, 180, RouteStatus.ACTIVE));
        assertThat(updated.estimatedDurationMinutes()).isEqualTo(180);
        assertThat(updated.status()).isEqualTo(RouteStatus.ACTIVE);
        verify(routes, never()).hasAnyTrips(7L);
    }

    @Test void endpointChangeIsBlockedAfterAnyTrip() {
        Location replacement = new Location(3L, "Other", "Province", "Address");
        when(routes.findByIdForUpdate(7L)).thenReturn(Optional.of(current(RouteStatus.ACTIVE)));
        when(locations.findById(3L)).thenReturn(Optional.of(replacement));
        when(locations.findById(2L)).thenReturn(Optional.of(destination));
        when(routes.hasAnyTrips(7L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(7L, command(3L, 2L, 120, RouteStatus.ACTIVE)))
                .isInstanceOf(RouteEndpointsLockedException.class);
        verify(routes, never()).save(any());
    }

    @Test void endpointChangeIsAllowedBeforeFirstTrip() {
        Location replacement = new Location(3L, "Other", "Province", "Address");
        when(routes.findByIdForUpdate(7L)).thenReturn(Optional.of(current(RouteStatus.ACTIVE)));
        when(locations.findById(3L)).thenReturn(Optional.of(replacement));
        when(locations.findById(2L)).thenReturn(Optional.of(destination));
        when(routes.save(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(service.update(7L, command(3L, 2L, 120, RouteStatus.ACTIVE)).origin()).isEqualTo(replacement);
        verify(routes).hasAnyTrips(7L);
    }

    @Test void deactivationIsBlockedOnlyByOperationalTrips() {
        locationsExist();
        when(routes.findByIdForUpdate(7L)).thenReturn(Optional.of(current(RouteStatus.ACTIVE)));
        when(routes.hasOperationalTrips(7L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(7L, command(1L, 2L, 120, RouteStatus.INACTIVE)))
                .isInstanceOf(RouteInUseException.class);
        verify(routes, never()).save(any());
    }

    @Test void deactivationWithHistoricalTripsIsAllowed() {
        locationsExist();
        when(routes.findByIdForUpdate(7L)).thenReturn(Optional.of(current(RouteStatus.ACTIVE)));
        when(routes.save(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(service.update(7L, command(1L, 2L, 120, RouteStatus.INACTIVE)).status())
                .isEqualTo(RouteStatus.INACTIVE);
    }

    @Test void searchValidatesIdsAndDelegatesFilters() {
        service.search(1L, 2L, RouteStatus.ACTIVE);
        verify(routes).search(1L, 2L, RouteStatus.ACTIVE);
        assertThatThrownBy(() -> service.search(0L, null, null)).isInstanceOf(BusinessException.class);
    }
}
