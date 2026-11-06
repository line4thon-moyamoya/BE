CREATE TABLE IF NOT EXISTS consents (
    consent_id BIGINT NOT NULL AUTO_INCREMENT,
    consent_type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    PRIMARY KEY (consent_id),
    UNIQUE KEY uk_consents_consent_type (consent_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO consents (consent_id, consent_type, content) VALUES
    (1, 'TERMS_OF_SERVICE', '서비스 이용약관 내용'),
    (2, 'PRIVACY_POLICY', '개인정보 처리방침 내용'),
    (3, 'LEGAL_GUARDIAN', '법정대리인 확인 내용'),
    (4, 'VOICE_PROCESSING', '음성 수집 및 이용 동의 내용'),
    (5, 'VOICE_IMPROVEMENT', '서비스 개선을 위한 음성 활용 동의 내용')
ON DUPLICATE KEY UPDATE
    content = VALUES(content);
