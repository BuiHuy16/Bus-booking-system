package com.example.busbooking.data;

import com.example.busbooking.business.port.repository.RoleRepository;
import com.example.busbooking.business.port.repository.UserRepository;
import com.example.busbooking.domain.enums.RoleName;
import com.example.busbooking.domain.enums.UserStatus;
import com.example.busbooking.domain.model.Role;
import com.example.busbooking.domain.model.User;
import com.example.busbooking.domain.exception.DuplicateEmailException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AuthPersistenceIntegrationTest {
    private static final String EMAIL = "day1.user@example.com";
    // Fixture only: password hashing belongs to the next implementation day.
    private static final String HASH_FIXTURE = "test-only-hash-placeholder";

    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanFixtures() {
        jdbc.update("DELETE FROM users WHERE email LIKE 'day1.%@example.com'");
    }

    @Test
    void seedsTheThreeSupportedRoles() {
        assertThat(roles.findByName(RoleName.USER)).isPresent();
        assertThat(roles.findByName(RoleName.ADMIN)).isPresent();
        assertThat(roles.findByName(RoleName.SUPER_ADMIN)).isPresent();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM roles", Long.class)).isEqualTo(3L);
    }

    @Test
    void savesNormalizedEmailAndLoadsRoleOutsideTheAdapterTransaction() {
        User saved = users.save(newUser(null, " Day1.User@Example.COM ", UserStatus.ACTIVE));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getPasswordHash()).isEqualTo(HASH_FIXTURE);
        assertThat(saved.toString()).doesNotContain(HASH_FIXTURE);

        User loaded = users.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getRole().getName()).isEqualTo(RoleName.USER);
        assertThat(loaded.getRole().getId()).isNotNull();
        assertThat(users.findByEmail(" DAY1.USER@EXAMPLE.COM ")).isPresent();
        assertThat(users.existsByEmail(" DAY1.USER@EXAMPLE.COM ")).isTrue();
        assertThat(users.findByEmail("day1.missing@example.com")).isEmpty();
    }

    @Test
    void rejectsDuplicateEmailAfterNormalization() {
        users.save(newUser(null, EMAIL, UserStatus.ACTIVE));
        assertThatThrownBy(() -> users.save(newUser(null, " DAY1.USER@EXAMPLE.COM ", UserStatus.ACTIVE)))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("Email is already registered");
    }

    @Test
    void adapterDoesNotTranslateForeignKeyFailuresIntoDuplicateEmail() {
        User invalid = new User(null, "Day One", EMAIL, HASH_FIXTURE, "0901234567",
                new Role(-1L, RoleName.USER), UserStatus.ACTIVE, null, null);
        assertThatThrownBy(() -> users.save(invalid))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("fk_users_role");
    }

    @Test
    void adapterDoesNotTranslateCheckFailuresIntoDuplicateEmail() {
        User invalid = new User(null, " ", EMAIL, HASH_FIXTURE, "0901234567",
                roles.findByName(RoleName.USER).orElseThrow(), UserStatus.ACTIVE, null, null);
        assertThatThrownBy(() -> users.save(invalid))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("ck_users_full_name");
    }

    @Test
    void databaseRejectsUnnormalizedEmailWrittenDirectly() {
        assertRejected("Day1.User@Example.COM", roleId(), "ACTIVE", "ck_users_email_normalized");
        assertRejected(" day1.user@example.com ", roleId(), "ACTIVE", "ck_users_email_normalized");
    }

    @Test
    void databaseRejectsUnknownRole() {
        assertRejected(EMAIL, -1L, "ACTIVE", "fk_users_role");
    }

    @Test
    void databaseRejectsUnknownStatus() {
        assertRejected(EMAIL, roleId(), "DISABLED", "ck_users_status");
    }

    @Test
    void databaseRequiresAUserName() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO users (full_name, email, password, phone, role_id)
                VALUES (' ', ?, ?, '0901234567', ?)
                """, EMAIL, HASH_FIXTURE, roleId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("ck_users_full_name");
    }

    @Test
    void databaseRequiresARole() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO users (full_name, email, password, phone)
                VALUES ('Day One', ?, ?, '0901234567')
                """, EMAIL, HASH_FIXTURE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseDefaultsNewUsersToActive() {
        jdbc.update("""
                INSERT INTO users (full_name, email, password, phone, role_id)
                VALUES ('Day One', ?, ?, '0901234567', ?)
                """, EMAIL, HASH_FIXTURE, roleId());
        assertThat(users.findByEmail(EMAIL).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void databaseOwnsTimestampsForDirectSqlUpdates() {
        jdbc.update("""
                INSERT INTO users (full_name, email, password, phone, role_id, created_at, updated_at)
                VALUES ('Day One', ?, ?, '0901234567', ?, '2020-01-01T00:00:00Z', '2020-01-01T00:00:00Z')
                """, EMAIL, HASH_FIXTURE, roleId());
        jdbc.update("UPDATE users SET full_name = 'Updated', created_at = CURRENT_TIMESTAMP WHERE email = ?", EMAIL);
        User updated = users.findByEmail(EMAIL).orElseThrow();
        assertThat(updated.getCreatedAt()).isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));
        assertThat(updated.getUpdatedAt()).isAfter(updated.getCreatedAt());
    }

    @Test
    void updatingUserPreservesCreationTimeAndPersistsStatus() {
        User saved = users.save(newUser(null, EMAIL, UserStatus.ACTIVE));
        User updated = users.save(newUser(saved.getId(), EMAIL, UserStatus.INACTIVE));
        assertThat(updated.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(saved.getUpdatedAt());
        assertThat(users.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    private User newUser(Long id, String email, UserStatus status) {
        Role role = roles.findByName(RoleName.USER).orElseThrow();
        return new User(id, "Nguyễn Văn Huy Hoàng", email, HASH_FIXTURE, "0901234567",
                role, status, null, null);
    }

    private Long roleId() {
        return roles.findByName(RoleName.USER).orElseThrow().getId();
    }

    private void assertRejected(String email, Long roleId, String status, String constraint) {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO users (full_name, email, password, phone, role_id, status)
                VALUES ('Day One', ?, ?, '0901234567', ?, ?)
                """, email, HASH_FIXTURE, roleId, status))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining(constraint);
    }
}
