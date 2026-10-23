package com.example.busbooking.business.service;

import com.example.busbooking.business.port.repository.BookingRepository;
import com.example.busbooking.business.port.repository.TripRepository;
import com.example.busbooking.domain.enums.BookingStatus;
import com.example.busbooking.domain.enums.TripStatus;
import com.example.busbooking.domain.exception.*;
import com.example.busbooking.domain.model.Booking;
import com.example.busbooking.domain.model.Trip;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {
    private final BookingRepository bookingRepository;
    private final TripRepository tripRepository;

    public BookingService(BookingRepository bookingRepository, TripRepository tripRepository) {
        this.bookingRepository = bookingRepository;
        this.tripRepository = tripRepository;
    }

    @Transactional
    public Booking create(Long userId, Long tripId, String passengerName,
                          String passengerPhone, int quantity) {
        if (quantity <= 0) throw new BusinessException("Quantity must be greater than zero");
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + tripId));
        if (trip.getStatus() != TripStatus.SCHEDULED) {
            throw new BusinessException("Trip is not available for booking");
        }
        if (trip.getAvailableSeats() < quantity) {
            throw new InsufficientSeatsException("Not enough available seats");
        }
        if (!tripRepository.updateAvailableSeats(tripId, -quantity)) {
            throw new InsufficientSeatsException("Not enough available seats");
        }
        BigDecimal total = trip.getPrice().multiply(BigDecimal.valueOf(quantity));
        Booking booking = new Booking(null, "BK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20), userId, tripId,
                passengerName, passengerPhone, quantity, trip.getPrice(), total,
                BookingStatus.CONFIRMED, LocalDateTime.now());
        return bookingRepository.save(booking);
    }

    @Transactional(readOnly = true)
    public Booking get(Long userId, boolean admin, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
        verifyAccess(userId, admin, booking);
        return booking;
    }

    @Transactional(readOnly = true)
    public List<Booking> history(Long userId) {
        return bookingRepository.findByUserId(userId);
    }

    @Transactional
    public Booking cancel(Long userId, boolean admin, Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
        verifyAccess(userId, admin, booking);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessException("Booking is already cancelled");
        }
        Trip trip = tripRepository.findByIdForUpdate(booking.getTripId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trip not found with id: " + booking.getTripId()));
        if (trip.getVehicle() == null
                || trip.getAvailableSeats() + booking.getQuantity() > trip.getVehicle().seatCapacity()) {
            throw new BusinessException("Unable to restore available seats");
        }
        if (!tripRepository.updateAvailableSeats(booking.getTripId(), booking.getQuantity())) {
            throw new BusinessException("Unable to restore available seats");
        }
        return bookingRepository.updateStatus(bookingId, BookingStatus.CANCELLED);
    }

    private void verifyAccess(Long userId, boolean admin, Booking booking) {
        if (!admin && !booking.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("You do not have access to this booking");
        }
    }
}
