package com.example.marketplace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling is enabled here because PaymentService expires abandoned COD
 * requests and unpaid wallet-gateway sessions on a timer. Without it those
 * {@code @Scheduled} methods would compile and never run.
 */
@SpringBootApplication
@EnableScheduling
public class MarketplaceServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MarketplaceServiceApplication.class, args);
	}

}
