package com.example.busbooking.data.adapter;

import com.example.busbooking.business.port.repository.TripRepository;
import com.example.busbooking.data.entity.TripEntity;
import com.example.busbooking.data.mapper.TripPersistenceMapper;
import com.example.busbooking.data.repository.SpringDataTripRepository;
import com.example.busbooking.domain.model.Trip;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class TripRepositoryAdapter implements TripRepository {

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private final SpringDataTripRepository springDataTripRepository;
    private final TripPersistenceMapper tripPersistenceMapper;

    public TripRepositoryAdapter(
            SpringDataTripRepository springDataTripRepository,
            TripPersistenceMapper tripPersistenceMapper) {
        this.springDataTripRepository = springDataTripRepository;
        this.tripPersistenceMapper = tripPersistenceMapper;
    }

    @Override
    public List<Trip> findForAdmin(Long routeId, com.example.busbooking.domain.enums.TripStatus status,
                                   LocalDate departureDate) {
        return springDataTripRepository.findForAdmin(routeId, status,
                departureDate == null ? null : departureDate.atStartOfDay(),
                departureDate == null ? null : departureDate.plusDays(1).atStartOfDay(),
                routeId == null, status == null, departureDate == null).stream()
                .map(tripPersistenceMapper::toDomain).toList();
    }

    @Override
    public Trip save(Trip trip) {
        TripEntity entity = trip.getId() == null ? new TripEntity()
                : springDataTripRepository.findById(trip.getId()).orElseThrow();
        entity.updateDetails(trip.getRouteId(), trip.getVehicleId(), trip.getDepartureTime(),
                trip.getArrivalTime(), trip.getPrice(), trip.getAvailableSeats(), trip.getStatus());
        entity = springDataTripRepository.saveAndFlush(entity);
        // Scalar foreign keys own these columns. Refresh only this trip after flushing so read-only
        // route/vehicle associations reflect changed IDs, without clearing other pending work.
        entityManager.refresh(entity);
        return tripPersistenceMapper.toDomain(entity);
    }

    @Override
    public List<Trip> search(
            Long originId,
            Long destinationId,
            LocalDate departureDate) {

        List<TripEntity> entities =
                springDataTripRepository.search(
                        originId,
                        destinationId,
                        departureDate == null ? null : departureDate.atStartOfDay(),
                        departureDate == null ? null : departureDate.plusDays(1).atStartOfDay()
                );

        return entities.stream()
                .map(tripPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Trip> findById(Long tripId) {
        return springDataTripRepository.findDetailedById(tripId)
                .map(tripPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Trip> findByIdForUpdate(Long tripId) {
        return springDataTripRepository.findByIdForUpdate(tripId)
                .map(tripPersistenceMapper::toDomain);
    }

    @Override
    public boolean updateAvailableSeats(Long tripId, int seatDelta) {
        return springDataTripRepository.updateAvailableSeats(tripId, seatDelta) == 1;
    }
}
