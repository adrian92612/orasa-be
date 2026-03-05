package com.orasa.backend.service.sms;

public class SmsUtils {

    private SmsUtils() {
    }

    /**
     * Sanitizes message for 'plain' type (GSM 7-bit).
     * Replaces smart quotes, dashes, and other non-GSM characters.
     */
    public static String sanitizeMessage(String message) {
        if (message == null) {
            return "";
        }

        return message
                // Smart single quotes
                .replace('‘', '\'')
                .replace('’', '\'')
                // Smart double quotes
                .replace('“', '\"')
                .replace('”', '\"')
                // Dashes
                .replace('–', '-') // en dash
                .replace('—', '-') // em dash
                // Ellipsis
                .replace("…", "...")
                // Grave accent
                .replace('`', '\'');
    }
}
