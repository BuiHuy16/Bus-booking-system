package com.example.busbooking.business;

import com.example.busbooking.business.port.repository.*;
import com.example.busbooking.business.service.BookingService;
import com.example.busbooking.domain.enums.BookingStatus;
import com.example.busbooking.domain.model.Booking;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBookingServiceTest {
    BookingRepository repository = mock(BookingRepository.class);
    BookingService service = new BookingService(repository, mock(TripRepository.class));

    @Test void filtersAreDelegatedAndResultsAreNotRestrictedToCurrentUser() {
        Booking booking = mock(Booking.class);
        when(repository.findForAdmin(7L, 9L, BookingStatus.CONFIRMED)).thenReturn(List.of(booking));
        assertThat(service.listForAdmin(7L, 9L, BookingStatus.CONFIRMED)).containsExactly(booking);
        verify(repository).findForAdmin(7L, 9L, BookingStatus.CONFIRMED);
    }

    @Test void noFiltersRequestsBookingsAcrossAllUsers() {
        when(repository.findForAdmin(null, null, null)).thenReturn(List.of());
        assertThat(service.listForAdmin(null, null, null)).isEmpty();
        verify(repository).findForAdmin(null, null, null);
    }
}
