package com.orasa.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.domain.BusinessReminderConfigEntity;
import com.orasa.backend.dto.sms.CreateReminderConfigRequest;
import com.orasa.backend.dto.sms.ReminderConfigResponse;
import com.orasa.backend.dto.sms.UpdateReminderConfigRequest;
import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.BusinessReminderConfigRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ReminderConfigService {

    private final BusinessReminderConfigRepository reminderConfigRepository;

    @Transactional
    public ReminderConfigResponse createConfig(UUID businessId, CreateReminderConfigRequest request) {
        log.info("SMS request: {}", request);
        List<BusinessReminderConfigEntity> existingConfigs = reminderConfigRepository.findByBusinessId(businessId);
        boolean duplicateLeadTime = existingConfigs.stream()
                .anyMatch(c -> c.getLeadTimeMinutes() != null && c.getLeadTimeMinutes().equals(request.getLeadTimeMinutes()));

        if (duplicateLeadTime) {
            throw new BusinessException("Reminder with " + request.getLeadTimeMinutes() + " minutes lead time already exists");
        }

        BusinessReminderConfigEntity config = BusinessReminderConfigEntity.builder()
                .businessId(businessId)
                .leadTimeMinutes(request.getLeadTimeMinutes())
                .messageTemplate(request.getMessageTemplate())
                .isEnabled(request.getEnabled() == null ? false : request.getEnabled())
                .build();

        BusinessReminderConfigEntity saved = reminderConfigRepository.save(config);
        return mapToResponse(saved);
    }

    @Transactional
    public ReminderConfigResponse updateConfig(UUID configId, UUID businessId, UpdateReminderConfigRequest request) {
        BusinessReminderConfigEntity config = getConfigById(configId, businessId);

        if (request.getLeadTimeMinutes() != null) {
            List<BusinessReminderConfigEntity> existingConfigs = reminderConfigRepository.findByBusinessId(businessId);
            boolean duplicateLeadTime = existingConfigs.stream()
                    .filter(c -> !c.getId().equals(configId))
                    .anyMatch(c -> c.getLeadTimeMinutes() != null && c.getLeadTimeMinutes().equals(request.getLeadTimeMinutes()));

            if (duplicateLeadTime) {
                throw new BusinessException("Reminder with " + request.getLeadTimeMinutes() + " minutes lead time already exists");
            }
            config.setLeadTimeMinutes(request.getLeadTimeMinutes());
        }

        if (request.getMessageTemplate() != null) {
            config.setMessageTemplate(request.getMessageTemplate());
        }

        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }

        BusinessReminderConfigEntity saved = reminderConfigRepository.save(config);
        return mapToResponse(saved);
    }

    public List<ReminderConfigResponse> getConfigsByBusiness(UUID businessId) {
        return reminderConfigRepository.findByBusinessId(businessId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<BusinessReminderConfigEntity> getEnabledConfigs(UUID businessId) {
        return reminderConfigRepository.findByBusinessId(businessId).stream()
                .filter(BusinessReminderConfigEntity::isEnabled)
                .toList();
    }

    @Transactional
    public void deleteConfig(UUID configId, UUID businessId) {
        BusinessReminderConfigEntity config = getConfigById(configId, businessId);
        reminderConfigRepository.delete(config);
    }

    private BusinessReminderConfigEntity getConfigById(UUID configId, UUID businessId) {
        BusinessReminderConfigEntity config = reminderConfigRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder config not found"));

        if (!config.getBusinessId().equals(businessId)) {
            throw new BusinessException("Reminder config does not belong to your business");
        }

        return config;
    }

    private ReminderConfigResponse mapToResponse(BusinessReminderConfigEntity config) {
        return ReminderConfigResponse.builder()
                .id(config.getId())
                .businessId(config.getBusinessId())
                .leadTimeMinutes(config.getLeadTimeMinutes())
                .messageTemplate(config.getMessageTemplate())
                .enabled(config.isEnabled())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
