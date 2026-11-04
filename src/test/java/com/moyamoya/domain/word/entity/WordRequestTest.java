package com.moyamoya.domain.word.entity;

import com.moyamoya.domain.word.exception.WordErrorCode;
import com.moyamoya.global.exception.CustomException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WordRequestTest {

    @Test
    void resolveRejectsAlreadyResolvedRequest() {
        WordRequest request = WordRequest.create(null, "사과", null);
        request.resolve("사과");

        assertThatThrownBy(() -> request.resolve("배"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(WordErrorCode.WORD_REQUEST409);
    }

    @Test
    void resolveRejectsBlankCorrectedWord() {
        WordRequest request = WordRequest.create(null, "사과", null);

        assertThatThrownBy(() -> request.resolve("   "))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(WordErrorCode.WORD_REQUEST400);
        assertThat(request.getStatus()).isEqualTo(WordRequestStatus.PENDING);
        assertThat(request.getResolvedAt()).isNull();
    }

    @Test
    void resolveRejectsCorrectedWordLongerThanColumnLimit() {
        WordRequest request = WordRequest.create(null, "사과", null);
        String tooLongWord = "a".repeat(101);

        assertThatThrownBy(() -> request.resolve(tooLongWord))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(WordErrorCode.WORD_REQUEST400);
        assertThat(request.getStatus()).isEqualTo(WordRequestStatus.PENDING);
        assertThat(request.getResolvedAt()).isNull();
    }

    @Test
    void resolveTrimsCorrectedWordBeforeResolving() {
        WordRequest request = WordRequest.create(null, "사과", null);

        request.resolve("  바나나  ");

        assertThat(request.getCorrectedWord()).isEqualTo("바나나");
        assertThat(request.getStatus()).isEqualTo(WordRequestStatus.RESOLVED);
        assertThat(request.getResolvedAt()).isNotNull();
    }
}
