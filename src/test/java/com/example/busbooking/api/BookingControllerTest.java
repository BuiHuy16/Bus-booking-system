package com.example.busbooking.api;

import com.example.busbooking.api.controller.BookingController;
import com.example.busbooking.api.dto.booking.BookingHistoryResponse;
import com.example.busbooking.api.dto.booking.BookingRequest;
import com.example.busbooking.api.dto.booking.BookingResponse;
import com.example.busbooking.api.mapper.BookingDtoMapper;
import com.example.busbooking.business.service.BookingService;
import com.example.busbooking.domain.exception.ForbiddenOperationException;
import com.example.busbooking.domain.model.Booking;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    @Mock
    private BookingDtoMapper mapper;

    @InjectMocks
    private BookingController bookingController;

    private Authentication authentication(String userId, String... roles) {
        var authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(userId, null, authorities);
    }

    @Test
    void create_withAuthenticatedUser_shouldReturn201() {
        BookingRequest request = mock(BookingRequest.class);
        Booking booking = mock(Booking.class);
        BookingResponse response = mock(BookingResponse.class);
        Authentication auth = authentication("7", "ROLE_USER");

        when(request.tripId()).thenReturn(15L);
        when(request.passengerName()).thenReturn("Nguyen Van A");
        when(request.passengerPhone()).thenReturn("0901234567");
        when(request.quantity()).thenReturn(2);
        when(bookingService.create(7L, 15L, "Nguyen Van A", "0901234567", 2))
                .thenReturn(booking);
        when(mapper.toResponse(booking)).thenReturn(response);

        ResponseEntity<BookingResponse> result = bookingController.create(request, auth);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(response, result.getBody());
        verify(bookingService).create(7L, 15L, "Nguyen Van A", "0901234567", 2);
        verify(mapper).toResponse(booking);
    }

    @Test
    void get_asOwner_shouldReturnBooking() {
        Booking booking = mock(Booking.class);
        BookingResponse response = mock(BookingResponse.class);
        when(bookingService.get(7L, false, 100L)).thenReturn(booking);
        when(mapper.toResponse(booking)).thenReturn(response);

        BookingResponse result = bookingController.get(100L, authentication("7", "ROLE_USER"));

        assertSame(response, result);
        verify(bookingService).get(7L, false, 100L);
    }

    @Test
    void get_asAdmin_shouldPassAdminFlag() {
        Booking booking = mock(Booking.class);
        when(bookingService.get(1L, true, 100L)).thenReturn(booking);

        bookingController.get(100L, authentication("1", "ROLE_ADMIN"));

        verify(bookingService).get(1L, true, 100L);
    }

    @Test
    void cancel_asOwner_shouldCallServiceAndReturnResponse() {
        Booking cancelled = mock(Booking.class);
        BookingResponse response = mock(BookingResponse.class);
        when(bookingService.cancel(7L, false, 100L)).thenReturn(cancelled);
        when(mapper.toResponse(cancelled)).thenReturn(response);

        BookingResponse result = bookingController.cancel(100L, authentication("7", "ROLE_USER"));

        assertSame(response, result);
        verify(bookingService).cancel(7L, false, 100L);
    }

    @Test
    void history_shouldReturnCurrentUsersBookings() {
        Booking booking1 = mock(Booking.class);
        Booking booking2 = mock(Booking.class);
        BookingHistoryResponse response1 = mock(BookingHistoryResponse.class);
        BookingHistoryResponse response2 = mock(BookingHistoryResponse.class);
        when(bookingService.history(7L)).thenReturn(List.of(booking1, booking2));
        when(mapper.toHistoryResponse(booking1)).thenReturn(response1);
        when(mapper.toHistoryResponse(booking2)).thenReturn(response2);

        List<BookingHistoryResponse> result = bookingController.history(authentication("7", "ROLE_USER"));

        assertEquals(List.of(response1, response2), result);
        verify(bookingService).history(7L);
    }

    @Test
    void get_withoutAuthentication_shouldThrowForbiddenOperationException() {
        ForbiddenOperationException exception = assertThrows(
                ForbiddenOperationException.class,
                () -> bookingController.get(100L, null)
        );

        assertEquals("Authentication is required", exception.getMessage());
        verifyNoInteractions(bookingService, mapper);
    }

    @Test
    void create_withUnauthenticatedPrincipal_shouldThrowForbiddenOperationException() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        BookingRequest request = mock(BookingRequest.class);

        ForbiddenOperationException exception = assertThrows(
                ForbiddenOperationException.class,
                () -> bookingController.create(request, auth)
        );

        assertEquals("Authentication is required", exception.getMessage());
        verifyNoInteractions(bookingService, mapper);
    }

    @Test
    void cancel_withNonNumericPrincipal_shouldThrowForbiddenOperationException() {
        Authentication auth = authentication("not-a-number", "ROLE_USER");

        ForbiddenOperationException exception = assertThrows(
                ForbiddenOperationException.class,
                () -> bookingController.cancel(100L, auth)
        );

        assertEquals("Authenticated principal must contain a numeric user id", exception.getMessage());
        verifyNoInteractions(bookingService, mapper);
    }
}
