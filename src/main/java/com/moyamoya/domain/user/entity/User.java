package com.moyamoya.domain.user.entity;

import com.moyamoya.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(name = "parent_pin", nullable = false, length = 255)
    private String parentPin;

    @Column(name = "refresh_token", length = 255)
    private String refreshToken;

    @Column(name = "last_selected_child_id")
    private Long lastSelectedChildId;

    @Column(name = "weekly_report_enabled")
    private Boolean weeklyReportEnabled;

    private User(String email, String password, String parentPin) {
        this.email = email;
        this.password = password;
        this.parentPin = parentPin;
        this.weeklyReportEnabled = false;
    }

    public static User create(String email, String encodedPassword, String encodedParentPin) {
        return new User(email, encodedPassword, encodedParentPin);
    }

    public void updateRefreshToken(String refreshTokenHash) {
        this.refreshToken = refreshTokenHash;
    }

    public void updateLastSelectedChildId(Long childId) {
        this.lastSelectedChildId = childId;
    }
}
