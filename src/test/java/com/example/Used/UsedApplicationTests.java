package com.example.Used;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Plain context-load smoke test. The "dev" profile is required because the
 * default profile has no DataSource configuration (see application-dev.properties).
 */
@SpringBootTest
@ActiveProfiles("dev")
class UsedApplicationTests {

	@Test
	void contextLoads() {
	}

}
