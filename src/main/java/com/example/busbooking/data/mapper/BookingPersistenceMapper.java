package com.example.busbooking.data.mapper;

import com.example.busbooking.data.entity.BookingEntity;
import com.example.busbooking.domain.model.Booking;
import org.springframework.stereotype.Component;

@Component
public class BookingPersistenceMapper {
    public Booking toDomain(BookingEntity entity) {
        return entity == null ? null : new Booking(entity.getId(), entity.getBookingCode(),
                entity.getUserId(), entity.getTripId(), entity.getPassengerName(),
                entity.getPassengerPhone(), entity.getQuantity(), entity.getUnitPrice(),
                entity.getTotalAmount(), entity.getStatus(), entity.getBookedAt());
    }

    public BookingEntity toEntity(Booking booking) {
        BookingEntity entity;
        try {
            var constructor = BookingEntity.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            entity = constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create booking entity", exception);
        }
        entity.setBookingCode(booking.getBookingCode());
        entity.setUserId(booking.getUserId());
        entity.setTripId(booking.getTripId());
        entity.setPassengerName(booking.getPassengerName());
        entity.setPassengerPhone(booking.getPassengerPhone());
        entity.setQuantity(booking.getQuantity());
        entity.setUnitPrice(booking.getUnitPrice());
        entity.setTotalAmount(booking.getTotalAmount());
        entity.setStatus(booking.getStatus());
        entity.setBookedAt(booking.getBookedAt());
        return entity;
    }
}
