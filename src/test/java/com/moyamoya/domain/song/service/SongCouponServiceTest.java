package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.word.repository.LearnedWordRepository;
import com.moyamoya.global.exception.CustomException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SongCouponServiceTest {
    private final ChildRepository children = mock(ChildRepository.class);
    private final LearnedWordRepository cards = mock(LearnedWordRepository.class);
    private final SongCouponService service = new SongCouponService(children, cards);

    private void ownedChild() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(7L);
        when(children.findById(2L)).thenReturn(Optional.of(Child.create(owner, "지우", AgeGroup.AGE_5)));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 3, 5, 12})
    void returnsActualUnusedCardCountWithoutConsumingCards(long count) {
        ownedChild();
        when(cards.countUnusedForSongByChildId(2L)).thenReturn(count);
        assertThat(service.getCoupons(7L, 2L).couponCount()).isEqualTo(count);
        var order = inOrder(children, cards);
        order.verify(children).findById(2L);
        order.verify(cards).countUnusedForSongByChildId(2L);
        verifyNoMoreInteractions(cards);
    }

    private void error(String code, Long userId, Long childId) {
        assertThatThrownBy(() -> service.getCoupons(userId, childId))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo(code));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void invalidChildIdDoesNotQueryRepositories(Long childId) {
        error("CHILD400", 7L, childId);
        verifyNoInteractions(children, cards);
    }

    @Test
    void missingChildReturns404BeforeCounting() {
        error("CHILD404", 7L, 2L);
        verifyNoInteractions(cards);
    }

    @Test
    void otherParentsChildReturns403BeforeCounting() {
        ownedChild();
        error("CHILD403", 8L, 2L);
        verifyNoInteractions(cards);
    }
}
