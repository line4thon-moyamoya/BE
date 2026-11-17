package com.moyamoya.domain.question.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.question.config.QuestionDataLoader;
import com.moyamoya.domain.question.model.DictionaryEntry;
import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.word.entity.HeardFrom;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.entity.WordSourceType;
import com.moyamoya.domain.word.repository.WordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CandidateSearchServiceTest {

    private CandidateRankingService rankingService;
    private BadWordFilterService badWordFilterService;
    private WordRepository wordRepository;
    private ExternalDictionaryService dictionaryService;
    private ExternalContextScoringService externalContextScoringService;
    private CandidateSearchService service;

    @BeforeEach
    void setUp() throws Exception {
        QuestionDataLoader loader = new QuestionDataLoader(new ObjectMapper());
        loader.load();
        badWordFilterService = Mockito.mock(BadWordFilterService.class);
        when(badWordFilterService.containsBlockedText(any()))
                .thenReturn(false);

        rankingService = new CandidateRankingService(
                loader,
                new PronunciationSimilarity(),
                new PronunciationVariantGenerator(loader),
                new ContextScoringService(),
                badWordFilterService
        );
        wordRepository = Mockito.mock(WordRepository.class);
        dictionaryService = Mockito.mock(ExternalDictionaryService.class);
        externalContextScoringService =
                Mockito.mock(ExternalContextScoringService.class);

        when(externalContextScoringService.scoreCandidates(any(), any(), any()))
                .thenReturn(Map.of());

        service = new CandidateSearchService(
                rankingService,
                wordRepository,
                dictionaryService,
                externalContextScoringService,
                new PronunciationVariantGenerator(loader),
                badWordFilterService
        );
    }

    @Test
    void csvWordExactMatch() {
        List<WordCandidate> result = service.searchFirstCandidates(List.of("세금"));
        assertThat(result).isNotEmpty();
        assertThat(result.getFirst().word()).isEqualTo("세금");
    }

    @Test
    void csvWordChildPronunciation() {
        List<WordCandidate> result = service.searchFirstCandidates(List.of("데금"));
        assertThat(result).extracting(WordCandidate::word).contains("세금");
    }

    @Test
    void dbExternalWordExactMatch() {
        Word word = Word.createExternal("모험", "어떤 일을 함에 있어 두려움 없이 도전하는 일", null, null, null, null, null, HeardFrom.PARENTS, null, null);
        when(wordRepository.findAllBySourceType(WordSourceType.EXTERNAL)).thenReturn(List.of(word));

        List<WordCandidate> result = service.searchFirstCandidates(List.of("모험"));
        assertThat(result).extracting(WordCandidate::word).contains("모험");
    }

    @Test
    void externalDictionaryShouldNotBeCalledForExactCuratedWord() {
        List<WordCandidate> result = service.searchFirstCandidates(List.of("세금"));

        assertThat(result)
                .extracting(WordCandidate::word)
                .contains("세금");

        verify(dictionaryService, never())
                .searchExactMatches(any());
    }

    @Test
    void externalSearchShouldRunWhenNoExistingCandidate() {
        String unknownWord = "쀍쀍쀍";

        service.searchFirstCandidates(List.of(unknownWord));

        verify(dictionaryService, atLeastOnce())
                .searchExactMatches(any());
    }

    @Test
    void storedExternalWordShouldBeReusedWithoutDictionarySearch() {
        Word word = Word.createExternal(
                "모험",
                "새롭고 어려운 일에 도전하는 것",
                null,
                null,
                null,
                null,
                null,
                HeardFrom.PARENTS,
                null,
                null
        );

        when(wordRepository.findAllBySourceType(WordSourceType.EXTERNAL))
                .thenReturn(List.of(word));

        List<WordCandidate> result = service.searchFirstCandidates(List.of("모험"));

        assertThat(result)
                .extracting(WordCandidate::word)
                .contains("모험");

        verify(dictionaryService, never())
                .searchExactMatches(any());
    }

    @Test
    void externalSearchShouldReturnAtMostThreeCandidates() {
        List<WordCandidate> result = service.searchFirstCandidates(List.of("쀍쀍쀍"));

        assertThat(result).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void blankSearchExpressionsShouldReturnNoCandidates() {
        List<WordCandidate> result = service.searchFirstCandidates(List.of("", " "));

        assertThat(result).isEmpty();
    }

    @Test
    void dbExternalWordSimilarPronunciation() {
        Word word = Word.createExternal("노래", "음악을 부르거나 듣는 활동", null, null, null, null, null, HeardFrom.PARENTS, null, null);
        when(wordRepository.findAllBySourceType(WordSourceType.EXTERNAL)).thenReturn(List.of(word));

        List<WordCandidate> result = service.searchFirstCandidates(List.of("로래"));
        assertThat(result).extracting(WordCandidate::word).contains("노래");
    }

    @Test
    void sameWordFromCsvAndDbIsDeduplicated() {
        Word word = Word.createExternal("세금", "나라에 내는 돈", null, null, null, null, null, HeardFrom.PARENTS, null, null);
        when(wordRepository.findAllBySourceType(WordSourceType.EXTERNAL)).thenReturn(List.of(word));

        List<WordCandidate> result = service.searchFirstCandidates(List.of("세금"));
        assertThat(result).extracting(WordCandidate::word).contains("세금");
        assertThat(result.stream().filter(candidate -> "세금".equals(candidate.word()))).hasSize(1);
    }

    @Test
    void firstPassBoundaryUsesExistingThresholds() {
        List<WordCandidate> result = service.searchFirstCandidates(List.of("데금"));
        assertThat(result.size()).isLessThanOrEqualTo(3);
        assertThat(result).isNotEmpty();
    }

    @Test
    void secondPassBoundaryUsesExistingThresholds() {
        List<WordCandidate> result = service.searchSecondCandidates(
                List.of("데금"),
                "엄마가 데금이 뭐야?",
                HeardFrom.PARENTS,
                Set.of("CURATED:세금")
        );
        assertThat(result.size()).isLessThanOrEqualTo(3);
    }

    @Test
    void externalSearchNeededWhenBestScoreBelowThreshold() {
        List<WordCandidate> candidates = List.of(
                new WordCandidate("CURATED:test", null, "테스트", WordSourceType.CURATED, 0.70, 0.10, 0.70, "def", null, null, null, null)
        );
        assertThat(service.shouldTriggerExternalSearch(candidates)).isTrue();
    }

    @Test
    void exactMatchDoesNotTriggerExternalSearch() {
        List<WordCandidate> candidates = List.of(
                new WordCandidate("CURATED:세금", null, "세금", WordSourceType.CURATED, 1.0, 0.10, 1.0, "def", null, null, null, null)
        );
        assertThat(service.shouldTriggerExternalSearch(candidates)).isFalse();
    }

    @Test
    void secondCandidatesExcludesFirstCandidateKeys() {
        List<WordCandidate> result = service.searchSecondCandidates(
                List.of("데금"),
                "데금이 뭐야?",
                HeardFrom.PARENTS,
                Set.of("CURATED:세금")
        );
        assertThat(result).allSatisfy(candidate -> assertThat(candidate.candidateId()).isNotEqualTo("CURATED:세금"));
    }

    @Test
    void firstPassDoesNotCallExternalContextScoring() {
        service.searchFirstCandidates(List.of("세금"));

        verify(externalContextScoringService, never())
                .scoreCandidates(any(), any(), any());
    }

    @Test
    void secondPassCallsExternalContextScoringForDictionaryVerifiedExternalCandidates() {
        Word word = Word.createExternal(
                "모험",
                "새롭고 어려운 일에 도전하는 것",
                null,
                null,
                null,
                null,
                null,
                HeardFrom.PARENTS,
                null,
                null
        );
        when(wordRepository.findAllBySourceType(WordSourceType.EXTERNAL))
                .thenReturn(List.of(word));
        when(dictionaryService.searchExactMatches("모험"))
                .thenReturn(List.of(new DictionaryEntry("모험", "새롭고 어려운 일에 도전하는 것", null)));

        service.searchSecondCandidates(
                List.of("모험"),
                "엄마가 모험이 뭐야? 라고 말했어",
                HeardFrom.PARENTS,
                Set.of()
        );

        verify(externalContextScoringService, atLeastOnce())
                .scoreCandidates(any(), any(), any());
    }

    @Test
    void reverseSearchShouldFindNewDictionaryWord() {
        when(dictionaryService.searchExactMatches("통닭"))
                .thenReturn(List.of(
                        new DictionaryEntry(
                                "통닭",
                                "닭을 통째로 조리한 음식",
                                null
                        )
                ));

        List<WordCandidate> result =
                service.searchFirstCandidates(List.of("토닭"));

        assertThat(result)
                .extracting(WordCandidate::word)
                .contains("통닭");

        verify(dictionaryService, atLeastOnce())
                .searchExactMatches("통닭");
    }

    @Test
    void secondPassShouldEvaluateNewDictionaryCandidate() {
        when(dictionaryService.searchExactMatches("통닭"))
                .thenReturn(List.of(
                        new DictionaryEntry(
                                "통닭",
                                "닭을 통째로 조리한 음식",
                                null
                        )
                ));

        when(externalContextScoringService.scoreCandidates(
                any(), any(), any()
        )).thenReturn(Map.of("DICTIONARY:통닭", 0.95));

        List<WordCandidate> result =
                service.searchSecondCandidates(
                        List.of("통닭"),
                        "엄마가 통닭 먹자고 했는데 뭐야?",
                        HeardFrom.PARENTS,
                        Set.of()
                );

        assertThat(result)
                .filteredOn(candidate ->
                        candidate.word().equals("통닭")
                )
                .isNotEmpty()
                .allSatisfy(candidate ->
                        assertThat(candidate.contextScore())
                                .isEqualTo(0.95)
                );

        verify(externalContextScoringService)
                .scoreCandidates(any(), any(), any());
    }

    @Test
    void mismatchedDictionaryHeadwordShouldBeExcluded() {
        when(dictionaryService.searchExactMatches("쀍쀍쀍"))
                .thenReturn(List.of(
                        new DictionaryEntry(
                                "사과",
                                "과일",
                                null
                        )
                ));

        List<WordCandidate> result =
                service.searchFirstCandidates(List.of("쀍쀍쀍"));

        assertThat(result)
                .extracting(WordCandidate::word)
                .doesNotContain("사과");
    }

    @Test
    void secondPassShouldExcludeWordRegardlessOfSource() {
        List<WordCandidate> result =
                service.searchSecondCandidates(
                        List.of("데금"),
                        "데금이 뭐야?",
                        HeardFrom.PARENTS,
                        Set.of("EXTERNAL:세금")
                );

        assertThat(result)
                .extracting(WordCandidate::word)
                .doesNotContain("세금");
    }

    @Test
    void firstPassDictionaryCallsShouldBeLimited() {
        service.searchFirstCandidates(
                List.of("쀍쀍쀍이", "쀍쀍쀍")
        );

        verify(dictionaryService, atMost(10))
                .searchExactMatches(any());
    }

    @Test
    void secondPassDictionaryCallsShouldBeLimited() {
        service.searchSecondCandidates(
                List.of("쀍쀍쀍이", "쀍쀍쀍"),
                "쀍쀍쀍이 뭐야?",
                HeardFrom.PARENTS,
                Set.of()
        );

        verify(dictionaryService, atMost(25))
                .searchExactMatches(any());
    }
}
