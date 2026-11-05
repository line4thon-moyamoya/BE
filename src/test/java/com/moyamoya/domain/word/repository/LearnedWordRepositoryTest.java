package com.moyamoya.domain.word.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.word.entity.LearnedWord;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.wordbook.service.WordbookService;
import com.moyamoya.global.exception.CustomException;
import java.time.LocalDateTime;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = "jwt.secret=test-only-secret-key-at-least-32-bytes")
@Import(WordbookService.class)
class LearnedWordRepositoryTest {
    @Autowired private WordbookService service;

    @Test
    void viewingPersistsNewFlagAndLeavesOtherCardAndStickerUnchanged() {
        User user = em.persist(User.create("view@example.com", "encoded", "encoded"));
        Child child = em.persist(Child.create(user, "지우", AgeGroup.AGE_5));
        LearnedWord card = em.persist(LearnedWord.create(child, word("적금"), true));
        LearnedWord untouched = em.persist(LearnedWord.create(child, word("자동차"), false));
        em.flush();
        em.clear();

        service.markAsViewed(user.getId(), child.getId(), card.getId());
        em.flush();
        em.clear();
        service.markAsViewed(user.getId(), child.getId(), card.getId());
        em.flush();
        em.clear();

        LearnedWord stored = repository.findById(card.getId()).orElseThrow();
        assertThat(stored.isNew()).isFalse();
        assertThat(stored.isStickerEarned()).isTrue();
        assertThat(repository.findById(untouched.getId()).orElseThrow().isNew()).isTrue();
        assertThat(service.getWordbook(user.getId(), child.getId()).learnedWords())
                .filteredOn(response -> response.learnedWordId().equals(card.getId()))
                .allSatisfy(response -> assertThat(response.isNew()).isFalse());
    }

    @Test
    void anotherChildsCardIsNotFoundAndIsNotModified() {
        User user = em.persist(User.create("ownership@example.com", "encoded", "encoded"));
        Child child = em.persist(Child.create(user, "지우", AgeGroup.AGE_5));
        Child other = em.persist(Child.create(user, "서아", AgeGroup.AGE_5));
        LearnedWord card = em.persist(LearnedWord.create(other, word("통장"), false));
        em.flush();
        em.clear();

        assertThat(repository.findByIdAndChild_Id(card.getId(), child.getId())).isEmpty();
        assertThatThrownBy(() -> service.markAsViewed(user.getId(), child.getId(), card.getId()))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode().getCode()).isEqualTo("LEARNED_WORD404"));
        assertThat(repository.findById(card.getId()).orElseThrow().isNew()).isTrue();
    }
    @Autowired private TestEntityManager em;
    @Autowired private LearnedWordRepository repository;

    private Word word(String name) {
        return em.persist(Word.createExternal(name, "설명", null, null, null,
                null, null, "노래 설명", null, null, null, null, null));
    }

    @Test
    void filtersByChildOrdersByAcquisitionTimeAndIdAndFetchesWord() {
        User user = em.persist(User.create("repo@example.com", "encoded", "encoded"));
        Child child = em.persist(Child.create(user, "지우", AgeGroup.AGE_5));
        Child other = em.persist(Child.create(user, "서아", AgeGroup.AGE_5));
        LearnedWord oldest = em.persist(LearnedWord.create(child, word("적금"), false));
        LearnedWord latest = em.persist(LearnedWord.create(child, word("자동차"), true));
        LearnedWord tied = em.persist(LearnedWord.create(child, word("통장"), false));
        em.persist(LearnedWord.create(other, word("영수증"), false));
        em.flush();
        LocalDateTime now = LocalDateTime.of(2026, 11, 4, 12, 0);
        em.getEntityManager().createNativeQuery("update learned_words set learned_at = :time where learned_word_id = :id")
                .setParameter("time", now.minusDays(1)).setParameter("id", oldest.getId()).executeUpdate();
        em.getEntityManager().createNativeQuery("update learned_words set learned_at = :time where learned_word_id in (:first, :second)")
                .setParameter("time", now).setParameter("first", latest.getId()).setParameter("second", tied.getId()).executeUpdate();
        em.clear();

        var result = repository.findAllWithWordByChildId(child.getId());

        assertThat(result).extracting(LearnedWord::getId)
                .containsExactly(tied.getId(), latest.getId(), oldest.getId());
        assertThat(result).allSatisfy(card -> assertThat(Hibernate.isInitialized(card.getWord())).isTrue());
        assertThat(result).extracting(card -> card.getWord().getWord()).containsExactly("통장", "자동차", "적금");
    }

    @Test
    void returnsEmptyListForChildWithoutCards() {
        assertThat(repository.findAllWithWordByChildId(Long.MAX_VALUE)).isEmpty();
    }
}
