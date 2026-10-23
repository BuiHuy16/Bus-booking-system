package com.example.busbooking.config;

import com.example.busbooking.business.command.RegisterCommand;
import com.example.busbooking.business.service.BootstrapSuperAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BootstrapSuperAdminConfigTest {
    private final UserConfig config = new UserConfig();
    private final BootstrapSuperAdminService service = mock(BootstrapSuperAdminService.class);

    @Test
    void disabledBootstrapDoesNothingAndNeedsNoCredentialsInAnyProfile() throws Exception {
        var props = new BootstrapSuperAdminProperties();
        var environment = new MockEnvironment(); environment.setActiveProfiles("prod");
        config.superAdminBootstrapRunner(props, environment, service).run(new DefaultApplicationArguments());
        verifyNoInteractions(service);
    }

    @Test
    void enabledBootstrapRejectsDefaultProdAndMixedProfilesBeforeCreatingAnything() {
        var props = new BootstrapSuperAdminProperties(); props.setEnabled(true);
        for (String[] profiles : new String[][] {{}, {"prod"}, {"dev", "prod"}, {"test", "staging"}}) {
            var environment = new MockEnvironment(); environment.setActiveProfiles(profiles);
            assertThatThrownBy(() -> config.superAdminBootstrapRunner(props, environment, service).run(new DefaultApplicationArguments()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("dev/test");
        }
        verifyNoInteractions(service);
    }

    @Test
    void devAndTestAreAllowedAndPropertiesNeverPrintPassword() throws Exception {
        var props = new BootstrapSuperAdminProperties(); props.setEnabled(true);
        props.setEmail("root@example.com"); props.setFullName("Root"); props.setPhone("0901234567"); props.setPassword("SecretPassword123");
        when(service.bootstrap(any())).thenReturn(BootstrapSuperAdminService.Result.CREATED_EMAIL_PENDING);
        for (String profile : new String[] {"dev", "test"}) {
            var environment = new MockEnvironment(); environment.setActiveProfiles(profile);
            config.superAdminBootstrapRunner(props, environment, service).run(new DefaultApplicationArguments());
        }
        verify(service, times(2)).bootstrap(new RegisterCommand("Root", "root@example.com", "SecretPassword123", "0901234567"));
        assertThat(props.toString()).doesNotContain("SecretPassword123");
    }
}
