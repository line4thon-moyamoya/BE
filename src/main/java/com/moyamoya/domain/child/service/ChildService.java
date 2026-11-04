package com.moyamoya.domain.child.service;

import com.moyamoya.domain.child.dto.request.CreateChildRequest;
import com.moyamoya.domain.child.dto.response.ChildResponse;
import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.entity.MoyaType;
import com.moyamoya.domain.child.exception.ChildErrorCode;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.user.repository.UserRepository;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.security.exception.SecurityErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChildService {
    private final UserRepository userRepository;
    private final ChildRepository childRepository;

    @Transactional
    public ChildResponse createChild(Long userId, CreateChildRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(SecurityErrorCode.AUTHENTICATION_REQUIRED));
        if (request == null || request.nickname() == null || request.ageGroup() == null) {
            throw new CustomException(ChildErrorCode.INVALID_CREATE_REQUEST);
        }
        String nickname = request.nickname().trim();
        if (nickname.isBlank() || nickname.codePointCount(0, nickname.length()) > 50) {
            throw new CustomException(ChildErrorCode.INVALID_NICKNAME);
        }
        AgeGroup ageGroup;
        try {
            ageGroup = AgeGroup.valueOf(request.ageGroup());
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ChildErrorCode.INVALID_AGE_GROUP);
        }
        MoyaType moyaType = MoyaType.DEFAULT;
        if (request.moyaType() != null) {
            try {
                moyaType = MoyaType.valueOf(request.moyaType());
            } catch (IllegalArgumentException exception) {
                throw new CustomException(ChildErrorCode.INVALID_MOYA_TYPE);
            }
        }
        Child child = childRepository.save(Child.create(user, nickname, ageGroup, moyaType));
        return ChildResponse.from(child);
    }
}
