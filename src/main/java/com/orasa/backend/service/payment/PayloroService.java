package com.orasa.backend.service.payment;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;

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
        System.out.println("[PAYLORO] Initiating payment request for order: " + request.getMerchantOrderNo() + " (Amount: " + request.getPayAmount() + ")");
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

            System.out.println("[PAYLORO] Raw Response Body: " + response.getBody());
            log.info("Payloro response: {}", response.getBody());

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
                System.err.println("[PAYLORO] Error Status: " + root.path("status").asText() + ", Message: " + error);
                log.error("Payloro error response: {}", error);
                return new PayloroResponse(false, null, null, null, error);
            }

        } catch (Exception e) {
            log.error("Payloro payment execution error", e);
            return new PayloroResponse(false, null, null, null, e.getMessage());
        }
    }

    private String generateSignature(PayloroRequest request) throws Exception {
        // Order alphabetically: description + email + merchantNo + merchantOrderNo + method + mobile + name + notifyUrl + payAmount
        StringBuilder sb = new StringBuilder();
        sb.append(request.getDescription());
        sb.append(request.getEmail());
        sb.append(request.getMerchantNo());
        sb.append(request.getMerchantOrderNo());
        sb.append(request.getMethod());
        sb.append(request.getMobile());
        sb.append(request.getName());
        if (request.getNotifyUrl() != null && !request.getNotifyUrl().isEmpty()) {
            sb.append(request.getNotifyUrl());
        }
        sb.append(request.getPayAmount());
        String data = sb.toString();

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
        java.security.interfaces.RSAPrivateKey privateKey = 
            (java.security.interfaces.RSAPrivateKey) kf.generatePrivate(spec);

        // Payloro uses RSA Cipher private-key encryption (NOT Signature),
        // with URL-safe Base64 encoding and split-codec for long data
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("RSA");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, privateKey);
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = rsaSplitCodec(cipher, dataBytes, privateKey.getModulus().bitLength());

        return Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
    }

    private byte[] rsaSplitCodec(javax.crypto.Cipher cipher, byte[] data, int keySize) throws Exception {
        int maxBlock = keySize / 8 - 11;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int offSet = 0;
        int i = 0;
        while (data.length > offSet) {
            byte[] buff;
            if (data.length - offSet > maxBlock) {
                buff = cipher.doFinal(data, offSet, maxBlock);
            } else {
                buff = cipher.doFinal(data, offSet, data.length - offSet);
            }
            out.write(buff, 0, buff.length);
            i++;
            offSet = i * maxBlock;
        }
        byte[] result = out.toByteArray();
        out.close();
        return result;
    }

    @Data
    @Builder
    public static class PayloroRequest {
        private String merchantNo;
        private String merchantOrderNo;
        private String payAmount; 
        private String description;
        private String method;
        private String name;
        private String mobile;
        private String email;
        private String notifyUrl;
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
