package com.example.busbooking.business;

import com.example.busbooking.business.command.SaveOperatorCommand;
import com.example.busbooking.business.port.repository.OperatorRepository;
import com.example.busbooking.business.service.OperatorService;
import com.example.busbooking.domain.enums.OperatorStatus;
import com.example.busbooking.domain.exception.*;
import com.example.busbooking.domain.model.Operator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperatorServiceTest {
    @Mock OperatorRepository operators;

    @Test void createNormalizesFieldsAndAlwaysUsesActiveStatus() {
        OperatorService service = new OperatorService(operators);
        when(operators.save(any())).thenAnswer(invocation -> {
            Operator value = invocation.getArgument(0);
            return new Operator(1L, value.name(), value.address(), value.phone(), value.email(),
                    value.status(), LocalDateTime.of(2026, 10, 24, 10, 0));
        });

        Operator result = service.create(new SaveOperatorCommand("  Nhà   xe A ", "  Hà   Nội ",
                "+84901234567", " CONTACT@EXAMPLE.COM ", OperatorStatus.INACTIVE));

        assertThat(result.name()).isEqualTo("Nhà xe A");
        assertThat(result.address()).isEqualTo("Hà Nội");
        assertThat(result.phone()).isEqualTo("0901234567");
        assertThat(result.email()).isEqualTo("contact@example.com");
        assertThat(result.status()).isEqualTo(OperatorStatus.ACTIVE);
    }

    @Test void createRejectsDuplicateNameOrEmail() {
        OperatorService service = new OperatorService(operators);
        when(operators.existsByName("Operator", null)).thenReturn(true);
        assertThatThrownBy(() -> service.create(command("Operator", OperatorStatus.ACTIVE)))
                .isInstanceOf(DuplicateOperatorException.class);
        verify(operators, never()).save(any());
    }

    @Test void deactivationIsBlockedWhenOperationalDependenciesExist() {
        OperatorService service = new OperatorService(operators);
        Operator current = current(OperatorStatus.ACTIVE);
        when(operators.findByIdForUpdate(7L)).thenReturn(Optional.of(current));
        when(operators.hasOperationalDependencies(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(7L, command("Operator", OperatorStatus.INACTIVE)))
                .isInstanceOf(OperatorInUseException.class);
        verify(operators, never()).save(any());
    }

    @Test void ordinaryUpdateDoesNotCheckOperationalDependencies() {
        OperatorService service = new OperatorService(operators);
        when(operators.findByIdForUpdate(7L)).thenReturn(Optional.of(current(OperatorStatus.ACTIVE)));
        when(operators.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(7L, command("Renamed", OperatorStatus.ACTIVE));

        verify(operators, never()).hasOperationalDependencies(anyLong());
        verify(operators).save(argThat(value -> value.name().equals("Renamed")
                && value.createdAt().equals(current(OperatorStatus.ACTIVE).createdAt())));
    }

    @Test void updateRejectsMissingOperatorAndMissingStatus() {
        OperatorService service = new OperatorService(operators);
        when(operators.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(99L, command("Operator", OperatorStatus.ACTIVE)))
                .isInstanceOf(ResourceNotFoundException.class);

        when(operators.findByIdForUpdate(7L)).thenReturn(Optional.of(current(OperatorStatus.ACTIVE)));
        assertThatThrownBy(() -> service.update(7L, command("Operator", null)))
                .isInstanceOf(InvalidOperatorInputException.class);
    }

    @Test void validationRejectsInvalidContactAndOversizedQuery() {
        OperatorService service = new OperatorService(operators);
        assertThatThrownBy(() -> service.create(new SaveOperatorCommand("Operator", "Address",
                "+14155552671", "contact@example.com", null))).isInstanceOf(InvalidOperatorInputException.class);
        assertThatThrownBy(() -> service.create(new SaveOperatorCommand("Operator", "Address",
                "0901234567", "invalid", null))).isInstanceOf(InvalidOperatorInputException.class);
        assertThatThrownBy(() -> service.search("x".repeat(101), null))
                .isInstanceOf(InvalidOperatorInputException.class);
    }

    @Test void searchNormalizesBlankAndWhitespace() {
        OperatorService service = new OperatorService(operators);
        service.search("   ", OperatorStatus.ACTIVE);
        service.search("  nhà   xe ", null);
        verify(operators).search("", OperatorStatus.ACTIVE);
        verify(operators).search("nhà xe", null);
    }

    private SaveOperatorCommand command(String name, OperatorStatus status) {
        return new SaveOperatorCommand(name, "Address", "0901234567", "contact@example.com", status);
    }

    private Operator current(OperatorStatus status) {
        return new Operator(7L, "Operator", "Address", "0901234567", "contact@example.com",
                status, LocalDateTime.of(2026, 10, 24, 10, 0));
    }
}
