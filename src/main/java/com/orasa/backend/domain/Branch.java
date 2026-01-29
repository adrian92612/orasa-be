package com.orasa.backend.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "branches")
@Getter
@Setter
public class Branch extends BaseEntity {


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @NotBlank
    @Column(nullable = false)
    private String name;

    private String address;

    @Column(name = "phone_number")
    private String phoneNumber;
}
