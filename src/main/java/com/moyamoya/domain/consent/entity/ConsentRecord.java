package com.moyamoya.domain.consent.entity;

import com.moyamoya.domain.user.entity.User;
import com.moyamoya.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "consent_records")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consent_record_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consent_id", nullable = false)
    private Consent consent;

    @Column(name = "is_agreed", nullable = false)
    private boolean agreed;

    private ConsentRecord(User user, Consent consent, boolean agreed) {
        this.user = user;
        this.consent = consent;
        this.agreed = agreed;
    }

    public static ConsentRecord create(User user, Consent consent, boolean agreed) {
        return new ConsentRecord(user, consent, agreed);
    }
}
