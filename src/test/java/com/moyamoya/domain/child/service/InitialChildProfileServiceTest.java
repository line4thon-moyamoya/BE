package com.moyamoya.domain.child.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.child.dto.request.InitialChildProfileRequest;
import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.entity.MoyaType;
import com.moyamoya.domain.child.exception.ChildErrorCode;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.user.repository.UserRepository;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.security.exception.SecurityErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class InitialChildProfileServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final ChildRepository children = mock(ChildRepository.class);
    private final ChildService service = new ChildService(users, children);
    private final User owner = User.create("parent@example.com", "encoded", "encoded");
    private final Child child = Child.create(owner, "지우", AgeGroup.AGE_3_4);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(owner, "id", 7L);
        ReflectionTestUtils.setField(child, "id", 2L);
        when(users.findById(7L)).thenReturn(Optional.of(owner));
        when(children.findById(2L)).thenReturn(Optional.of(child));
    }

    @ParameterizedTest
    @EnumSource(MoyaType.class)
    void setsSelectedMoyaAndPreservesOtherProfileFields(MoyaType selected) {
        var response = service.setupInitialProfile(7L, 2L, new InitialChildProfileRequest(selected.name()));

        assertEquals(2L, response.childId());
        assertEquals(selected, response.moyaType());
        assertEquals(selected, child.getMoyaType());
        assertEquals("지우", child.getNickname());
        assertEquals(AgeGroup.AGE_3_4, child.getAgeGroup());
        assertSame(owner, child.getUser());
        verify(children, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"UNKNOWN", "car", " "})
    void rejectsInvalidMoyaWithoutChangingProfile(String moyaType) {
        CustomException error = assertThrows(CustomException.class,
                () -> service.setupInitialProfile(7L, 2L, new InitialChildProfileRequest(moyaType)));
        assertSame(ChildErrorCode.INVALID_MOYA_TYPE, error.getErrorCode());
        assertEquals(MoyaType.DEFAULT, child.getMoyaType());
    }

    @Test
    void rejectsMissingRequest() {
        CustomException error = assertThrows(CustomException.class,
                () -> service.setupInitialProfile(7L, 2L, null));
        assertSame(ChildErrorCode.INVALID_MOYA_TYPE, error.getErrorCode());
    }

    @Test
    void rejectsAnotherAccountsChildWithoutChangingProfile() {
        User other = User.create("other@example.com", "encoded", "encoded");
        ReflectionTestUtils.setField(other, "id", 8L);
        ReflectionTestUtils.setField(child, "user", other);
        CustomException error = assertThrows(CustomException.class,
                () -> service.setupInitialProfile(7L, 2L, new InitialChildProfileRequest("CAR")));
        assertSame(ChildErrorCode.UPDATE_FORBIDDEN, error.getErrorCode());
        assertEquals(MoyaType.DEFAULT, child.getMoyaType());
    }

    @Test
    void rejectsMissingChild() {
        when(children.findById(2L)).thenReturn(Optional.empty());
        CustomException error = assertThrows(CustomException.class,
                () -> service.setupInitialProfile(7L, 2L, new InitialChildProfileRequest("CAR")));
        assertSame(ChildErrorCode.CHILD_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void rejectsDeletedAccount() {
        when(users.findById(7L)).thenReturn(Optional.empty());
        CustomException error = assertThrows(CustomException.class,
                () -> service.setupInitialProfile(7L, 2L, new InitialChildProfileRequest("CAR")));
        assertSame(SecurityErrorCode.AUTHENTICATION_REQUIRED, error.getErrorCode());
        verifyNoInteractions(children);
    }
}
