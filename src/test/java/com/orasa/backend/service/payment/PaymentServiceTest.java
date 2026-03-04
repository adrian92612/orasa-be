package com.orasa.backend.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.orasa.backend.common.UserRole;
import com.orasa.backend.config.OrasaProperties;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.PaymentEntity;
import com.orasa.backend.domain.UserEntity;
import com.orasa.backend.dto.payment.PaymentHistoryResponse;
import com.orasa.backend.dto.payment.PaymentStatus;
import com.orasa.backend.dto.payment.PaymentType;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.PaymentRepository;
import com.orasa.backend.repository.UserRepository;
import com.orasa.backend.service.SubscriptionService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PaymentServiceTest {

    @Mock
    private PayloroService payloroService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private OrasaProperties orasaProperties;

    @Mock
    private Clock clock;

    @InjectMocks
    private PaymentService paymentService;

    private final OffsetDateTime now = OffsetDateTime.of(2026, 3, 4, 10, 0, 0, 0, ZoneOffset.UTC);
    private UUID businessId;
    private BusinessEntity business;
    private UserEntity owner;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        business = BusinessEntity.builder()
                .name("Test Business")
                .build();
        business.setId(businessId);

        owner = new UserEntity();
        owner.setUsername("owner@test.com");
        owner.setRole(UserRole.OWNER);

        when(clock.instant()).thenReturn(now.toInstant());
        when(clock.getZone()).thenReturn(now.getOffset());
    }

    private PaymentEntity buildPayment(PaymentType type, PaymentStatus status, BigDecimal amount, String description) {
        return PaymentEntity.builder()
                .id(UUID.randomUUID())
                .businessId(businessId)
                .merchantOrderNo("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .platOrderNo("PLAT-123")
                .amount(amount)
                .description(description)
                .method("gcash")
                .type(type)
                .status(status)
                .createdAt(now.minusDays(1))
                .updatedAt(now.minusDays(1))
                .build();
    }

    // ─── getPaymentHistory ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getPaymentHistory")
    class GetPaymentHistoryTests {

        @Test
        @DisplayName("Should return only SUCCESS payments mapped to DTOs")
        void getPaymentHistory_returnsOnlySuccessPayments() {
            PaymentEntity successSub = buildPayment(
                    PaymentType.SUBSCRIPTION_RENEWAL, PaymentStatus.SUCCESS,
                    new BigDecimal("299.00"), "Subscription - 1 Month");
            PaymentEntity successCredit = buildPayment(
                    PaymentType.CREDIT_TOPUP, PaymentStatus.SUCCESS,
                    new BigDecimal("50.00"), "SMS Credits - 50 units");
            PaymentEntity pendingPayment = buildPayment(
                    PaymentType.SUBSCRIPTION_RENEWAL, PaymentStatus.PENDING,
                    new BigDecimal("299.00"), "Subscription - 1 Month");
            PaymentEntity failedPayment = buildPayment(
                    PaymentType.CREDIT_TOPUP, PaymentStatus.FAILED,
                    new BigDecimal("100.00"), "SMS Credits - 100 units");

            when(paymentRepository.findByBusinessIdOrderByCreatedAtDesc(businessId))
                    .thenReturn(List.of(successSub, successCredit, pendingPayment, failedPayment));

            List<PaymentHistoryResponse> history = paymentService.getPaymentHistory(businessId);

            assertThat(history).hasSize(2);
            assertThat(history).allMatch(p -> p.status() == PaymentStatus.SUCCESS);
            assertThat(history.get(0).description()).isEqualTo("Subscription - 1 Month");
            assertThat(history.get(1).description()).isEqualTo("SMS Credits - 50 units");
        }

        @Test
        @DisplayName("Should return empty list when no payments exist")
        void getPaymentHistory_noPayments_returnsEmptyList() {
            when(paymentRepository.findByBusinessIdOrderByCreatedAtDesc(businessId))
                    .thenReturn(List.of());

            List<PaymentHistoryResponse> history = paymentService.getPaymentHistory(businessId);

            assertThat(history).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list when all payments are non-SUCCESS")
        void getPaymentHistory_allNonSuccess_returnsEmptyList() {
            PaymentEntity pending = buildPayment(
                    PaymentType.SUBSCRIPTION_RENEWAL, PaymentStatus.PENDING,
                    new BigDecimal("299.00"), "Sub");
            PaymentEntity failed = buildPayment(
                    PaymentType.CREDIT_TOPUP, PaymentStatus.FAILED,
                    new BigDecimal("50.00"), "Credits");
            PaymentEntity expired = buildPayment(
                    PaymentType.SUBSCRIPTION_RENEWAL, PaymentStatus.EXPIRED,
                    new BigDecimal("299.00"), "Sub expired");

            when(paymentRepository.findByBusinessIdOrderByCreatedAtDesc(businessId))
                    .thenReturn(List.of(pending, failed, expired));

            List<PaymentHistoryResponse> history = paymentService.getPaymentHistory(businessId);

            assertThat(history).isEmpty();
        }

        @Test
        @DisplayName("Should correctly map all DTO fields")
        void getPaymentHistory_correctlyMapsDTOFields() {
            PaymentEntity payment = buildPayment(
                    PaymentType.CREDIT_TOPUP, PaymentStatus.SUCCESS,
                    new BigDecimal("100.00"), "SMS Credits - 100 units");

            when(paymentRepository.findByBusinessIdOrderByCreatedAtDesc(businessId))
                    .thenReturn(List.of(payment));

            List<PaymentHistoryResponse> history = paymentService.getPaymentHistory(businessId);

            assertThat(history).hasSize(1);
            PaymentHistoryResponse dto = history.get(0);
            assertThat(dto.id()).isEqualTo(payment.getId());
            assertThat(dto.merchantOrderNo()).isEqualTo(payment.getMerchantOrderNo());
            assertThat(dto.platOrderNo()).isEqualTo(payment.getPlatOrderNo());
            assertThat(dto.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(dto.description()).isEqualTo("SMS Credits - 100 units");
            assertThat(dto.method()).isEqualTo("gcash");
            assertThat(dto.type()).isEqualTo(PaymentType.CREDIT_TOPUP);
            assertThat(dto.status()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(dto.createdAt()).isEqualTo(payment.getCreatedAt());
        }
    }

    // ─── handleWebhookCallback ──────────────────────────────────────────────

    @Nested
    @DisplayName("handleWebhookCallback")
    class HandleWebhookCallbackTests {

        @Test
        @DisplayName("Should update status and fulfill subscription payment on SUCCESS")
        void handleWebhookCallback_successSubscription_fulfills() {
            PaymentEntity payment = buildPayment(
                    PaymentType.SUBSCRIPTION_RENEWAL, PaymentStatus.PENDING,
                    new BigDecimal("598.00"), "Subscription - 2 Months");

            when(paymentRepository.findByMerchantOrderNo(payment.getMerchantOrderNo()))
                    .thenReturn(Optional.of(payment));

            paymentService.handleWebhookCallback(payment.getMerchantOrderNo(), "PLAT-456", "SUCCESS");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            verify(paymentRepository).save(payment);
            verify(subscriptionService).extendSubscription(eq(businessId), eq(2));
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/payments/" + businessId),
                    any(PaymentService.PaymentStatusMessage.class));
        }

        @Test
        @DisplayName("Should update status and fulfill credit topup payment on SUCCESS")
        void handleWebhookCallback_successCreditTopup_fulfills() {
            PaymentEntity payment = buildPayment(
                    PaymentType.CREDIT_TOPUP, PaymentStatus.PENDING,
                    new BigDecimal("50.00"), "SMS Credits - 50 units");

            when(paymentRepository.findByMerchantOrderNo(payment.getMerchantOrderNo()))
                    .thenReturn(Optional.of(payment));

            paymentService.handleWebhookCallback(payment.getMerchantOrderNo(), null, "SUCCESS");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            verify(paymentRepository).save(payment);
            verify(subscriptionService).addPaidCredits(eq(businessId), eq(50));
        }

        @Test
        @DisplayName("Should skip already-processed SUCCESS payments")
        void handleWebhookCallback_alreadySuccess_skips() {
            PaymentEntity payment = buildPayment(
                    PaymentType.SUBSCRIPTION_RENEWAL, PaymentStatus.SUCCESS,
                    new BigDecimal("299.00"), "Already processed");

            when(paymentRepository.findByMerchantOrderNo(payment.getMerchantOrderNo()))
                    .thenReturn(Optional.of(payment));

            paymentService.handleWebhookCallback(payment.getMerchantOrderNo(), null, "SUCCESS");

            // Should NOT save again or fulfill
            verify(paymentRepository, never()).save(any());
            verify(subscriptionService, never()).extendSubscription(any(), any(Integer.class));
            verify(subscriptionService, never()).addPaidCredits(any(), any(Integer.class));
        }

        @Test
        @DisplayName("Should log warning and return for unknown merchant order number")
        void handleWebhookCallback_unknownOrder_doesNothing() {
            when(paymentRepository.findByMerchantOrderNo("UNKNOWN-ORDER"))
                    .thenReturn(Optional.empty());
            when(paymentRepository.findByPlatOrderNo("UNKNOWN-PLAT"))
                    .thenReturn(Optional.empty());

            paymentService.handleWebhookCallback("UNKNOWN-ORDER", "UNKNOWN-PLAT", "SUCCESS");

            verify(paymentRepository, never()).save(any());
            verify(subscriptionService, never()).extendSubscription(any(), any(Integer.class));
        }

        @Test
        @DisplayName("Should update status to FAILED without fulfilling")
        void handleWebhookCallback_failedStatus_updatesWithoutFulfilling() {
            PaymentEntity payment = buildPayment(
                    PaymentType.SUBSCRIPTION_RENEWAL, PaymentStatus.PENDING,
                    new BigDecimal("299.00"), "Subscription");

            when(paymentRepository.findByMerchantOrderNo(payment.getMerchantOrderNo()))
                    .thenReturn(Optional.of(payment));

            paymentService.handleWebhookCallback(payment.getMerchantOrderNo(), null, "FAILED");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(paymentRepository).save(payment);
            verify(subscriptionService, never()).extendSubscription(any(), any(Integer.class));
            verify(subscriptionService, never()).addPaidCredits(any(), any(Integer.class));
        }

        @Test
        @DisplayName("Should send WebSocket notification on status update")
        void handleWebhookCallback_sendsWebSocketNotification() {
            PaymentEntity payment = buildPayment(
                    PaymentType.CREDIT_TOPUP, PaymentStatus.PENDING,
                    new BigDecimal("100.00"), "Credits");

            when(paymentRepository.findByMerchantOrderNo(payment.getMerchantOrderNo()))
                    .thenReturn(Optional.of(payment));

            paymentService.handleWebhookCallback(payment.getMerchantOrderNo(), null, "EXPIRED");

            ArgumentCaptor<PaymentService.PaymentStatusMessage> messageCaptor =
                    ArgumentCaptor.forClass(PaymentService.PaymentStatusMessage.class);

            verify(messagingTemplate).convertAndSend(
                    eq("/topic/payments/" + businessId),
                    messageCaptor.capture());

            PaymentService.PaymentStatusMessage message = messageCaptor.getValue();
            assertThat(message.merchantOrderNo()).isEqualTo(payment.getMerchantOrderNo());
            assertThat(message.status()).isEqualTo("EXPIRED");
            assertThat(message.type()).isEqualTo("CREDIT_TOPUP");
        }
    }

    // ─── createSubscriptionPayment ──────────────────────────────────────────

    @Nested
    @DisplayName("createSubscriptionPayment")
    class CreateSubscriptionPaymentTests {

        @Test
        @DisplayName("Should create subscription payment with correct amount and description")
        void createSubscriptionPayment_correctParams() {
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
            when(userRepository.findByBusinessId(businessId)).thenReturn(List.of(owner));

            OrasaProperties.Payloro payloroProps = new OrasaProperties.Payloro();
            payloroProps.setNotifyUrl("https://test.com/webhook");
            when(orasaProperties.getPayloro()).thenReturn(payloroProps);

            PayloroService.PayloroResponse mockResponse = new PayloroService.PayloroResponse(
                    true, "https://pay.test/link", null, "PLAT-999", null);
            when(payloroService.createPayment(any())).thenReturn(mockResponse);

            PayloroService.PayloroResponse response = paymentService.createSubscriptionPayment(businessId, 3, "gcash");

            assertThat(response.success()).isTrue();

            ArgumentCaptor<PaymentEntity> paymentCaptor = ArgumentCaptor.forClass(PaymentEntity.class);
            verify(paymentRepository).save(paymentCaptor.capture());

            PaymentEntity savedPayment = paymentCaptor.getValue();
            assertThat(savedPayment.getAmount()).isEqualByComparingTo(new BigDecimal("897.00"));
            assertThat(savedPayment.getDescription()).contains("3 Months");
            assertThat(savedPayment.getType()).isEqualTo(PaymentType.SUBSCRIPTION_RENEWAL);
            assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(savedPayment.getMerchantOrderNo()).startsWith("SUB-");
        }
    }

    // ─── createCreditsPayment ───────────────────────────────────────────────

    @Nested
    @DisplayName("createCreditsPayment")
    class CreateCreditsPaymentTests {

        @Test
        @DisplayName("Should create credits payment with correct amount and description")
        void createCreditsPayment_correctParams() {
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
            when(userRepository.findByBusinessId(businessId)).thenReturn(List.of(owner));

            OrasaProperties.Payloro payloroProps = new OrasaProperties.Payloro();
            payloroProps.setNotifyUrl("https://test.com/webhook");
            when(orasaProperties.getPayloro()).thenReturn(payloroProps);

            PayloroService.PayloroResponse mockResponse = new PayloroService.PayloroResponse(
                    true, "https://pay.test/link", null, "PLAT-888", null);
            when(payloroService.createPayment(any())).thenReturn(mockResponse);

            PayloroService.PayloroResponse response = paymentService.createCreditsPayment(businessId, 200, "gcash");

            assertThat(response.success()).isTrue();

            ArgumentCaptor<PaymentEntity> paymentCaptor = ArgumentCaptor.forClass(PaymentEntity.class);
            verify(paymentRepository).save(paymentCaptor.capture());

            PaymentEntity savedPayment = paymentCaptor.getValue();
            assertThat(savedPayment.getAmount()).isEqualByComparingTo(new BigDecimal("200"));
            assertThat(savedPayment.getDescription()).contains("200 units");
            assertThat(savedPayment.getType()).isEqualTo(PaymentType.CREDIT_TOPUP);
            assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(savedPayment.getMerchantOrderNo()).startsWith("CRD-");
        }
    }
}
