package com.orasa.backend.service.sms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SmsUtilsTest {

    @Test
    void sanitizeMessage_shouldReplaceNonGsmCharacters() {
        // Test various special characters
        String input = "Hello ‘World’! “It’s” a test… with – dashes — and `backticks`.";
        String expected = "Hello 'World'! \"It's\" a test... with - dashes - and 'backticks'.";

        assertEquals(expected, SmsUtils.sanitizeMessage(input));
    }

    @Test
    void sanitizeMessage_nullShouldReturnEmpty() {
        assertEquals("", SmsUtils.sanitizeMessage(null));
    }

    @Test
    void sanitizeMessage_emptyShouldReturnEmpty() {
        assertEquals("", SmsUtils.sanitizeMessage(""));
    }
}
