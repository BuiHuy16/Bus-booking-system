package com.example.busbooking.api.dto.booking;

import com.example.busbooking.domain.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingResponse(
        Long id, 
        String bookingCode, 
        Long tripId, 
        String passengerName,
        String passengerPhone, 
        int quantity, 
        BigDecimal unitPrice,
        BigDecimal totalAmount, 
        BookingStatus status, 
        LocalDateTime bookedAt) {
}
