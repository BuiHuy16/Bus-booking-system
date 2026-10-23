
package com.example.busbooking.business;

import com.example.busbooking.business.port.repository.TripRepository;
import com.example.busbooking.business.service.TripService;
import com.example.busbooking.domain.exception.ResourceNotFoundException;
import com.example.busbooking.domain.model.Trip;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock
    private TripRepository tripRepository;

    @InjectMocks
    private TripService tripService;

    @Test
    void searchTrips_whenTripsExist_shouldReturnMatchingTrips() {
        Long originId = 1L;
        Long destinationId = 2L;
        LocalDate departureDate = LocalDate.of(2026, 10, 25);
        Trip trip = mock(Trip.class);
        List<Trip> expectedTrips = List.of(trip);

        when(tripRepository.search(originId, destinationId, departureDate))
                .thenReturn(expectedTrips);

        List<Trip> actualTrips = tripService.searchTrips(
                originId, destinationId, departureDate
        );

        assertEquals(expectedTrips, actualTrips);
        verify(tripRepository).search(originId, destinationId, departureDate);
    }

    @Test
    void searchTrips_whenNoTripsExist_shouldReturnEmptyList() {
        Long originId = 1L;
        Long destinationId = 2L;
        LocalDate departureDate = LocalDate.of(2026, 10, 25);

        when(tripRepository.search(originId, destinationId, departureDate))
                .thenReturn(List.of());

        List<Trip> result = tripService.searchTrips(
                originId, destinationId, departureDate
        );

        assertTrue(result.isEmpty());
        verify(tripRepository).search(originId, destinationId, departureDate);
    }

    @Test
    void searchTrips_whenAllFiltersAreNull_shouldDelegateNullsWithoutChangingThem() {
        when(tripRepository.search(null, null, null)).thenReturn(List.of());

        assertTrue(tripService.searchTrips(null, null, null).isEmpty());

        verify(tripRepository).search(null, null, null);
        verifyNoMoreInteractions(tripRepository);
    }

    @Test
    void getTripById_whenTripExists_shouldReturnTrip() {
        Long tripId = 10L;
        Trip expectedTrip = mock(Trip.class);

        when(tripRepository.findById(tripId))
                .thenReturn(Optional.of(expectedTrip));

        Trip actualTrip = tripService.getTripById(tripId);

        assertSame(expectedTrip, actualTrip);
        verify(tripRepository).findById(tripId);
    }

    @Test
    void getTripById_whenTripDoesNotExist_shouldThrowResourceNotFoundException() {
        Long tripId = 99L;

        when(tripRepository.findById(tripId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> tripService.getTripById(tripId)
        );

        assertEquals("Trip not found with id: 99", exception.getMessage());
        verify(tripRepository).findById(tripId);
    }
}
