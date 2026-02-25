package com.orasa.backend.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.orasa.backend.common.AppointmentStatus;
import com.orasa.backend.common.AppointmentType;
import com.orasa.backend.domain.AppointmentEntity;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AppointmentSpecification {

    public static Specification<AppointmentEntity> withBranchId(UUID branchId) {
        return (root, query, cb) -> cb.equal(root.get("branch").get("id"), branchId);
    }

    public static Specification<AppointmentEntity> withBusinessId(UUID businessId) {
        return (root, query, cb) -> cb.equal(root.get("business").get("id"), businessId);
    }

    public static Specification<AppointmentEntity> withSearch(String search) {
        return (root, query, cb) -> {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("customerName")), pattern),
                    cb.like(cb.lower(root.get("customerPhone")), pattern),
                    cb.like(cb.lower(root.get("notes")), pattern)
            );
        };
    }

    public static Specification<AppointmentEntity> withStatus(AppointmentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<AppointmentEntity> withType(AppointmentType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<AppointmentEntity> withDateRange(OffsetDateTime start, OffsetDateTime end) {
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("startDateTime"), start),
                cb.lessThanOrEqualTo(root.get("startDateTime"), end)
        );
    }

    public static Specification<AppointmentEntity> buildSearchSpec(
            UUID branchId,
            UUID businessId,
            String search,
            AppointmentStatus status,
            AppointmentType type,
            OffsetDateTime start,
            OffsetDateTime end) {

        Specification<AppointmentEntity> spec = Specification.where((root, query, cb) -> cb.conjunction());

        if (branchId != null) {
            spec = spec.and(withBranchId(branchId));
        }

        if (businessId != null) {
            spec = spec.and(withBusinessId(businessId));
        }

        if (search != null && !search.trim().isEmpty()) {
            spec = spec.and(withSearch(search));
        }

        if (status != null) {
            spec = spec.and(withStatus(status));
        }

        if (type != null) {
            spec = spec.and(withType(type));
        }

        spec = spec.and(withDateRange(start, end));

        return spec;
    }
}
