package com.example.moderation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ModerationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ModerationServiceApplication.class, args);
	}

}
