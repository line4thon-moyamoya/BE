SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS consents (
    consent_id BIGINT NOT NULL AUTO_INCREMENT,
    consent_type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    PRIMARY KEY (consent_id),
    UNIQUE KEY uk_consents_consent_type (consent_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO consents (consent_id, consent_type, content) VALUES
    (1, 'TERMS_OF_SERVICE', '[필수] 이용약관에 동의해요'),
    (2, 'PRIVACY_POLICY', '[필수] 개인정보 처리방침에 동의해요'),
    (3, 'LEGAL_GUARDIAN', '[필수] 만 14세 미만 아동의 법정대리인이에요'),
    (4, 'VOICE_PROCESSING', '[필수] 음성 수집·이용에 동의해요')
ON DUPLICATE KEY UPDATE content = VALUES(content);
