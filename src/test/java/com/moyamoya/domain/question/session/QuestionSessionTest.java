package com.moyamoya.domain.question.session;

import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.word.entity.HeardFrom;
import com.moyamoya.domain.word.entity.WordSourceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionSessionTest {

    // 1차 후보가 없으면 FIRST_NO_CANDIDATES
    @Test
    void setFirstCandidatesWithEmptyListSetsFirstNoCandidates() {
        QuestionSession session = createSession();

        session.setFirstCandidates(List.of());

        assertThat(session.getStatus())
                .isEqualTo(QuestionSessionStatus.FIRST_NO_CANDIDATES);
        assertThat(session.getFirstCandidates()).isEmpty();
    }

    // 1차 후보가 있으면 FIRST_CANDIDATES_READY
    @Test
    void setFirstCandidatesWithCandidatesSetsReady() {
        QuestionSession session = createSession();

        session.setFirstCandidates(
                List.of(candidate("1", "사과"))
        );

        assertThat(session.getStatus())
                .isEqualTo(QuestionSessionStatus.FIRST_CANDIDATES_READY);
        assertThat(session.getFirstCandidates()).hasSize(1);
    }

    // 1차 후보가 없어도 2차 탐색 가능
    @Test
    void setSecondCandidatesAllowedFromFirstNoCandidates() {
        QuestionSession session = createSession();

        session.setFirstCandidates(List.of());
        session.setSecondCandidates(HeardFrom.UNKNOWN, List.of());

        assertThat(session.getStatus())
                .isEqualTo(QuestionSessionStatus.SECOND_NO_CANDIDATES);
    }

    // 2차에서 후보가 있으면 SECOND_CANDIDATES_READY
    @Test
    void setSecondCandidatesWithCandidatesSetsReady() {
        QuestionSession session = createSession();

        session.setFirstCandidates(List.of());

        session.setSecondCandidates(
                HeardFrom.UNKNOWN,
                List.of(candidate("2", "바나나"))
        );

        assertThat(session.getStatus())
                .isEqualTo(QuestionSessionStatus.SECOND_CANDIDATES_READY);
    }

    // 후보가 없는 상태에서는 확정 불가
    @Test
    void confirmNotAllowedWhenNoCandidates() {
        QuestionSession session = createSession();

        session.setFirstCandidates(List.of());

        assertThatThrownBy(session::confirm)
                .isInstanceOf(IllegalStateException.class);
    }

    // 정상 후보가 있으면 확정 가능
    @Test
    void confirmAllowedWhenCandidatesExist() {
        QuestionSession session = createSession();

        session.setFirstCandidates(
                List.of(candidate("1", "사과"))
        );

        session.confirm();

        assertThat(session.getStatus())
                .isEqualTo(QuestionSessionStatus.CONFIRMED);
    }

    // 2차 후보가 0개면 미해결 처리 가능
    @Test
    void markUnresolvedAllowedWhenSecondHasNoCandidates() {
        QuestionSession session = createSession();

        session.setFirstCandidates(List.of());
        session.setSecondCandidates(HeardFrom.UNKNOWN, List.of());

        session.markUnresolved();

        assertThat(session.getStatus())
                .isEqualTo(QuestionSessionStatus.UNRESOLVED);
    }

    // 2차 후보를 모두 거절한 경우에도 미해결 처리 가능
    @Test
    void markUnresolvedAllowedAfterSecondCandidates() {
        QuestionSession session = createSession();

        session.setFirstCandidates(
                List.of(candidate("1", "사과"))
        );

        session.setSecondCandidates(
                HeardFrom.UNKNOWN,
                List.of(candidate("2", "바나나"))
        );

        session.markUnresolved();

        assertThat(session.getStatus())
                .isEqualTo(QuestionSessionStatus.UNRESOLVED);
    }

    // 1차에 보여준 단어는 2차에서 다시 제시 불가
    @Test
    void secondCandidatesCannotContainFirstCandidates() {
        QuestionSession session = createSession();

        session.setFirstCandidates(
                List.of(candidate("1", "사과"))
        );

        assertThatThrownBy(() -> session.setSecondCandidates(
                HeardFrom.UNKNOWN,
                List.of(candidate("2", "사과"))
        )).isInstanceOf(IllegalArgumentException.class);

        // 실패하면 기존 상태가 유지되어야 함
        assertThat(session.getStatus())
                .isEqualTo(QuestionSessionStatus.FIRST_CANDIDATES_READY);
    }

    // 확정된 세션은 2차 탐색 불가
    @Test
    void setSecondCandidatesNotAllowedAfterConfirmed() {
        QuestionSession session = createSession();

        session.setFirstCandidates(
                List.of(candidate("1", "사과"))
        );
        session.confirm();

        assertThatThrownBy(() -> session.setSecondCandidates(
                HeardFrom.UNKNOWN,
                List.of(candidate("2", "바나나"))
        )).isInstanceOf(IllegalStateException.class);
    }

    // 미해결 처리된 세션은 확정 불가
    @Test
    void confirmNotAllowedAfterUnresolved() {
        QuestionSession session = createSession();

        session.setFirstCandidates(List.of());
        session.setSecondCandidates(HeardFrom.UNKNOWN, List.of());
        session.markUnresolved();

        assertThatThrownBy(session::confirm)
                .isInstanceOf(IllegalStateException.class);
    }

    // F1 추출 직후에는 원본 발음을 유지
    @Test
    void extractionResultInitiallyPreservesOriginalPronunciation() {
        QuestionSession session = createSession();

        session.setExtractionResult(
                "구케이원이",
                List.of("구케이원이", "구케이원")
        );

        assertThat(session.getRecognizedText())
                .isEqualTo("구케이원이");

        assertThat(session.getTargetPronunciation())
                .isEqualTo("구케이원이");
    }

    // F2 판단 후 조사 제거형으로 변경 가능
    @Test
    void targetPronunciationCanBeUpdatedAfterJosaResolution() {
        QuestionSession session = createSession();

        session.setExtractionResult(
                "구케이원이",
                List.of("구케이원이", "구케이원")
        );

        session.updateTargetPronunciation("구케이원");

        assertThat(session.getRecognizedText())
                .isEqualTo("구케이원이");

        assertThat(session.getTargetPronunciation())
                .isEqualTo("구케이원");
    }

    // 검색용 표현에 없는 값은 저장 불가
    @Test
    void targetPronunciationMustBeOneOfTargetExpressions() {
        QuestionSession session = createSession();

        session.setExtractionResult(
                "구케이원이",
                List.of("구케이원이", "구케이원")
        );

        assertThatThrownBy(() ->
                session.updateTargetPronunciation("국회의원")
        ).isInstanceOf(IllegalArgumentException.class);
    }

    // 질문이 종료된 뒤에는 발음 변경 불가
    @Test
    void targetPronunciationCannotChangeAfterUnresolved() {
        QuestionSession session = createSession();

        session.setExtractionResult(
                "구케이원이",
                List.of("구케이원이", "구케이원")
        );

        session.setFirstCandidates(List.of());
        session.setSecondCandidates(HeardFrom.UNKNOWN, List.of());
        session.markUnresolved();

        assertThatThrownBy(() ->
                session.updateTargetPronunciation("구케이원")
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void publicCandidateIdMapsToInternalCandidate() {
        QuestionSession session = createSession();

        session.setFirstCandidates(
                List.of(candidate("CURATED:사과", "사과"))
        );

        // 클라이언트에는 공개 ID 전달
        assertThat(session.getFirstCandidatesForClient().getFirst().candidateId())
                .isEqualTo("c_001");

        // 공개 ID로 내부 후보 조회 가능
        assertThat(session.findCurrentCandidate("c_001"))
                .isPresent()
                .get()
                .extracting(WordCandidate::word)
                .isEqualTo("사과");
    }

    private QuestionSession createSession() {
        return QuestionSession.create(
                1L,
                "사과가 뭐야?",
                null
        );
    }

    private WordCandidate candidate(String id, String word) {
        return new WordCandidate(
                id,
                1L,
                word,
                WordSourceType.CURATED,
                0.8,
                0.8,
                0.8,
                "뜻",
                null,
                null,
                null,
                null
        );
    }
}
