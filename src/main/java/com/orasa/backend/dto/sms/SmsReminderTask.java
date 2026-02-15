package com.orasa.backend.dto.sms;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsReminderTask implements Serializable {
    private UUID appointmentId;
    private UUID businessId;
    private UUID scheduledTaskId;
    private Integer leadTimeMinutes;
    private OffsetDateTime scheduledAt;
}
