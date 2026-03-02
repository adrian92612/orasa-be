package com.orasa.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.orasa.backend.config.TestRedisConfig;

@SpringBootTest
@Import(TestRedisConfig.class)
class OrasaApplicationTests {

	@Test
	void contextLoads() {
	}

}
