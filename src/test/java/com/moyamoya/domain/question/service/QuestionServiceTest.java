package com.moyamoya.domain.question.service;

import com.moyamoya.domain.question.extractor.JosaCandidateGenerator;
import com.moyamoya.domain.question.extractor.QuestionTargetExtractor;
import com.moyamoya.domain.question.model.QuestionExtractionResult;
import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.question.session.QuestionSessionStore;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.entity.WordSourceType;
import com.moyamoya.domain.word.repository.WordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionServiceTest {

    private QuestionTargetExtractor targetExtractor;
    private JosaCandidateGenerator josaGenerator;
    private CandidateRankingService rankingService;
    private QuestionSessionStore sessionStore;
    private WordRepository wordRepository;
    private QuestionService questionService;

    @BeforeEach
    void setUp() {
        targetExtractor = mock(QuestionTargetExtractor.class);
        josaGenerator = mock(JosaCandidateGenerator.class);
        rankingService = mock(CandidateRankingService.class);
        sessionStore = mock(QuestionSessionStore.class);
        wordRepository = mock(WordRepository.class);

        questionService = new QuestionService(
                targetExtractor,
                josaGenerator,
                rankingService,
                sessionStore,
                wordRepository
        );
    }

    @Test
    void testSttFillsCandidateWordIdFromWordRepository() {
        when(targetExtractor.extract("엄마, 데금이 뭐야?"))
                .thenReturn(QuestionExtractionResult.success("엄마, 데금이 뭐야?", "데금이"));
        when(josaGenerator.generate("데금이"))
                .thenReturn(List.of("데금이", "데금"));
        when(rankingService.rankFirst(List.of("데금이", "데금")))
                .thenReturn(List.of(new WordCandidate(
                        "CURATED:세금",
                        null,
                        "세금",
                        WordSourceType.CURATED,
                        0.92,
                        0.0,
                        0.92,
                        "기존정의",
                        null
                )));
        when(rankingService.resolveTargetPronunciation(any()))
                .thenReturn(Optional.empty());

        Word word = mock(Word.class);
        when(word.getId()).thenReturn(42L);
        when(word.getDefinition()).thenReturn("나라에 내는 돈");
        when(word.getWordAudioUrl()).thenReturn("https://cdn/audio/word-tax.mp3");
        when(wordRepository.findByWord("세금")).thenReturn(Optional.of(word));

        var response = questionService.testStt(1L, "엄마, 데금이 뭐야?");

        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().get(0).wordId()).isEqualTo(42L);
        assertThat(response.candidates().get(0).description()).isEqualTo("나라에 내는 돈");
        assertThat(response.candidates().get(0).wordAudioUrl()).isEqualTo("https://cdn/audio/word-tax.mp3");
    }
}
