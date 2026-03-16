package com.orasa.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.orasa.backend.domain.AppointmentEntity;
import com.orasa.backend.domain.ServiceEntity;
import com.orasa.backend.repository.ServiceRepository;

public class AppointmentMapperTest {

    @Mock
    private ServiceRepository serviceRepository;

    @InjectMocks
    private AppointmentMapper appointmentMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Should return empty list when appointment has no services")
    void resolveServices_noServices_returnsEmptyList() {
        AppointmentEntity appointment = AppointmentEntity.builder().services(null).build();
        
        List<ServiceEntity> result = appointmentMapper.resolveServices(appointment);
        
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return resolved services from repository")
    void resolveServices_withServices_returnsFilteredServices() {
        UUID serviceId1 = UUID.randomUUID();
        UUID serviceId2 = UUID.randomUUID();
        
        ServiceEntity s1 = ServiceEntity.builder().name("Service 1").build();
        s1.setId(serviceId1);
        ServiceEntity s2 = ServiceEntity.builder().name("Service 2").build();
        s2.setId(serviceId2);
        
        AppointmentEntity appointment = AppointmentEntity.builder()
                .services(Set.of(s1, s2))
                .build();
        
        when(serviceRepository.findAllById(anyList()))
            .thenReturn(List.of(s1, s2));
        
        List<ServiceEntity> result = appointmentMapper.resolveServices(appointment);
        
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder(s1, s2);
    }
}
