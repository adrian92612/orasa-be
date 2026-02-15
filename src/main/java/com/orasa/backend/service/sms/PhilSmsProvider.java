package com.orasa.backend.service.sms;

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.net.SocketTimeoutException;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.ResourceAccessException;

import com.fasterxml.jackson.databind.JsonNode;
import com.orasa.backend.config.OrasaProperties;
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
    private final OrasaProperties orasaProperties;

    /**
     * Sends an SMS message to a single recipient.
     * 
     * @param recipient Phone number in format 639XXXXXXXXX
     * @param message   SMS body text
     * @return SendSmsResult with success status and provider message ID
     */
    @Retryable(
        retryFor = { ResourceAccessException.class }, 
        maxAttempts = 2, 
        backoff = @Backoff(delay = 5000)
    )
    public SendSmsResult sendSms(String recipient, String message) {
        try {
            HttpHeaders headers = createHeaders();

            Map<String, Object> body = Map.of(
                "recipient", formatPhoneNumber(recipient),
                "sender_id", orasaProperties.getPhilsms().getSenderId(),
                "type", "plain",
                "message", message
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                orasaProperties.getPhilsms().getBaseUrl() + "/sms/send",
                HttpMethod.POST,
                request,
                String.class
            );

            String rawResponseBody = response.getBody();
            JsonNode responseBody = objectMapper.readTree(rawResponseBody != null ? rawResponseBody : "{}");
            String status = responseBody.path("status").asText();

            if ("success".equals(status)) {
                String uid = responseBody.path("data").path("uid").asText("");
                log.info("SMS sent successfully to {} with provider ID: {}", recipient, uid);
                return SendSmsResult.success(uid.isEmpty() ? "sent" : uid, rawResponseBody);
            } else {
                String errorMessage = responseBody.path("message").asText("Unknown error");
                log.error("Failed to send SMS to {}: {}", recipient, errorMessage);
                return SendSmsResult.failure(errorMessage, rawResponseBody);
            }

        } catch (ResourceAccessException e) {
            // Safety Hatch: Don't retry on Read Timeouts (potential duplicates)
            if (e.getCause() instanceof SocketTimeoutException) {
                log.error("Read timeout sending SMS to {}: {} - NOT retrying to avoid duplicates", recipient, e.getMessage());
                return SendSmsResult.failure("Read timeout - status uncertain", null);
            }
            
            // Connection Timeouts -> Safe to retry
            log.warn("Connection error sending SMS to {} (attempting retry): {}", recipient, e.getMessage());
            throw e; 
        } catch (RestClientException e) {
            log.error("HTTP error sending SMS to {}: {}", recipient, e.getMessage());
            return SendSmsResult.failure("HTTP error: " + e.getMessage(), null);
        } catch (Exception e) {
            log.error("Unexpected error sending SMS to {}: {}", recipient, e.getMessage());
            return SendSmsResult.failure("Unexpected error: " + e.getMessage(), null);
        }
    }

    @Recover
    public SendSmsResult recover(ResourceAccessException e, String recipient, String message) {
        log.error("All retry attempts failed for SMS to {}: {}", recipient, e.getMessage());
        return SendSmsResult.failure("Max retries reached. Connection error: " + e.getMessage(), null);
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
                orasaProperties.getPhilsms().getBaseUrl() + "/balance",
                HttpMethod.GET,
                request,
                String.class
            );

            JsonNode responseBody = objectMapper.readTree(response.getBody());
            String status = responseBody.path("status").asText();

            if ("success".equals(status)) {
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
        headers.setBearerAuth(orasaProperties.getPhilsms().getApiToken());
        return headers;
    }

    /**
     * Formats phone number to PhilSMS required format (639XXXXXXXXX).
     * Handles various input formats: +639..., 09..., 639...
     */
    private String formatPhoneNumber(String phone) {
        if (phone == null) return "";

        String digits = phone.replaceAll("[^0-9]", "");

        if (digits.startsWith("09") && digits.length() == 11) {
            return "63" + digits.substring(1);
        }
        
        if (digits.startsWith("63") && digits.length() == 12) {
            return digits;
        }

        if (digits.length() == 10 && digits.startsWith("9")) {
            return "63" + digits;
        }
        
        return digits;
    }

    // ==================== Result Classes ====================

    public record SendSmsResult(boolean success, String providerId, String errorMessage, String rawResponse) {
        public static SendSmsResult success(String providerId, String rawResponse) {
            return new SendSmsResult(true, providerId, null, rawResponse);
        }
        public static SendSmsResult failure(String errorMessage, String rawResponse) {
            return new SendSmsResult(false, null, errorMessage, rawResponse);
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
