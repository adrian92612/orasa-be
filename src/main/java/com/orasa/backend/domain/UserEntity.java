package com.orasa.backend.domain;

import com.orasa.backend.common.UserRole;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE users SET is_deleted = true, deleted_at = NOW() WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class UserEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = true)
    private BusinessEntity business;

    @ManyToMany
    @JoinTable(
        name = "user_branches",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns= @JoinColumn(name = "branch_id")
    )
    @Builder.Default
    private Set<BranchEntity> branches = new HashSet<>();

    @NotBlank
    @Column(unique = true, nullable = false, columnDefinition = "citext")
    private String username;

    @Column(unique = true, columnDefinition = "citext")
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;



    /**
     * Returns a user-friendly display name for activity logs
     */
    public String getDisplayName() {
        if (this.email != null && !this.email.isBlank()) {
            return this.email;
        }
        return this.username;
    }
}
