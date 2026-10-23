package com.example.busbooking.data;

import com.example.busbooking.data.adapter.TripRepositoryAdapter;
import com.example.busbooking.data.entity.TripEntity;
import com.example.busbooking.data.mapper.TripPersistenceMapper;
import com.example.busbooking.data.repository.SpringDataTripRepository;
import com.example.busbooking.domain.model.Trip;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripRepositoryAdapterTest {

    @Mock
    private SpringDataTripRepository repository;

    @Mock
    private TripPersistenceMapper mapper;

    @Mock
    private Trip trip;

    @Test
    void search_withoutDatePassesBothDateBoundsAsNull() {
        when(repository.search(null, 2L, null, null)).thenReturn(List.of());
        TripRepositoryAdapter adapter = new TripRepositoryAdapter(repository, mapper);

        assertEquals(List.of(), adapter.search(null, 2L, null));

        verify(repository).search(null, 2L, null, null);
        verifyNoInteractions(mapper);
    }

    @Test
    void search_withDateUsesHalfOpenWholeDayRange() {
        TripEntity entity = mock(TripEntity.class);
        when(repository.search(eq(1L), eq(2L),
                eq(LocalDate.of(2026, 10, 25).atStartOfDay()),
                eq(LocalDate.of(2026, 10, 26).atStartOfDay())))
                .thenReturn(List.of(entity));
        when(mapper.toDomain(entity)).thenReturn(trip);
        TripRepositoryAdapter adapter = new TripRepositoryAdapter(repository, mapper);

        assertEquals(List.of(trip), adapter.search(1L, 2L, LocalDate.of(2026, 10, 25)));

        verify(mapper).toDomain(entity);
    }
}
