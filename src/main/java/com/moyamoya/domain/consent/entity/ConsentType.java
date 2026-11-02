package com.moyamoya.domain.consent.entity;

public enum ConsentType {
    TERMS_OF_SERVICE(true),
    PRIVACY_POLICY(true),
    LEGAL_GUARDIAN(true),
    VOICE_PROCESSING(true),
    VOICE_IMPROVEMENT(false);

    private final boolean required;

    ConsentType(boolean required) {
        this.required = required;
    }

    public boolean isRequired() {
        return required;
    }
}
