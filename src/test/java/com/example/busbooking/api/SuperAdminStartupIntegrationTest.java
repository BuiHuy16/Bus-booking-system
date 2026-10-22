package com.example.busbooking.api;

import com.example.busbooking.business.port.notification.EmailVerificationSender;
import com.example.busbooking.business.port.repository.UserRepository;
import com.example.busbooking.domain.enums.RoleName;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "auth.bootstrap-super-admin.enabled=true",
        "auth.bootstrap-super-admin.email=startup.phase4.root@example.com",
        "auth.bootstrap-super-admin.password=StartupPassword123",
        "auth.bootstrap-super-admin.full-name=Root",
        "auth.bootstrap-super-admin.phone=0901234567"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SuperAdminStartupIntegrationTest {
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmailVerificationSender sender;

    @AfterEach
    void cleanup() { jdbc.update("DELETE FROM users WHERE email = 'startup.phase4.root@example.com'"); }

    @Test
    void explicitStartupCreatesTheOnlyRootAndStillRequiresTheEmailOtp() throws Exception {
        var root = users.findSuperAdmin().orElseThrow();
        assertThat(root.getEmail()).isEqualTo("startup.phase4.root@example.com");
        assertThat(root.getRole().getName()).isEqualTo(RoleName.SUPER_ADMIN);
        assertThat(root.isEmailVerified()).isFalse();
        var code = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq(root.getEmail()), code.capture(), any());
        byte[] login = json.writeValueAsBytes(Map.of("email", root.getEmail(), "password", "StartupPassword123"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("email", root.getEmail(), "code", code.getValue()))))
                .andExpect(status().isNoContent());
        var response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("SUPER_ADMIN")).andReturn();
        String token = json.readTree(response.getResponse().getContentAsString()).get("token").asString();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("SUPER_ADMIN"));
    }
}
