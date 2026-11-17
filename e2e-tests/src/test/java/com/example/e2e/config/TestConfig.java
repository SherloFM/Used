package com.example.e2e.config;

/**
 * Central place for URLs and credentials used by the E2E tests.
 * Override with -DbaseUrl=..., -Dusername=..., -Dpassword=...
 */
public final class TestConfig {

    private TestConfig() {}

    public static final String BASE_URL =
            System.getProperty("baseUrl", "http://localhost:8080");

    public static final String USERNAME =
            System.getProperty("username", "alice@used.local");

    public static final String PASSWORD =
            System.getProperty("password", "password123");

    public static final String LISTING_IMAGE_PATH =
            System.getProperty("listingImagePath",
                    "services/frontend-service/src/main/resources/static/images/laptop.jpg");

    public static final String PROFILE_IMAGE_PATH =
            System.getProperty("profileImagePath",
                    "services/frontend-service/src/main/resources/static/images/avatar1.jpg");
}
