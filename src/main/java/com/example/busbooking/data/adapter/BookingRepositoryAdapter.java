package com.example.busbooking.data.adapter;

import com.example.busbooking.business.port.repository.BookingRepository;
import com.example.busbooking.data.entity.BookingEntity;
import com.example.busbooking.data.mapper.BookingPersistenceMapper;
import com.example.busbooking.data.repository.SpringDataBookingRepository;
import com.example.busbooking.domain.enums.BookingStatus;
import com.example.busbooking.domain.model.Booking;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class BookingRepositoryAdapter implements BookingRepository {
    private final SpringDataBookingRepository repository;
    private final BookingPersistenceMapper mapper;

    public BookingRepositoryAdapter(SpringDataBookingRepository repository, BookingPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Booking save(Booking booking) {
        return mapper.toDomain(repository.save(mapper.toEntity(booking)));
    }
    public long countConfirmedSeats(Long tripId) {
        return repository.countSeats(tripId, BookingStatus.CONFIRMED);
    }
    public List<Booking> findForAdmin(Long userId, Long tripId, BookingStatus status) {
        return repository.findForAdmin(userId, tripId, status, userId == null, tripId == null, status == null)
                .stream().map(mapper::toDomain).toList();
    }
    public Optional<Booking> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }
    public Optional<Booking> findByIdForUpdate(Long id) {
        return repository.findByIdForUpdate(id).map(mapper::toDomain);
    }
    public List<Booking> findByUserId(Long userId) {
        return repository.findAllByUserIdOrderByBookedAtDesc(userId).stream().map(mapper::toDomain).toList();
    }
    public Booking updateStatus(Long id, BookingStatus status) {
        repository.updateStatus(id, status);
        return repository.findById(id).map(mapper::toDomain).orElseThrow();
    }
}
