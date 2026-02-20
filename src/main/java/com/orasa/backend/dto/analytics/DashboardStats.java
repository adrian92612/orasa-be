package com.orasa.backend.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStats {
    private long totalAppointments;
    private long scheduledCount;
    private long walkInCount;
    private long cancelledCount;
    private long noShowCount;
    private long smsDelivered;
    private long smsFailed;
    private List<DailyStatsDTO> dailyStats;
    private List<ServiceStatsDTO> serviceStats;
    private List<StatusStatsDTO> statusStats;
}
