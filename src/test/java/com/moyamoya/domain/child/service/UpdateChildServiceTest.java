package com.moyamoya.domain.child.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.child.dto.request.UpdateChildRequest;
import com.moyamoya.domain.child.entity.AgeGroup;
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

class UpdateChildServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final ChildRepository children = mock(ChildRepository.class);
    private final ChildService service = new ChildService(users, children);
    private final ObjectMapper mapper = new ObjectMapper();
    private final User owner = mock(User.class);
    private final Child child = Child.create(owner, "서아", AgeGroup.AGE_5, MoyaType.CAR);

    @BeforeEach
    void setUp() {
        when(owner.getId()).thenReturn(1L);
        when(users.findById(1L)).thenReturn(Optional.of(owner));
        when(children.findById(2L)).thenReturn(Optional.of(child));
    }

    private void update(String json) throws Exception {
        service.updateChild(1L, 2L, mapper.readValue(json, UpdateChildRequest.class));
    }

    @Test
    void updatesAllFields() throws Exception {
        update("{\"nickname\":\" 지우 \",\"ageGroup\":\"AGE_3_4\",\"moyaType\":\"DEFAULT\"}");
        assertEquals("지우", child.getNickname());
        assertEquals(AgeGroup.AGE_3_4, child.getAgeGroup());
        assertEquals(MoyaType.DEFAULT, child.getMoyaType());
        assertSame(owner, child.getUser());
    }

    @Test
    void preservesOmittedFieldsAndAcceptsUnchangedValues() throws Exception {
        update("{\"nickname\":\"지우\"}");
        update("{\"nickname\":\"지우\"}");
        assertEquals("지우", child.getNickname());
        assertEquals(AgeGroup.AGE_5, child.getAgeGroup());
        assertEquals(MoyaType.CAR, child.getMoyaType());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{}|CHILD400",
            "{\"nickname\":null}|CHILD400_INVALID_NICKNAME",
            "{\"nickname\":\" \"}|CHILD400_INVALID_NICKNAME",
            "{\"ageGroup\":null}|CHILD400_INVALID_AGE",
            "{\"ageGroup\":\"AGE_6\"}|CHILD400_INVALID_AGE",
            "{\"moyaType\":null}|CHILD400_INVALID_MOYA",
            "{\"moyaType\":\"UNKNOWN\"}|CHILD400_INVALID_MOYA"
    }, delimiter = '|')
    void rejectsInvalidInputWithoutChangingProfile(String json, String code) {
        CustomException error = assertThrows(CustomException.class, () -> update(json));
        assertEquals(code, error.getErrorCode().getCode());
        assertEquals("서아", child.getNickname());
        assertEquals(MoyaType.CAR, child.getMoyaType());
    }

    @Test
    void validatesEveryFieldBeforeChangingEntity() {
        assertThrows(CustomException.class,
                () -> update("{\"nickname\":\"지우\",\"moyaType\":\"INVALID\"}"));
        assertEquals("서아", child.getNickname());
    }

    @Test
    void rejectsLongNickname() {
        assertThrows(CustomException.class,
                () -> update("{\"nickname\":\"" + "가".repeat(51) + "\"}"));
    }

    @Test
    void rejectsOtherParentsChild() {
        User other = mock(User.class);
        when(other.getId()).thenReturn(3L);
        when(children.findById(2L)).thenReturn(Optional.of(
                Child.create(other, "다른 아이", AgeGroup.AGE_5)));
        CustomException error = assertThrows(CustomException.class, () -> update("{\"nickname\":\"지우\"}"));
        assertEquals("CHILD403", error.getErrorCode().getCode());
    }

    @Test
    void rejectsMissingChild() {
        when(children.findById(2L)).thenReturn(Optional.empty());
        CustomException error = assertThrows(CustomException.class, () -> update("{\"nickname\":\"지우\"}"));
        assertEquals("CHILD404", error.getErrorCode().getCode());
    }

    @Test
    void rejectsMissingUser() {
        when(users.findById(1L)).thenReturn(Optional.empty());
        CustomException error = assertThrows(CustomException.class, () -> update("{\"nickname\":\"지우\"}"));
        assertEquals("AUTH401", error.getErrorCode().getCode());
    }
}
