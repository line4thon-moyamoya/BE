package com.moyamoya.domain.child.service;

import com.moyamoya.domain.child.dto.response.ChildHomeResponse;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.exception.ChildErrorCode;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.word.repository.LearnedWordRepository;
import com.moyamoya.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChildHomeService {

    private final ChildRepository childRepository;
    private final LearnedWordRepository learnedWordRepository;

    @Transactional(readOnly = true)
    public ChildHomeResponse getChildHome(Long userId, Long childId) {

        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new CustomException(
                        ChildErrorCode.CHILD_NOT_FOUND
                ));

        if (!child.getUser().getId().equals(userId)) {
            throw new CustomException(
                    ChildErrorCode.HOME_ACCESS_FORBIDDEN
            );
        }

        int songCouponCount = Math.toIntExact(
                learnedWordRepository.countByChild_Id(
                        childId
                )
        );

        return new ChildHomeResponse(
                child.getId(),
                child.getNickname(),
                child.getAgeGroup().name(),
                child.getMoyaType().name(),
                songCouponCount
        );
    }
}
