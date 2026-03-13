package com.orasa.backend.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import com.orasa.backend.config.TimeConfig;
import com.orasa.backend.domain.AppointmentEntity;
import com.orasa.backend.domain.ServiceEntity;
import com.orasa.backend.domain.SmsLogEntity;
import com.orasa.backend.dto.export.ExportProgressMessage;
import com.orasa.backend.repository.AppointmentRepository;
import com.orasa.backend.repository.AppointmentSpecification;
import com.orasa.backend.repository.SmsLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final AppointmentRepository appointmentRepository;
    private final SmsLogRepository smsLogRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    @Async
    @Transactional(readOnly = true)
    public void exportAppointments(UUID businessId, int month, int year) {
        String topic = "/topic/export/" + businessId;

        try {
            sendProgress(topic, ExportProgressMessage.preparing(10, "Preparing data..."));

            YearMonth ym = YearMonth.of(year, month);
            LocalDate firstDay = ym.atDay(1);
            LocalDate lastDay = ym.atEndOfMonth();

            OffsetDateTime start = firstDay.atStartOfDay(TimeConfig.PH_ZONE).toOffsetDateTime();
            OffsetDateTime end = lastDay.plusDays(1).atStartOfDay(TimeConfig.PH_ZONE).toOffsetDateTime();

            sendProgress(topic, ExportProgressMessage.preparing(20, "Fetching appointments..."));

            List<AppointmentEntity> appointments = appointmentRepository.findAll(
                    AppointmentSpecification.buildSearchSpec(null, null, businessId, null, null, null, start, end),
                    Pageable.unpaged()
            ).getContent();

            if (!appointments.isEmpty()) {
                List<UUID> ids = appointments.stream()
                        .map(AppointmentEntity::getId)
                        .toList();
                appointmentRepository.findAllByIdWithAssociations(ids);
            }

            sendProgress(topic, ExportProgressMessage.preparing(40, "Fetching SMS logs..."));

            List<SmsLogEntity> smsLogs = smsLogRepository.searchSmsLogs(
                    businessId, null, null, start, end, Pageable.unpaged()
            ).getContent();

            sendProgress(topic, ExportProgressMessage.generating(60, "Generating CSV..."));

            String csv = buildCsv(appointments, smsLogs);

            sendProgress(topic, ExportProgressMessage.generating(90, "Finalizing..."));

            String encoded = Base64.getEncoder().encodeToString(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            sendProgress(topic, ExportProgressMessage.complete(encoded));

            log.info("[EXPORT] Export complete for business {}: {} appointments, {} SMS logs",
                    businessId, appointments.size(), smsLogs.size());

        } catch (Exception e) {
            log.error("[EXPORT] Export failed for business {}: {}", businessId, e.getMessage(), e);
            sendProgress(topic, ExportProgressMessage.error("Export failed: " + e.getMessage()));
        }
    }

    private String buildCsv(List<AppointmentEntity> appointments, List<SmsLogEntity> smsLogs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Date,Time,Branch,Customer Name,Customer Phone,Type,Status,Services,Notes,SMS Count,SMS Statuses\n");

        for (AppointmentEntity apt : appointments) {
            OffsetDateTime startDt = apt.getStartDateTime().atZoneSameInstant(TimeConfig.PH_ZONE).toOffsetDateTime();

            List<SmsLogEntity> aptSmsLogs = smsLogs.stream()
                    .filter(sms -> sms.getAppointment() != null && sms.getAppointment().getId().equals(apt.getId()))
                    .toList();

            String smsStatuses = aptSmsLogs.stream()
                    .map(sms -> sms.getStatus().name())
                    .collect(Collectors.joining("; "));

            String services = apt.getServices().stream()
                    .map(ServiceEntity::getName)
                    .collect(Collectors.joining(", "));

            sb.append(csvEscape(startDt.format(DATE_FMT))).append(",");
            sb.append(csvEscape(startDt.format(TIME_FMT))).append(",");
            sb.append(csvEscape(apt.getBranch().getName())).append(",");
            sb.append(csvEscape(apt.getCustomerName())).append(",");
            sb.append(csvEscape(apt.getCustomerPhone())).append(",");
            sb.append(apt.getType().name()).append(",");
            sb.append(apt.getStatus().name()).append(",");
            sb.append(csvEscape(services)).append(",");
            sb.append(csvEscape(apt.getNotes() != null ? apt.getNotes() : "")).append(",");
            sb.append(aptSmsLogs.size()).append(",");
            sb.append(csvEscape(smsStatuses));
            sb.append("\n");
        }

        return sb.toString();
    }

    private String csvEscape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void sendProgress(String topic, ExportProgressMessage message) {
        messagingTemplate.convertAndSend(topic, message);
    }
}
