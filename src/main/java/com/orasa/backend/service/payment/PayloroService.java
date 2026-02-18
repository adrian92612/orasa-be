package com.orasa.backend.service.payment;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orasa.backend.config.OrasaProperties;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayloroService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OrasaProperties orasaProperties;

    public PayloroResponse createPayment(PayloroRequest request) {
        try {
            request.setMerchantNo(orasaProperties.getPayloro().getMerchantNo());
            String sign = generateSignature(request);
            request.setSign(sign);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PayloroRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                orasaProperties.getPayloro().getBaseUrl() + "/pay/code",
                entity,
                String.class
            );

            log.debug("Payloro response: {}", response.getBody());

            JsonNode root = objectMapper.readTree(response.getBody());
            if ("200".equals(root.path("status").asText())) {
                JsonNode data = root.path("data");
                return new PayloroResponse(
                    true,
                    data.path("paymentLink").asText(),
                    data.path("paymentImage").asText(),
                    data.path("platOrderNo").asText(),
                    null
                );
            } else {
                String error = root.path("message").asText("Unknown error from Payloro");
                log.error("Payloro error response: {}", error);
                return new PayloroResponse(false, null, null, null, error);
            }

        } catch (Exception e) {
            log.error("Payloro payment execution error", e);
            return new PayloroResponse(false, null, null, null, e.getMessage());
        }
    }

    private String generateSignature(PayloroRequest request) throws Exception {
        // Order according to payloro.md: 
        // description + email + merchantNo + merchantOrderNo + method + mobile + name + payAmount
        String data = String.format("%s%s%s%s%s%s%s%s",
            request.getDescription(),
            request.getEmail(),
            request.getMerchantNo(),
            request.getMerchantOrderNo(),
            request.getMethod(),
            request.getMobile(),
            request.getName(),
            request.getPayAmount()
        );

        log.debug("Data to sign: [{}]", data);
        return sign(data, orasaProperties.getPayloro().getPrivateKey());
    }

    private String sign(String data, String privateKeyStr) throws Exception {
        if (privateKeyStr == null || privateKeyStr.isEmpty()) {
            throw new IllegalArgumentException("Payloro private key is not configured");
        }

        String realPK = privateKeyStr
            .replaceAll("-----BEGIN PRIVATE KEY-----", "")
            .replaceAll("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(realPK);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = kf.generatePrivate(spec);

        // Payloro likely uses SHA256withRSA based on standard practices, 
        // though the doc just says "RSA". PHP's openssl_sign usually defaults to SHA1 or SHA256.
        // I'll stick with SHA256withRSA for now.
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        byte[] signed = signature.sign();

        return Base64.getEncoder().encodeToString(signed);
    }

    @Data
    @Builder
    public static class PayloroRequest {
        private String merchantNo;
        private String merchantOrderNo;
        private String payAmount; // Format "0.00"
        private String description;
        private String method; // grabpay, gcash, maya, qrph
        private String name;
        private String mobile;
        private String email;
        private String sign;
    }

    public record PayloroResponse(
        boolean success,
        String paymentLink,
        String paymentImage,
        String platOrderNo,
        String errorMessage
    ) {}
}
