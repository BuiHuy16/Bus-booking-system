package com.example.busbooking.business.port.repository;

import com.example.busbooking.domain.model.Booking;

import java.util.List;
import java.util.Optional;

public interface BookingRepository {
    long countConfirmedSeats(Long tripId);
    List<Booking> findForAdmin(Long userId, Long tripId, com.example.busbooking.domain.enums.BookingStatus status);
    Booking save(Booking booking);
    Optional<Booking> findById(Long bookingId);
    Optional<Booking> findByIdForUpdate(Long bookingId);
    List<Booking> findByUserId(Long userId);
    Booking updateStatus(Long bookingId, com.example.busbooking.domain.enums.BookingStatus status);
}
