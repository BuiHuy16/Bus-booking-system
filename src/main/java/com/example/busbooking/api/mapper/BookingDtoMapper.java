package com.example.busbooking.api.mapper;

import com.example.busbooking.api.dto.booking.*;
import com.example.busbooking.domain.model.Booking;
import org.springframework.stereotype.Component;

@Component
public class BookingDtoMapper {
    public BookingResponse toResponse(Booking b) {
        return new BookingResponse(b.getId(), 
                                b.getBookingCode(), 
                                b.getTripId(),
                                b.getPassengerName(), 
                                b.getPassengerPhone(), 
                                b.getQuantity(),
                                b.getUnitPrice(), 
                                b.getTotalAmount(), 
                                b.getStatus(), 
                                b.getBookedAt());
    }
    public BookingHistoryResponse toHistoryResponse(Booking b) {
        return new BookingHistoryResponse(b.getId(), 
                                        b.getBookingCode(), 
                                        b.getTripId(),
                                        b.getPassengerName(), 
                                        b.getPassengerPhone(), 
                                        b.getQuantity(),
                                        b.getTotalAmount(), 
                                        b.getStatus(), 
                                        b.getBookedAt());
    }
}
