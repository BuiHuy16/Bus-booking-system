package com.example.busbooking.domain.model;

import com.example.busbooking.domain.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Booking {
    private final Long id;
    private final String bookingCode;
    private final Long userId;
    private final Long tripId;
    private final String passengerName;
    private final String passengerPhone;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal totalAmount;
    private final BookingStatus status;
    private final LocalDateTime bookedAt;

    public Booking(Long id, String bookingCode, Long userId, Long tripId,
                   String passengerName, String passengerPhone, int quantity,
                   BigDecimal unitPrice, BigDecimal totalAmount,
                   BookingStatus status, LocalDateTime bookedAt) {
        this.id = id;
        this.bookingCode = bookingCode;
        this.userId = userId;
        this.tripId = tripId;
        this.passengerName = passengerName;
        this.passengerPhone = passengerPhone;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = totalAmount;
        this.status = status;
        this.bookedAt = bookedAt;
    }

    public Long getId() { return id; }
    public String getBookingCode() { return bookingCode; }
    public Long getUserId() { return userId; }
    public Long getTripId() { return tripId; }
    public String getPassengerName() { return passengerName; }
    public String getPassengerPhone() { return passengerPhone; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BookingStatus getStatus() { return status; }
    public LocalDateTime getBookedAt() { return bookedAt; }
}
