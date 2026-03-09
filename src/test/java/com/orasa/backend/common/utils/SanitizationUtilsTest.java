package com.orasa.backend.common.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SanitizationUtilsTest {

    @Test
    void shouldSanitizeName() {
        assertEquals("My Business", SanitizationUtils.sanitizeName("My Business!"));
        assertEquals("My Business 1", SanitizationUtils.sanitizeName("My Business #1"));
        assertEquals("My Business", SanitizationUtils.sanitizeName("  My Business  "));
        assertEquals("My Business With A Long Name That I", SanitizationUtils.sanitizeName("My Business With A Long Name That Is Exactly 35 Characters Long But With Extra Stuff!"));
    }

    @Test
    void shouldHandleNull() {
        assertEquals("", SanitizationUtils.sanitizeName(null));
    }

    @Test
    void shouldRemoveSpecialCharacters() {
        assertEquals("Hello World 123", SanitizationUtils.sanitizeName("Hello! @World# 123..."));
    }

    @Test
    void shouldTruncateTo35() {
        String longName = "A".repeat(50);
        assertEquals("A".repeat(35), SanitizationUtils.sanitizeName(longName));
    }
}
