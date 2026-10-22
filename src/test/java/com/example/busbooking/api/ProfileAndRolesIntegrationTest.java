package com.example.busbooking.api;

import com.example.busbooking.business.command.*;
import com.example.busbooking.business.port.notification.EmailVerificationSender;
import com.example.busbooking.business.port.repository.*;
import com.example.busbooking.business.service.*;
import com.example.busbooking.domain.enums.*;
import com.example.busbooking.domain.exception.*;
import com.example.busbooking.domain.model.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"auth.rate-limit.login-limit=500", "auth.rate-limit.verify-limit=500"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(ProfileAndRolesIntegrationTest.AdminFixture.class)
class ProfileAndRolesIntegrationTest {
    private static final String ROOT = "phase4.root@example.com";
    private static final String EMPLOYEE = "phase4.employee@example.com";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuthService auth;
    @Autowired EmailVerificationService verification;
    @Autowired BootstrapSuperAdminService bootstrap;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired RoleManagementRepository roleChanges;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmailVerificationSender sender;
    private final Map<String, String> codes = new ConcurrentHashMap<>();

    @BeforeEach
    void setup() {
        cleanup();
        doAnswer(call -> { codes.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(sender).send(anyString(), anyString(), any());
    }
    @AfterEach
    void cleanup() { jdbc.update("DELETE FROM users WHERE email LIKE 'phase4.%@example.com'"); }

    @Test
    void readsAndUpdatesOwnProfileWithExistingJwtAndMatchingAuthMe() throws Exception {
        String token = userToken(EMPLOYEE);
        var original = users.findByEmail(EMPLOYEE).orElseThrow();
        profile(token).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(EMPLOYEE));
        putProfile(token, Map.of("fullName", " Hoang Updated ", "phone", "+84912345678"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Hoang Updated"))
                .andExpect(jsonPath("$.phone").value("0912345678"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Hoang Updated"));
        var updated = users.findById(original.getId()).orElseThrow();
        assertThat(updated.getPasswordHash()).isEqualTo(original.getPasswordHash());
        assertThat(updated.getRole().getName()).isEqualTo(RoleName.USER);
        assertThat(updated.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(original.getUpdatedAt());
    }

    @Test
    void rejectsMissingInvalidAndPrivilegedFieldsWithoutChangingProfile() throws Exception {
        String token = userToken(EMPLOYEE);
        for (String field : List.of("id", "role", "status", "email", "password", "emailVerified", "createdAt", "updatedAt")) {
            var body = new HashMap<String, Object>(Map.of("fullName", "Changed", "phone", "0901234567"));
            body.put(field, "SUPER_ADMIN");
            putProfile(token, body).andExpect(status().isBadRequest());
        }
        putProfile(token, Map.of("fullName", "Changed")).andExpect(status().isBadRequest());
        putProfile(token, Map.of("fullName", " ", "phone", "0901234567")).andExpect(status().isBadRequest());
        putProfile(token, Map.of("fullName", "Changed", "phone", "+14155552671")).andExpect(status().isBadRequest());
        assertThat(users.findByEmail(EMPLOYEE).orElseThrow().getFullName()).isEqualTo("Hoang");
    }

    @Test
    void queryIdDoesNotSelectAnotherAccountAndMissingJwtIs401() throws Exception {
        String token = userToken(EMPLOYEE);
        userToken("phase4.other@example.com");
        long other = users.findByEmail("phase4.other@example.com").orElseThrow().getId();
        mvc.perform(get("/api/users/me").param("id", Long.toString(other)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(EMPLOYEE))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dedicatedProfileUpdateDoesNotOverwriteConcurrentRoleOrReviveAnIneligibleUser() {
        userToken(EMPLOYEE);
        User stale = users.findByEmail(EMPLOYEE).orElseThrow();
        jdbc.update("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'ADMIN') WHERE id = ?", stale.getId());
        User updated = users.updateProfile(stale.getId(), "Updated", "0912345678").orElseThrow();
        assertThat(updated.getRole().getName()).isEqualTo(RoleName.ADMIN);
        assertThat(updated.getPasswordHash()).isEqualTo(stale.getPasswordHash());
        assertThat(updated.isEmailVerified()).isTrue();
        jdbc.update("UPDATE users SET status = 'INACTIVE' WHERE id = ?", stale.getId());
        assertThat(users.updateProfile(stale.getId(), "Should not persist", "0901234567")).isEmpty();
        jdbc.update("UPDATE users SET status = 'ACTIVE', email_verified = FALSE WHERE id = ?", stale.getId());
        assertThat(users.updateProfile(stale.getId(), "Should not persist", "0901234567")).isEmpty();
        assertThat(users.findById(stale.getId()).orElseThrow().getFullName()).isEqualTo("Updated");
    }

    @Test
    void superAdminMustVerifyOtpBeforeLoginAndCanUseAdminFunctionsAndOwnProfile() throws Exception {
        assertThat(bootstrap.bootstrap(rootCommand(ROOT))).isEqualTo(BootstrapSuperAdminService.Result.CREATED_VERIFICATION_SENT);
        postJson("/api/auth/login", Map.of("email", ROOT, "password", "Password123"))
                .andExpect(status().isUnauthorized());
        postJson("/api/auth/verify-email", Map.of("email", ROOT, "code", codes.get(ROOT))).andExpect(status().isNoContent());
        var response = postJson("/api/auth/login", Map.of("email", ROOT, "password", "Password123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("SUPER_ADMIN")).andReturn();
        String token = json.readTree(response.getResponse().getContentAsString()).get("token").asString();
        profile(token).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("SUPER_ADMIN"));
        putProfile(token, Map.of("fullName", "Root Updated", "phone", "0901234567")).andExpect(status().isOk());
        admin(token).andExpect(status().isOk());
    }

    @Test
    void onlySuperAdminCanPromoteAndDemoteAndOldJwtSeesTheNewRole() throws Exception {
        String root = rootToken();
        String employee = userToken(EMPLOYEE);
        long target = users.findByEmail(EMPLOYEE).orElseThrow().getId();
        changeRole(employee, target, "ADMIN").andExpect(status().isForbidden());
        admin(employee).andExpect(status().isForbidden());
        changeRole(root, target, "ADMIN").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        admin(employee).andExpect(status().isOk());
        changeRole(employee, target, "USER").andExpect(status().isForbidden());
        changeRole(root, target, "USER").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"));
        admin(employee).andExpect(status().isForbidden());
    }

    @Test
    void cannotCreateAnotherSuperAdminOrChangeTheSuperAdminsRole() throws Exception {
        String root = rootToken();
        userToken(EMPLOYEE);
        long id = users.findByEmail(EMPLOYEE).orElseThrow().getId();
        changeRole(root, id, "SUPER_ADMIN").andExpect(status().isBadRequest());
        changeRole(root, users.findSuperAdmin().orElseThrow().getId(), "USER").andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users u JOIN roles r ON r.id = u.role_id WHERE r.name = 'SUPER_ADMIN'", Long.class)).isEqualTo(1L);
    }

    @Test
    void numericRoleValuesAre400AndNeverChangeTheTargetRole() throws Exception {
        String root = rootToken();
        userToken(EMPLOYEE);
        long id = users.findByEmail(EMPLOYEE).orElseThrow().getId();
        for (String value : List.of("0", "1", "\"0\"", "\"1\"")) {
            mvc.perform(put("/api/admin/users/" + id + "/role")
                            .header("Authorization", "Bearer " + root)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":" + value + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
            assertThat(users.findById(id).orElseThrow().getRole().getName()).isEqualTo(RoleName.USER);
        }
        changeRole(root, id, "ADMIN").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        changeRole(root, id, "USER").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void promotionRequiresVerifiedActiveTargetButDemotionCanRevokeAnInactiveAdminsRole() throws Exception {
        String root = rootToken();
        auth.register(registration(EMPLOYEE));
        long id = users.findByEmail(EMPLOYEE).orElseThrow().getId();
        changeRole(root, id, "ADMIN").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ACCOUNT_NOT_ELIGIBLE"));
        verification.verify(new VerifyEmailCommand(EMPLOYEE, codes.get(EMPLOYEE)));
        jdbc.update("UPDATE users SET status = 'INACTIVE' WHERE id = ?", id);
        changeRole(root, id, "ADMIN").andExpect(status().isConflict());
        jdbc.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", id);
        changeRole(root, id, "ADMIN").andExpect(status().isOk());
        jdbc.update("UPDATE users SET status = 'INACTIVE', email_verified = FALSE WHERE id = ?", id);
        changeRole(root, id, "USER").andExpect(status().isOk());
        var stored = users.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(stored.isEmailVerified()).isFalse();
    }

    @Test
    void missingTargetIs404AndRoleRequestCannotOverrideActorOrOtherFields() throws Exception {
        String root = rootToken();
        changeRole(root, Long.MAX_VALUE, "ADMIN").andExpect(status().isNotFound());
        long id = users.findSuperAdmin().orElseThrow().getId();
        mvc.perform(put("/api/admin/users/" + id + "/role").header("Authorization", "Bearer " + root)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("role", "ADMIN", "actorId", 999))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/users/1/role").contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dataLayerRechecksActorAndTargetBeforeUpdatingRoles() {
        rootToken(); userToken(EMPLOYEE);
        long actor = users.findSuperAdmin().orElseThrow().getId();
        long target = users.findByEmail(EMPLOYEE).orElseThrow().getId();
        jdbc.update("UPDATE users SET status = 'INACTIVE' WHERE id = ?", actor);
        assertThatThrownBy(() -> roleChanges.changeRole(actor, target, RoleName.ADMIN)).isInstanceOf(InvalidCredentialsException.class);
        jdbc.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", actor);
        jdbc.update("UPDATE users SET email_verified = FALSE WHERE id = ?", target);
        assertThatThrownBy(() -> roleChanges.changeRole(actor, target, RoleName.ADMIN)).isInstanceOf(AccountNotEligibleException.class);
        assertThat(users.findById(target).orElseThrow().getRole().getName()).isEqualTo(RoleName.USER);
    }

    @Test
    void bootstrapIsIdempotentAndCannotPromoteAnExistingUserOrReplaceRoot() {
        userToken(EMPLOYEE);
        var before = users.findByEmail(EMPLOYEE).orElseThrow();
        assertThatThrownBy(() -> bootstrap.bootstrap(rootCommand(EMPLOYEE))).isInstanceOf(IllegalStateException.class);
        assertThat(users.findById(before.getId()).orElseThrow().getPasswordHash()).isEqualTo(before.getPasswordHash());
        assertThat(users.findById(before.getId()).orElseThrow().getRole().getName()).isEqualTo(RoleName.USER);
        bootstrap.bootstrap(rootCommand(ROOT));
        User root = users.findSuperAdmin().orElseThrow();
        assertThat(bootstrap.bootstrap(new RegisterCommand(null, ROOT, null, null))).isEqualTo(BootstrapSuperAdminService.Result.ALREADY_PRESENT);
        assertThat(users.findSuperAdmin().orElseThrow().getPasswordHash()).isEqualTo(root.getPasswordHash());
        verify(sender, times(1)).send(eq(ROOT), anyString(), any());
        assertThatThrownBy(() -> bootstrap.bootstrap(rootCommand("phase4.other-root@example.com"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void databaseConstraintPreventsASecondSuperAdminEvenIfTheFirstIsInactive() {
        bootstrap.bootstrap(rootCommand(ROOT));
        var root = users.findSuperAdmin().orElseThrow();
        jdbc.update("UPDATE users SET status = 'INACTIVE' WHERE id = ?", root.getId());
        var second = new User(null, "Second", "phase4.second-root@example.com", root.getPasswordHash(), "0901234567",
                roles.findByName(RoleName.SUPER_ADMIN).orElseThrow(), UserStatus.ACTIVE, false, null, null);
        assertThatThrownBy(() -> users.save(second)).isInstanceOf(SuperAdminAlreadyExistsException.class);
        assertThat(users.findSuperAdmin().orElseThrow().getId()).isEqualTo(root.getId());
    }

    @Test
    void concurrentBootstrapOfSameEmailCreatesOneSuperAdminAndOneEmail() throws Exception {
        var outcomes = parallel(() -> bootstrap.bootstrap(rootCommand(ROOT)).name());
        assertThat(outcomes).containsExactlyInAnyOrder("CREATED_VERIFICATION_SENT", "ALREADY_PRESENT");
        verify(sender, times(1)).send(eq(ROOT), anyString(), any());
        assertThat(users.findSuperAdmin()).isPresent();
    }

    @Test
    void concurrentBootstrapOfDifferentEmailsCannotCreateTwoSuperAdmins() throws Exception {
        var sequence = new java.util.concurrent.atomic.AtomicInteger();
        var outcomes = parallel(() -> {
            String email = sequence.incrementAndGet() == 1 ? ROOT : "phase4.other-root@example.com";
            try { bootstrap.bootstrap(rootCommand(email)); return "created"; }
            catch (IllegalStateException rejected) { return "rejected"; }
        });
        assertThat(outcomes).containsExactlyInAnyOrder("created", "rejected");
        verify(sender, times(1)).send(anyString(), anyString(), any());
    }

    @Test
    void bootstrapSmtpFailureLeavesPendingRootRecoverableByResend() throws Exception {
        doThrow(new EmailDeliveryException()).when(sender).send(anyString(), anyString(), any());
        assertThat(bootstrap.bootstrap(rootCommand(ROOT))).isEqualTo(BootstrapSuperAdminService.Result.CREATED_EMAIL_PENDING);
        assertThat(users.findSuperAdmin().orElseThrow().isEmailVerified()).isFalse();
        doAnswer(call -> { codes.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(sender).send(anyString(), anyString(), any());
        jdbc.update("UPDATE email_verifications SET sent_at = sent_at - INTERVAL '61 seconds' WHERE user_id = (SELECT id FROM users WHERE email = ?)", ROOT);
        postJson("/api/auth/resend-verification", Map.of("email", ROOT)).andExpect(status().isAccepted());
        postJson("/api/auth/verify-email", Map.of("email", ROOT, "code", codes.get(ROOT))).andExpect(status().isNoContent());
        postJson("/api/auth/login", Map.of("email", ROOT, "password", "Password123")).andExpect(status().isOk());
    }

    private String rootToken() {
        bootstrap.bootstrap(rootCommand(ROOT));
        verification.verify(new VerifyEmailCommand(ROOT, codes.get(ROOT)));
        return auth.login(new LoginCommand(ROOT, "Password123")).token().value();
    }
    private String userToken(String email) {
        auth.register(registration(email));
        verification.verify(new VerifyEmailCommand(email, codes.get(email)));
        return auth.login(new LoginCommand(email, "Password123")).token().value();
    }
    private RegisterCommand registration(String email) { return new RegisterCommand("Hoang", email, "Password123", "0901234567"); }
    private RegisterCommand rootCommand(String email) { return new RegisterCommand("Root", email, "Password123", "0901234567"); }
    private ResultActions profile(String token) throws Exception { return mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token)); }
    private ResultActions admin(String token) throws Exception { return mvc.perform(get("/api/admin/phase4-check").header("Authorization", "Bearer " + token)); }
    private ResultActions putProfile(String token, Object body) throws Exception {
        return mvc.perform(put("/api/users/me").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)));
    }
    private ResultActions changeRole(String token, long userId, String role) throws Exception {
        return mvc.perform(put("/api/admin/users/" + userId + "/role").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("role", role))));
    }
    private ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)));
    }
    private List<String> parallel(Callable<String> operation) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CyclicBarrier(2);
        Callable<String> timed = () -> { start.await(5, TimeUnit.SECONDS); return operation.call(); };
        try {
            var first = executor.submit(timed); var second = executor.submit(timed);
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS); }
    }
    @RestController
    static class AdminFixture {
        @GetMapping("/api/admin/phase4-check") Map<String, String> check() { return Map.of("result", "ok"); }
    }
}
