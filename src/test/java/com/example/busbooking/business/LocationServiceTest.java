package com.example.busbooking.business;

import com.example.busbooking.business.command.SaveLocationCommand;
import com.example.busbooking.business.port.repository.LocationRepository;
import com.example.busbooking.business.service.LocationService;
import com.example.busbooking.domain.exception.BusinessException;
import com.example.busbooking.domain.exception.DuplicateLocationException;
import com.example.busbooking.domain.exception.ResourceNotFoundException;
import com.example.busbooking.domain.model.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {
    @Mock LocationRepository locations;

    @Test void createNormalizesWhitespaceBeforeCheckingAndSaving() {
        LocationService service = new LocationService(locations);
        when(locations.save(any())).thenAnswer(invocation -> {
            Location value = invocation.getArgument(0);
            return new Location(1L, value.name(), value.province(), value.address());
        });

        Location result = service.create(new SaveLocationCommand(
                "  Bến   xe Miền Đông ", " TP.   Hồ Chí Minh ", " 292  Đinh Bộ Lĩnh "));

        assertThat(result).isEqualTo(new Location(1L, "Bến xe Miền Đông", "TP. Hồ Chí Minh", "292 Đinh Bộ Lĩnh"));
        verify(locations).existsByIdentity("Bến xe Miền Đông", "TP. Hồ Chí Minh", "292 Đinh Bộ Lĩnh", null);
    }

    @Test void createRejectsDuplicate() {
        LocationService service = new LocationService(locations);
        when(locations.existsByIdentity("A", "B", "C", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new SaveLocationCommand("A", "B", "C")))
                .isInstanceOf(DuplicateLocationException.class);
        verify(locations, never()).save(any());
    }

    @Test void updateExcludesCurrentIdFromDuplicateCheck() {
        LocationService service = new LocationService(locations);
        when(locations.findById(7L)).thenReturn(Optional.of(new Location(7L, "Old", "Old", "Old")));
        when(locations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Location result = service.update(7L, new SaveLocationCommand("New", "Province", "Address"));

        assertThat(result.id()).isEqualTo(7L);
        verify(locations).existsByIdentity("New", "Province", "Address", 7L);
    }

    @Test void updateRejectsMissingLocation() {
        LocationService service = new LocationService(locations);
        when(locations.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new SaveLocationCommand("A", "B", "C")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(locations, never()).save(any());
    }

    @Test void searchTreatsBlankAsAllAndRejectsOverOneHundredCharacters() {
        LocationService service = new LocationService(locations);
        service.search("   ");
        verify(locations).search("");

        assertThatThrownBy(() -> service.search("x".repeat(101)))
                .isInstanceOf(BusinessException.class);
    }

    @Test void validationUsesNormalizedLengthAndRejectsBlankFields() {
        LocationService service = new LocationService(locations);
        service.create(new SaveLocationCommand("A".repeat(100), "B", "C"));
        assertThatThrownBy(() -> service.create(new SaveLocationCommand("A".repeat(101), "B", "C")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(new SaveLocationCommand(" \t ", "B", "C")))
                .isInstanceOf(BusinessException.class);
    }
}
