package com.moyamoya.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenHasherTest {

    private final TokenHasher tokenHasher = new TokenHasher();

    @Test
    void matchesTokenWithStoredHash() {
        String hash = tokenHasher.hash("refresh-token");

        assertThat(tokenHasher.matches("refresh-token", hash)).isTrue();
        assertThat(tokenHasher.matches("different-token", hash)).isFalse();
        assertThat(tokenHasher.matches("refresh-token", null)).isFalse();
    }
}
