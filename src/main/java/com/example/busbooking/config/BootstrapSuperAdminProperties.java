package com.example.busbooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Set;

@ConfigurationProperties(prefix = "auth.bootstrap-super-admin")
public class BootstrapSuperAdminProperties {
    private boolean enabled;
    private String email;
    private String password;
    private String fullName;
    private String phone;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getEmail() { return email; }
    public void setEmail(String value) { email = value; }
    public String getPassword() { return password; }
    public void setPassword(String value) { password = value; }
    public String getFullName() { return fullName; }
    public void setFullName(String value) { fullName = value; }
    public String getPhone() { return phone; }
    public void setPhone(String value) { phone = value; }

    public void requireAllowedProfiles(String[] profiles) {
        if (!enabled) { return; }
        if (profiles.length == 0) { throw new IllegalStateException("Super admin bootstrap is restricted to dev/test profiles"); }
        for (String profile : profiles) {
            if (!Set.of("dev", "test").contains(profile)) {
                throw new IllegalStateException("Super admin bootstrap is restricted to dev/test profiles");
            }
        }
    }

    @Override public String toString() { return "BootstrapSuperAdminProperties[REDACTED]"; }
}
