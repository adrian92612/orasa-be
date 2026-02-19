package com.orasa.backend.service.payment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.config.OrasaProperties;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.PaymentEntity;
import com.orasa.backend.domain.PaymentEntity.PaymentStatus;
import com.orasa.backend.domain.UserEntity;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.PaymentRepository;
import com.orasa.backend.repository.UserRepository;
import com.orasa.backend.service.SubscriptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PayloroService payloroService;
    private final PaymentRepository paymentRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final OrasaProperties orasaProperties;
    private final Clock clock;

    @Transactional
    public PayloroService.PayloroResponse createSubscriptionPayment(UUID businessId, int months, String method) {
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        
        UserEntity owner = userRepository.findByBusinessId(businessId)
                .stream()
                .filter(u -> u.getRole().name().equals("OWNER"))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));

        String orderNo = "SUB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BigDecimal amount = new BigDecimal("299.00").multiply(new BigDecimal(months));
        String description = String.format("Orasa Subscription Renewal - %d Month%s", months, months > 1 ? "s" : "");

        return initiatePayloroPayment(business, owner, orderNo, amount, description, method, PaymentEntity.PaymentType.SUBSCRIPTION_RENEWAL);
    }

    @Transactional
    public PayloroService.PayloroResponse createCreditsPayment(UUID businessId, int credits, String method) {
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        
        UserEntity owner = userRepository.findByBusinessId(businessId)
                .stream()
                .filter(u -> u.getRole().name().equals("OWNER"))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));

        String orderNo = "CRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        // pricing: PHP 1.00 per credit for now
        BigDecimal amount = new BigDecimal(credits).setScale(2); 
        String description = "Orasa SMS Credits - " + credits + " units";

        return initiatePayloroPayment(business, owner, orderNo, amount, description, method, PaymentEntity.PaymentType.CREDIT_TOPUP);
    }

    @Transactional
    public void handleWebhookCallback(String merchantOrderNo, String platOrderNo, String orderStatus) {
        log.info("[PAYMENT] Webhook callback: merchantOrderNo={}, platOrderNo={}, orderStatus={}", 
                merchantOrderNo, platOrderNo, orderStatus);

        Optional<PaymentEntity> paymentOpt = paymentRepository.findByMerchantOrderNo(merchantOrderNo);
        if (paymentOpt.isEmpty() && platOrderNo != null) {
            paymentOpt = paymentRepository.findByPlatOrderNo(platOrderNo);
        }

        if (paymentOpt.isEmpty()) {
            log.warn("[PAYMENT] No payment found for merchantOrderNo={}, platOrderNo={}", merchantOrderNo, platOrderNo);
            return;
        }

        PaymentEntity payment = paymentOpt.get();

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            log.info("[PAYMENT] Payment {} already processed as SUCCESS, skipping", merchantOrderNo);
            return;
        }

        PaymentStatus newStatus = mapOrderStatus(orderStatus);
        payment.setStatus(newStatus);
        payment.setUpdatedAt(OffsetDateTime.now(clock));
        paymentRepository.save(payment);

        log.info("[PAYMENT] Updated payment {} status to {}", merchantOrderNo, newStatus);

        if (newStatus == PaymentStatus.SUCCESS) {
            fulfillPayment(payment);
        }

        // Push status update to connected frontend via WebSocket
        PaymentStatusMessage statusMessage = new PaymentStatusMessage(
                payment.getMerchantOrderNo(),
                newStatus.name(),
                payment.getType().name()
        );
        messagingTemplate.convertAndSend(
                "/topic/payments/" + payment.getBusinessId(),
                statusMessage
        );
        log.info("[PAYMENT] WebSocket notification sent to /topic/payments/{}", payment.getBusinessId());
    }

    private void fulfillPayment(PaymentEntity payment) {
        try {
            if (payment.getType() == PaymentEntity.PaymentType.SUBSCRIPTION_RENEWAL) {
                int months = payment.getAmount().divideToIntegralValue(new BigDecimal("299")).intValue();
                if (months < 1) months = 1;
                subscriptionService.extendSubscription(payment.getBusinessId(), months);
                log.info("[PAYMENT] Subscription extended by {} months for business {}", months, payment.getBusinessId());
            } else if (payment.getType() == PaymentEntity.PaymentType.CREDIT_TOPUP) {
                int credits = payment.getAmount().intValue();
                subscriptionService.addPaidCredits(payment.getBusinessId(), credits);
                log.info("[PAYMENT] Added {} SMS credits for business {}", credits, payment.getBusinessId());
            }
        } catch (Exception e) {
            log.error("[PAYMENT] Error fulfilling payment {}: {}", payment.getMerchantOrderNo(), e.getMessage(), e);
        }
    }

    private PaymentStatus mapOrderStatus(String orderStatus) {
        if (orderStatus == null) return PaymentStatus.PENDING;
        return switch (orderStatus.toUpperCase()) {
            case "SUCCESS" -> PaymentStatus.SUCCESS;
            case "FAILED" -> PaymentStatus.FAILED;
            case "EXPIRED" -> PaymentStatus.EXPIRED;
            default -> PaymentStatus.PENDING;
        };
    }

    private PayloroService.PayloroResponse initiatePayloroPayment(
            BusinessEntity business, 
            UserEntity owner, 
            String orderNo, 
            BigDecimal amount, 
            String description, 
            String method,
            PaymentEntity.PaymentType type) {

        PayloroService.PayloroRequest.PayloroRequestBuilder requestBuilder = PayloroService.PayloroRequest.builder()
                .merchantOrderNo(orderNo)
                .payAmount(amount.toString())
                .description(description)
                .method(method)
                .name(owner.getUsername()) 
                .mobile("09564497655")
                .email(owner.getUsername());

        String notifyUrl = orasaProperties.getPayloro().getNotifyUrl();
        if (notifyUrl != null && !notifyUrl.isBlank()) {
            requestBuilder.notifyUrl(notifyUrl);
        }

        PayloroService.PayloroRequest payloroRequest = requestBuilder.build();

        PayloroService.PayloroResponse payloroResponse = payloroService.createPayment(payloroRequest);

        PaymentEntity payment = PaymentEntity.builder()
                .businessId(business.getId())
                .merchantOrderNo(orderNo)
                .platOrderNo(payloroResponse.platOrderNo())
                .amount(amount)
                .description(description)
                .method(method)
                .type(type)
                .status(payloroResponse.success() ? PaymentStatus.PENDING : PaymentStatus.FAILED)
                .paymentLink(payloroResponse.paymentLink())
                .paymentImage(payloroResponse.paymentImage())
                .errorMessage(payloroResponse.errorMessage())
                .createdAt(OffsetDateTime.now(clock))
                .updatedAt(OffsetDateTime.now(clock))
                .build();

        paymentRepository.save(payment);

        return payloroResponse;
    }

    public record PaymentStatusMessage(
        String merchantOrderNo,
        String status,
        String type
    ) {}
}
