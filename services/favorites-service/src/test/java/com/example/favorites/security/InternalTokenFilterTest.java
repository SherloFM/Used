package com.example.favorites.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalTokenFilterTest {

    @Test
    void shortSecretIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new InternalTokenFilter("short"));
    }
}
