package com.orasa.backend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orasa.backend.service.payment.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Slf4j
public class PayloroWebhookController {

    private final PaymentService paymentService;

    @PostMapping("/payloro")
    public ResponseEntity<String> handlePayloroCallback(@RequestBody Map<String, Object> payload) {
        log.info("[PAYLORO WEBHOOK] Raw payload: {}", payload);

        try {
            String merchantOrderNo = (String) payload.get("merchantOrderNo");
            String platOrderNo = (String) payload.get("platOrderNo");
            String orderStatus = (String) payload.get("orderStatus");

            if (merchantOrderNo == null || orderStatus == null) {
                log.warn("[PAYLORO WEBHOOK] Missing required fields: merchantOrderNo={}, orderStatus={}", merchantOrderNo, orderStatus);
                return ResponseEntity.ok("success");
            }

            paymentService.handleWebhookCallback(merchantOrderNo, platOrderNo, orderStatus);
        } catch (Exception e) {
            log.error("[PAYLORO WEBHOOK] Error processing callback", e);
        }

        // Always return "success" so Payloro doesn't retry
        return ResponseEntity.ok("success");
    }
}
