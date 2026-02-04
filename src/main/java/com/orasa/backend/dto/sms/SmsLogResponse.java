package com.orasa.backend.dto.sms;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.orasa.backend.common.SmsStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsLogResponse {
    private UUID id;
    private UUID businessId;
    private UUID appointmentId;
    private String customerName;
    private String recipientPhone;
    private String messageBody;
    private SmsStatus status;
    private String errorMessage;
    private OffsetDateTime createdAt;
}
