package com.moyamoya.domain.child.entity;

import com.moyamoya.domain.user.entity.User;
import com.moyamoya.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "children")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Child extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "child_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", nullable = false, length = 20)
    private AgeGroup ageGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "moya_type", nullable = false, length = 20)
    private MoyaType moyaType;

    private Child(User user, String nickname, AgeGroup ageGroup) {
        this.user = user;
        this.nickname = nickname;
        this.ageGroup = ageGroup;
        this.moyaType = MoyaType.DEFAULT;
    }

    public static Child create(User user, String nickname, AgeGroup ageGroup) {
        return new Child(user, nickname, ageGroup);
    }

    public static Child create(User user, String nickname, AgeGroup ageGroup, MoyaType moyaType) {
        Child child = new Child(user, nickname, ageGroup);
        child.moyaType = moyaType;
        return child;
    }

    public void updateProfile(String nickname, AgeGroup ageGroup, MoyaType moyaType) {
        this.nickname = nickname;
        this.ageGroup = ageGroup;
        this.moyaType = moyaType;
    }

    public void updateMoyaType(MoyaType moyaType) {
        this.moyaType = moyaType;
    }
}
