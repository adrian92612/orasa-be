package com.orasa.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "branch_services",
        uniqueConstraints = @UniqueConstraint(columnNames = {"branch_id", "service_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE branch_services SET is_deleted = true, deleted_at = NOW() WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class BranchService extends BaseEntity{

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceOffering service;

    @Column(name = "custom_price")
    private BigDecimal customPrice;

    @Builder.Default
    @Column(name = "is_active")
    private boolean isActive = true;

    public BigDecimal getEffectivePrice() {
        return (customPrice != null) ? customPrice : service.getBasePrice();
    }
}

