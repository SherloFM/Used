package com.example.Used;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UsedApplication {

	public static void main(String[] args) {
		SpringApplication.run(UsedApplication.class, args);
	}

}
