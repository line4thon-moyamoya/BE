package com.moyamoya.domain.child.service;

import com.moyamoya.domain.child.dto.request.CreateChildRequest;
import com.moyamoya.domain.child.dto.request.UpdateChildRequest;
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
    public void updateChild(Long userId, Long childId, UpdateChildRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(SecurityErrorCode.AUTHENTICATION_REQUIRED));
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new CustomException(ChildErrorCode.CHILD_NOT_FOUND));
        if (!child.getUser().getId().equals(user.getId())) {
            throw new CustomException(ChildErrorCode.UPDATE_FORBIDDEN);
        }
        if (request == null) {
            throw new CustomException(ChildErrorCode.INVALID_UPDATE_REQUEST);
        }
        if (!request.hasNickname() && !request.hasAgeGroup() && !request.hasMoyaType()) {
            throw new CustomException(ChildErrorCode.EMPTY_UPDATE_REQUEST);
        }
        String nickname = child.getNickname();
        AgeGroup ageGroup = child.getAgeGroup();
        MoyaType moyaType = child.getMoyaType();
        if (request.hasNickname()) {
            if (request.getNickname() == null) {
                throw new CustomException(ChildErrorCode.INVALID_NICKNAME);
            }
            nickname = validateNickname(request.getNickname());
        }
        if (request.hasAgeGroup()) {
            if (request.getAgeGroup() == null) {
                throw new CustomException(ChildErrorCode.INVALID_AGE_GROUP);
            }
            ageGroup = parseAgeGroup(request.getAgeGroup());
        }
        if (request.hasMoyaType()) {
            if (request.getMoyaType() == null) {
                throw new CustomException(ChildErrorCode.INVALID_MOYA_TYPE);
            }
            moyaType = parseMoyaType(request.getMoyaType());
        }
        child.updateProfile(nickname, ageGroup, moyaType);
    }

    @Transactional
    public ChildResponse createChild(Long userId, CreateChildRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(SecurityErrorCode.AUTHENTICATION_REQUIRED));
        if (request == null || request.nickname() == null || request.ageGroup() == null) {
            throw new CustomException(ChildErrorCode.INVALID_CREATE_REQUEST);
        }
        String nickname = validateNickname(request.nickname());
        AgeGroup ageGroup = parseAgeGroup(request.ageGroup());
        MoyaType moyaType = MoyaType.DEFAULT;
        if (request.moyaType() != null) {
            moyaType = parseMoyaType(request.moyaType());
        }
        Child child = childRepository.save(Child.create(user, nickname, ageGroup, moyaType));
        return ChildResponse.from(child);
    }

    private String validateNickname(String value) {
        String nickname = value.trim();
        if (nickname.isBlank() || nickname.codePointCount(0, nickname.length()) > 50) {
            throw new CustomException(ChildErrorCode.INVALID_NICKNAME);
        }
        return nickname;
    }

    private AgeGroup parseAgeGroup(String value) {
        try {
            return AgeGroup.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ChildErrorCode.INVALID_AGE_GROUP);
        }
    }

    private MoyaType parseMoyaType(String value) {
        try {
            return MoyaType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ChildErrorCode.INVALID_MOYA_TYPE);
        }
    }
}
