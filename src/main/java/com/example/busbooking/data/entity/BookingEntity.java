package com.example.busbooking.data.entity;

import com.example.busbooking.domain.enums.BookingStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings", uniqueConstraints = @UniqueConstraint(name = "uk_bookings_code", columnNames = "booking_code"))
public class BookingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "booking_code", nullable = false, updatable = false)
    private String bookingCode;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "trip_id", nullable = false)
    private Long tripId;
    @Column(name = "passenger_name", nullable = false)
    private String passengerName;
    @Column(name = "passenger_phone", nullable = false)
    private String passengerPhone;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;
    @Column(name = "booked_at", nullable = false)
    private LocalDateTime bookedAt;

    public BookingEntity() {}

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

    public void setBookingCode(String bookingCode) { this.bookingCode = bookingCode; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setTripId(Long tripId) { this.tripId = tripId; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }
    public void setPassengerPhone(String passengerPhone) { this.passengerPhone = passengerPhone; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public void setBookedAt(LocalDateTime bookedAt) { this.bookedAt = bookedAt; }
}
