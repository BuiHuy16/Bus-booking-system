package com.example.busbooking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BusBookingApplicationTests {
    @Autowired ApplicationContext context;

	@Test
	void contextLoads() {
		assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
	}

}
