package com.orasa.backend.common.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SanitizationUtils {

    /**
     * Sanitizes a name (business or branch) for SMS compatibility.
     * 1. Replaces non-alphanumeric characters (except spaces) with an empty string.
     * 2. Truncates to 35 characters.
     * 3. Trims leading/trailing whitespace.
     *
     * @param name The name to sanitize
     * @return The sanitized name, or an empty string if input is null
     */
    public static String sanitizeName(String name) {
        if (name == null) {
            return "";
        }

        // 1. Remove non-alphanumeric and non-space characters
        String sanitized = name.replaceAll("[^a-zA-Z0-9 ]", "");

        // 2. Trim
        sanitized = sanitized.trim();

        // 3. Truncate to 35 characters
        if (sanitized.length() > 35) {
            sanitized = sanitized.substring(0, 35).trim();
        }

        return sanitized;
    }
}
