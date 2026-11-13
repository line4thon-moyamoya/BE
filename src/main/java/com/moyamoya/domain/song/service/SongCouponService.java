package com.moyamoya.domain.song.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.song.dto.response.SongCouponResponse;
import com.moyamoya.domain.song.exception.SongErrorCode;
import com.moyamoya.domain.word.repository.LearnedWordRepository;
import com.moyamoya.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SongCouponService {
    private final ChildRepository childRepository;
    private final LearnedWordRepository learnedWordRepository;

    @Transactional(readOnly = true)
    public SongCouponResponse getCoupons(Long userId, Long childId) {
        if (childId == null || childId <= 0) {
            throw new CustomException(SongErrorCode.INVALID_CHILD_ID);
        }
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new CustomException(SongErrorCode.CHILD_NOT_FOUND));
        if (!child.getUser().getId().equals(userId)) {
            throw new CustomException(SongErrorCode.CHILD_ACCESS_DENIED);
        }
        return new SongCouponResponse(learnedWordRepository.countUnusedForSongByChildId(childId));
    }
}
