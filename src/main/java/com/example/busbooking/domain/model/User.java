package com.example.busbooking.domain.model;

import com.example.busbooking.domain.enums.UserStatus;
import java.time.Instant;

public final class User {
    private final Long id;
    private final String fullName;
    private final String email;
    private final String passwordHash;
    private final String phone;
    private final Role role;
    private final UserStatus status;
    private final boolean emailVerified;
    private final Instant createdAt;
    private final Instant updatedAt;

    public User(Long id, String fullName, String email, String passwordHash, String phone, Role role, UserStatus status, Instant createdAt, Instant updatedAt) {
        this(id, fullName, email, passwordHash, phone, role, status, false, createdAt, updatedAt);
    }

    public User(Long id, String fullName, String email, String passwordHash, String phone, Role role, UserStatus status, boolean emailVerified, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phone = phone;
        this.role = role;
        this.status = status;
        this.emailVerified = emailVerified;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }

    public String getFullName() { return fullName; }

    public String getEmail() { return email; }

    public String getPasswordHash() { return passwordHash; }

    public String getPhone() { return phone; }

    public Role getRole() { return role; }

    public UserStatus getStatus() { return status; }

    public boolean isEmailVerified() { return emailVerified; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
