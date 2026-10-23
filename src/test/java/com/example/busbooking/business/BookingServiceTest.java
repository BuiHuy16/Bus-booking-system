package com.example.busbooking.business;

import com.example.busbooking.business.port.repository.BookingRepository;
import com.example.busbooking.business.port.repository.TripRepository;
import com.example.busbooking.business.service.BookingService;
import com.example.busbooking.domain.enums.BookingStatus;
import com.example.busbooking.domain.enums.TripStatus;
import com.example.busbooking.domain.exception.BusinessException;
import com.example.busbooking.domain.exception.ForbiddenOperationException;
import com.example.busbooking.domain.exception.InsufficientSeatsException;
import com.example.busbooking.domain.exception.ResourceNotFoundException;
import com.example.busbooking.domain.model.Booking;
import com.example.busbooking.domain.model.Trip;
import com.example.busbooking.domain.model.VehicleSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long TRIP_ID = 10L;
    private static final Long BOOKING_ID = 100L;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TripRepository tripRepository;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void create_whenValid_shouldSaveConfirmedBookingAndDecreaseSeats() {
        Trip trip = mock(Trip.class);
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));
        when(trip.getStatus()).thenReturn(TripStatus.SCHEDULED);
        when(trip.getAvailableSeats()).thenReturn(5);
        when(trip.getPrice()).thenReturn(new BigDecimal("150000"));
        when(tripRepository.updateAvailableSeats(TRIP_ID, -2)).thenReturn(true);

        Booking savedBooking = mock(Booking.class);
        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);

        Booking result = bookingService.create(USER_ID, TRIP_ID, "Nguyen Van A", "0901234567", 2);

        assertSame(savedBooking, result);
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        Booking newBooking = captor.getValue();
        assertEquals(USER_ID, newBooking.getUserId());
        assertEquals(TRIP_ID, newBooking.getTripId());
        assertEquals(2, newBooking.getQuantity());
        assertEquals(new BigDecimal("150000"), newBooking.getUnitPrice());
        assertEquals(new BigDecimal("300000"), newBooking.getTotalAmount());
        assertEquals(BookingStatus.CONFIRMED, newBooking.getStatus());
        assertNotNull(newBooking.getBookingCode());
        assertTrue(newBooking.getBookingCode().startsWith("BK-"));
        verify(tripRepository).updateAvailableSeats(TRIP_ID, -2);
    }

    @Test
    void create_whenQuantityIsZero_shouldRejectWithoutAccessingDatabase() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> bookingService.create(USER_ID, TRIP_ID, "A", "0901234567", 0));

        assertEquals("Quantity must be greater than zero", exception.getMessage());
        verifyNoInteractions(tripRepository, bookingRepository);
    }

    @Test
    void create_whenTripDoesNotExist_shouldThrowNotFound() {
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> bookingService.create(USER_ID, TRIP_ID, "A", "0901234567", 1));
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void create_whenTripIsNotScheduled_shouldReject() {
        Trip trip = mock(Trip.class);
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));
        when(trip.getStatus()).thenReturn(TripStatus.CANCELLED);

        assertThrows(BusinessException.class,
                () -> bookingService.create(USER_ID, TRIP_ID, "A", "0901234567", 1));
        verify(tripRepository, never()).updateAvailableSeats(anyLong(), anyInt());
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void create_whenNotEnoughSeats_shouldReject() {
        Trip trip = mock(Trip.class);
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));
        when(trip.getStatus()).thenReturn(TripStatus.SCHEDULED);
        when(trip.getAvailableSeats()).thenReturn(1);

        assertThrows(InsufficientSeatsException.class,
                () -> bookingService.create(USER_ID, TRIP_ID, "A", "0901234567", 2));
        verify(tripRepository, never()).updateAvailableSeats(anyLong(), anyInt());
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void create_whenSeatUpdateFails_shouldRejectWithoutSaving() {
        Trip trip = mock(Trip.class);
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));
        when(trip.getStatus()).thenReturn(TripStatus.SCHEDULED);
        when(trip.getAvailableSeats()).thenReturn(3);
        when(tripRepository.updateAvailableSeats(TRIP_ID, -2)).thenReturn(false);

        assertThrows(InsufficientSeatsException.class,
                () -> bookingService.create(USER_ID, TRIP_ID, "A", "0901234567", 2));
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void get_whenOwnerRequestsBooking_shouldReturnBooking() {
        Booking booking = mock(Booking.class);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(booking.getUserId()).thenReturn(USER_ID);

        assertSame(booking, bookingService.get(USER_ID, false, BOOKING_ID));
    }

    @Test
    void get_whenAnotherUserRequestsBooking_shouldThrowForbidden() {
        Booking booking = mock(Booking.class);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(booking.getUserId()).thenReturn(2L);

        assertThrows(ForbiddenOperationException.class,
                () -> bookingService.get(USER_ID, false, BOOKING_ID));
    }

    @Test
    void get_whenAdminRequestsBooking_shouldReturnBooking() {
        Booking booking = mock(Booking.class);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertSame(booking, bookingService.get(USER_ID, true, BOOKING_ID));
    }

    @Test
    void history_shouldReturnUserBookings() {
        List<Booking> bookings = List.of(mock(Booking.class), mock(Booking.class));
        when(bookingRepository.findByUserId(USER_ID)).thenReturn(bookings);

        assertSame(bookings, bookingService.history(USER_ID));
        verify(bookingRepository).findByUserId(USER_ID);
    }

    @Test
    void cancel_whenOwnerCancelsConfirmedBooking_shouldRestoreSeatsAndUpdateStatus() {
        Booking booking = mock(Booking.class);
        Trip trip = mock(Trip.class);
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(booking.getUserId()).thenReturn(USER_ID);
        when(booking.getStatus()).thenReturn(BookingStatus.CONFIRMED);
        when(booking.getTripId()).thenReturn(TRIP_ID);
        when(booking.getQuantity()).thenReturn(2);
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));
        when(trip.getAvailableSeats()).thenReturn(3);
        when(trip.getVehicle()).thenReturn(new VehicleSummary("SLEEPER", 10, "Operator"));
        when(tripRepository.updateAvailableSeats(TRIP_ID, 2)).thenReturn(true);
        Booking cancelledBooking = mock(Booking.class);
        when(bookingRepository.updateStatus(BOOKING_ID, BookingStatus.CANCELLED))
                .thenReturn(cancelledBooking);

        Booking result = bookingService.cancel(USER_ID, false, BOOKING_ID);

        assertSame(cancelledBooking, result);
        verify(tripRepository).updateAvailableSeats(TRIP_ID, 2);
        verify(bookingRepository).updateStatus(BOOKING_ID, BookingStatus.CANCELLED);
    }

    @Test
    void cancel_whenNotOwner_shouldThrowForbiddenAndNotRestoreSeats() {
        Booking booking = mock(Booking.class);
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(booking.getUserId()).thenReturn(2L);

        assertThrows(ForbiddenOperationException.class,
                () -> bookingService.cancel(USER_ID, false, BOOKING_ID));
        verifyNoInteractions(tripRepository);
        verify(bookingRepository, never()).updateStatus(anyLong(), any(BookingStatus.class));
    }

    @Test
    void cancel_whenAlreadyCancelled_shouldReject() {
        Booking booking = mock(Booking.class);
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(booking.getUserId()).thenReturn(USER_ID);
        when(booking.getStatus()).thenReturn(BookingStatus.CANCELLED);

        assertThrows(BusinessException.class,
                () -> bookingService.cancel(USER_ID, false, BOOKING_ID));
        verifyNoInteractions(tripRepository);
        verify(bookingRepository, never()).updateStatus(anyLong(), any(BookingStatus.class));
    }

    @Test
    void cancel_whenSeatRestoreFails_shouldRejectWithoutUpdatingBooking() {
        Booking booking = mock(Booking.class);
        Trip trip = mock(Trip.class);
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(booking.getUserId()).thenReturn(USER_ID);
        when(booking.getStatus()).thenReturn(BookingStatus.CONFIRMED);
        when(booking.getTripId()).thenReturn(TRIP_ID);
        when(booking.getQuantity()).thenReturn(2);
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));
        when(trip.getAvailableSeats()).thenReturn(3);
        when(trip.getVehicle()).thenReturn(new VehicleSummary("SLEEPER", 10, "Operator"));
        when(tripRepository.updateAvailableSeats(TRIP_ID, 2)).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> bookingService.cancel(USER_ID, false, BOOKING_ID));
        verify(bookingRepository, never()).updateStatus(anyLong(), any(BookingStatus.class));
    }

    @Test
    void cancel_whenRestoringWouldExceedVehicleCapacity_shouldRejectWithoutUpdating() {
        Booking booking = mock(Booking.class);
        Trip trip = mock(Trip.class);
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(booking.getUserId()).thenReturn(USER_ID);
        when(booking.getStatus()).thenReturn(BookingStatus.CONFIRMED);
        when(booking.getTripId()).thenReturn(TRIP_ID);
        when(booking.getQuantity()).thenReturn(2);
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));
        when(trip.getAvailableSeats()).thenReturn(9);
        when(trip.getVehicle()).thenReturn(new VehicleSummary("SLEEPER", 10, "Operator"));

        assertThrows(BusinessException.class,
                () -> bookingService.cancel(USER_ID, false, BOOKING_ID));

        verify(tripRepository, never()).updateAvailableSeats(anyLong(), anyInt());
        verify(bookingRepository, never()).updateStatus(anyLong(), any(BookingStatus.class));
    }
}
