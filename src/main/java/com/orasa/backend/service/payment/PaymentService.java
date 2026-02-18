package com.orasa.backend.service.payment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.PaymentEntity;
import com.orasa.backend.domain.PaymentEntity.PaymentStatus;
import com.orasa.backend.domain.UserEntity;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.PaymentRepository;
import com.orasa.backend.repository.UserRepository;

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

    private PayloroService.PayloroResponse initiatePayloroPayment(
            BusinessEntity business, 
            UserEntity owner, 
            String orderNo, 
            BigDecimal amount, 
            String description, 
            String method,
            PaymentEntity.PaymentType type) {

        PayloroService.PayloroRequest payloroRequest = PayloroService.PayloroRequest.builder()
                .merchantOrderNo(orderNo)
                .payAmount(amount.toString())
                .description(description)
                .method(method)
                .name(owner.getUsername()) 
                .mobile("09564497655") // Default placeholder as mobile is usually optional in Payloro for QR phase
                .email(owner.getUsername()) 
                .build();

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
}
