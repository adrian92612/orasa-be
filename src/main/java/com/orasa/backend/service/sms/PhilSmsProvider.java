package com.orasa.backend.service.sms;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PhilSMS API Provider.
 * Official API documentation: https://dashboard.philsms.com/api/v3/
 * 
 * IMPORTANT: SMART TELCO does not allow URL shorteners.
 * Avoid sending identical messages repeatedly to the same number (spam protection).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PhilSmsProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${philsms.base-url}")
    private String BASE_URL;

    @Value("${philsms.api-token}")
    private String apiToken;

    @Value("${philsms.sender-id}")
    private String senderId;

    /**
     * Sends an SMS message to a single recipient.
     * 
     * @param recipient Phone number in format 639XXXXXXXXX
     * @param message   SMS body text
     * @return SendSmsResult with success status and provider message ID
     */
    public SendSmsResult sendSms(String recipient, String message) {
        try {
            HttpHeaders headers = createHeaders();
            
            Map<String, Object> body = Map.of(
                "recipient", formatPhoneNumber(recipient),
                "sender_id", senderId,
                "type", "plain",
                "message", message
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                BASE_URL + "/sms/send",
                HttpMethod.POST,
                request,
                String.class
            );

            JsonNode responseBody = objectMapper.readTree(response.getBody());
            String status = responseBody.path("status").asText();

            if ("success".equals(status)) {
                String uid = responseBody.path("data").path("uid").asText("");
                log.info("SMS sent successfully to {} with provider ID: {}", recipient, uid);
                return SendSmsResult.success(uid.isEmpty() ? "sent" : uid);
            } else {
                String errorMessage = responseBody.path("message").asText("Unknown error");
                log.error("Failed to send SMS to {}: {}", recipient, errorMessage);
                return SendSmsResult.failure(errorMessage);
            }

        } catch (RestClientException e) {
            log.error("HTTP error sending SMS to {}: {}", recipient, e.getMessage());
            return SendSmsResult.failure("HTTP error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error sending SMS to {}: {}", recipient, e.getMessage());
            return SendSmsResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Gets the current SMS credit balance.
     * 
     * @return BalanceResult with remaining credits
     */
    public BalanceResult getBalance() {
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                BASE_URL + "/balance",
                HttpMethod.GET,
                request,
                String.class
            );

            JsonNode responseBody = objectMapper.readTree(response.getBody());
            String status = responseBody.path("status").asText();

            if ("success".equals(status)) {
                // Parse balance from data - actual structure depends on PhilSMS response
                JsonNode data = responseBody.path("data");
                int remainingCredits = data.path("remaining_unit").asInt(0);
                log.info("PhilSMS balance check: {} credits remaining", remainingCredits);
                return BalanceResult.success(remainingCredits);
            } else {
                String errorMessage = responseBody.path("message").asText("Unknown error");
                log.error("Failed to get SMS balance: {}", errorMessage);
                return BalanceResult.failure(errorMessage);
            }

        } catch (Exception e) {
            log.error("Error checking SMS balance: {}", e.getMessage());
            return BalanceResult.failure(e.getMessage());
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");
        headers.setBearerAuth(apiToken);
        return headers;
    }

    /**
     * Formats phone number to PhilSMS required format (639XXXXXXXXX).
     * Handles various input formats: +639..., 09..., 639...
     */
    private String formatPhoneNumber(String phone) {
        if (phone == null) return "";
        
        // Remove all non-digits
        String digits = phone.replaceAll("[^0-9]", "");
        
        // Convert 09XX to 639XX format
        if (digits.startsWith("09") && digits.length() == 11) {
            return "63" + digits.substring(1);
        }
        
        // Already in 639 format
        if (digits.startsWith("63") && digits.length() == 12) {
            return digits;
        }
        
        // Return as-is if format is unknown
        return digits;
    }

    // ==================== Result Classes ====================

    public record SendSmsResult(boolean success, String providerId, String errorMessage) {
        public static SendSmsResult success(String providerId) {
            return new SendSmsResult(true, providerId, null);
        }
        public static SendSmsResult failure(String errorMessage) {
            return new SendSmsResult(false, null, errorMessage);
        }
    }

    public record BalanceResult(boolean success, int remainingCredits, String errorMessage) {
        public static BalanceResult success(int credits) {
            return new BalanceResult(true, credits, null);
        }
        public static BalanceResult failure(String errorMessage) {
            return new BalanceResult(false, 0, errorMessage);
        }
    }
}
