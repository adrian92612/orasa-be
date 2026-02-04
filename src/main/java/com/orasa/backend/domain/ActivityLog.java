package com.orasa.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE activity_logs SET is_deleted = true, deleted_at = NOW() WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class ActivityLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(nullable = false)
    private String action;

    /**
     * Short, human-readable description shown in list view
     * Example: "Updated appointment for John Doe"
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Detailed JSON or text with before/after changes, shown when expanded
     * Example: {"customerName": {"before": "John", "after": "Jane"}, ...}
     */
    @Column(columnDefinition = "TEXT")
    private String details;
}

