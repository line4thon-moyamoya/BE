package com.moyamoya.domain.child.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.moyamoya.domain.child.dto.request.CreateChildRequest;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.entity.MoyaType;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.user.repository.UserRepository;
import com.moyamoya.global.exception.CustomException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChildServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final ChildRepository children = mock(ChildRepository.class);
    private final ChildService service = new ChildService(users, children);
    private final User user = User.create("parent@example.com", "encoded", "encoded");

    @BeforeEach
    void setUp() {
        when(users.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void createsChildForAuthenticatedParentWithDefaultMoya() {
        when(children.save(any(Child.class))).thenAnswer(invocation -> {
            Child child = invocation.getArgument(0);
            assertSame(user, child.getUser());
            return child;
        });
        var response = service.createChild(1L, new CreateChildRequest(" 서아 ", "AGE_5", null));
        assertEquals("서아", response.nickname());
        assertEquals(MoyaType.DEFAULT, response.moyaType());
    }

    @Test
    void createsChildWithSelectedMoya() {
        when(children.save(any(Child.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(MoyaType.CAR,
                service.createChild(1L, new CreateChildRequest("서아", "AGE_3_4", "CAR")).moyaType());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL|AGE_5|DEFAULT|CHILD400",
            "서아|NULL|DEFAULT|CHILD400",
            "' '|AGE_5|DEFAULT|CHILD400_INVALID_NICKNAME",
            "서아|AGE_6|DEFAULT|CHILD400_INVALID_AGE",
            "서아|AGE_5|UNKNOWN|CHILD400_INVALID_MOYA"
    }, delimiter = '|', nullValues = "NULL")
    void rejectsInvalidInputWithoutSaving(String nickname, String age, String moya, String code) {
        CustomException error = assertThrows(CustomException.class,
                () -> service.createChild(1L, new CreateChildRequest(nickname, age, moya)));
        assertEquals(code, error.getErrorCode().getCode());
        verifyNoInteractions(children);
    }

    @Test
    void rejectsNicknameLongerThanFiftyCharacters() {
        assertThrows(CustomException.class,
                () -> service.createChild(1L, new CreateChildRequest("가".repeat(51), "AGE_5", null)));
        verifyNoInteractions(children);
    }

    @Test
    void rejectsUnknownParent() {
        when(users.findById(1L)).thenReturn(Optional.empty());
        CustomException error = assertThrows(CustomException.class,
                () -> service.createChild(1L, new CreateChildRequest("서아", "AGE_5", null)));
        assertEquals("AUTH401", error.getErrorCode().getCode());
        verifyNoInteractions(children);
    }
}
