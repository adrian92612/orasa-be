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
        log.info("[PAYLORO] Initiating payment request for order: {} (Amount: {})", request.getMerchantOrderNo(), request.getPayAmount());
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

            log.info("[PAYLORO] Raw Response Body: {}", response.getBody());

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
                log.error("[PAYLORO] Error Status: {}, Message: {}", root.path("status").asText(), error);
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
        byte[] encrypted = rsaSplitCodec(cipher, javax.crypto.Cipher.ENCRYPT_MODE, dataBytes, privateKey.getModulus().bitLength());

        return Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
    }

    public boolean verifyWebhookSignature(java.util.Map<String, Object> payload) {
        if (!payload.containsKey("sign") || payload.get("sign") == null) {
            log.warn("[PAYLORO WEBHOOK] Payload missing signature");
            return false;
        }

        String sign = (String) payload.get("sign");
        java.util.Map<String, Object> verifyMap = new java.util.HashMap<>(payload);
        verifyMap.remove("sign");

        String sortValue = getSortValue(verifyMap);

        try {
            String decryptedStr = decrypt(sign, orasaProperties.getPayloro().getPlatPublicKey());
            if (sortValue.equals(decryptedStr)) {
                return true;
            } else {
                log.warn("[PAYLORO WEBHOOK] Signature mismatch. Expected: {}, Actual: {}", sortValue, decryptedStr);
                return false;
            }
        } catch (Exception e) {
            log.error("[PAYLORO WEBHOOK] Failed to verify webhook signature", e);
            return false;
        }
    }

    private String getSortValue(java.util.Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Object[] keys = map.keySet().toArray();
        java.util.Arrays.sort(keys);
        StringBuilder res = new StringBuilder();
        for (Object key : keys) {
            Object value = map.get(key);
            if (value != null) {
                res.append(value.toString());
            }
        }
        return res.toString();
    }

    private String decrypt(String base64EncryptedData, String publicKeyStr) throws Exception {
        if (publicKeyStr == null || publicKeyStr.isEmpty()) {
            throw new IllegalArgumentException("Payloro public key is not configured");
        }

        String realPK = publicKeyStr
            .replaceAll("-----BEGIN PUBLIC KEY-----", "")
            .replaceAll("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(realPK);
        java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        java.security.interfaces.RSAPublicKey publicKey = 
            (java.security.interfaces.RSAPublicKey) kf.generatePublic(spec);

        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("RSA");
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, publicKey);

        byte[] encryptedBytes;
        try {
            encryptedBytes = Base64.getUrlDecoder().decode(base64EncryptedData);
        } catch (IllegalArgumentException e) {
            encryptedBytes = Base64.getDecoder().decode(base64EncryptedData);
        }

        byte[] decryptedBytes = rsaSplitCodec(cipher, javax.crypto.Cipher.DECRYPT_MODE, encryptedBytes, publicKey.getModulus().bitLength());
        
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    private byte[] rsaSplitCodec(javax.crypto.Cipher cipher, int opmode, byte[] data, int keySize) throws Exception {
        int maxBlock = 0;
        if (opmode == javax.crypto.Cipher.DECRYPT_MODE) {
            maxBlock = keySize / 8;
        } else {
            maxBlock = keySize / 8 - 11;
        }
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
