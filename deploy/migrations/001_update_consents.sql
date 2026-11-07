SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO consents (consent_type, content) VALUES
    ('TERMS_OF_SERVICE', '[필수] 이용약관에 동의해요'),
    ('PRIVACY_POLICY', '[필수] 개인정보 처리방침에 동의해요'),
    ('LEGAL_GUARDIAN', '[필수] 만 14세 미만 아동의 법정대리인이에요'),
    ('VOICE_PROCESSING', '[필수] 음성 수집·이용에 동의해요')
ON DUPLICATE KEY UPDATE content = VALUES(content);

COMMIT;
