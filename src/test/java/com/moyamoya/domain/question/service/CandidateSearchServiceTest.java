package com.moyamoya.domain.question.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.question.config.QuestionDataLoader;
import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.word.entity.HeardFrom;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.entity.WordSourceType;
import com.moyamoya.domain.word.repository.WordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateSearchServiceTest {

    private CandidateRankingService rankingService;
    private WordRepository wordRepository;
    private ExternalDictionaryService dictionaryService;
    private CandidateSearchService service;

    @BeforeEach
    void setUp() throws Exception {
        QuestionDataLoader loader = new QuestionDataLoader(new ObjectMapper());
        loader.load();

        rankingService = new CandidateRankingService(
                loader,
                new PronunciationSimilarity(),
                new PronunciationVariantGenerator(loader),
                new ContextScoringService()
        );
        wordRepository = Mockito.mock(WordRepository.class);
        dictionaryService = Mockito.mock(ExternalDictionaryService.class);
        service = new CandidateSearchService(
                rankingService,
                wordRepository,
                dictionaryService,
                new PronunciationVariantGenerator(loader)
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
}
