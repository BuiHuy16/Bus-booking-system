package com.example.busbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// API identity is loaded by our JWT filter; do not create Boot's fallback user/password.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BusBookingApplication {

	public static void main(String[] args) {
		SpringApplication.run(BusBookingApplication.class, args);
	}

}
