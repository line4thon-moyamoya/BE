package com.moyamoya.domain.consent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "consents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consent_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, unique = true, length = 50)
    private ConsentType consentType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
}
