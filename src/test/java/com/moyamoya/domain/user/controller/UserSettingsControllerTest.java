package com.moyamoya.domain.user.controller;

import com.moyamoya.domain.user.dto.response.UserSettingsResponse;
import com.moyamoya.domain.user.service.UserSettingsService;
import com.moyamoya.domain.child.dto.response.ChildResponse;
import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.MoyaType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserSettingsControllerTest {
    private final UserSettingsService service = mock(UserSettingsService.class);

    @Test
    void returnsSettings() throws Exception {
        when(service.getSettings(1L)).thenReturn(new UserSettingsResponse(
                List.of(new ChildResponse(2L, "child", AgeGroup.AGE_5, MoyaType.CAR)), true, false));
        MockMvcBuilders.standaloneSetup(new UserSettingsController(service)).build()
                .perform(get("/api/v1/users/me/settings").principal(() -> "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.children[0].childId").value(2))
                .andExpect(jsonPath("$.data.children[0].ageGroup").value("AGE_5"))
                .andExpect(jsonPath("$.data.children[0].moyaType").value("CAR"))
                .andExpect(jsonPath("$.data.voiceProcessingAgreed").value(true))
                .andExpect(jsonPath("$.data.voiceImprovementAgreed").value(false));
    }

    @Test
    void returnsEmptyArrayWithoutChildren() throws Exception {
        when(service.getSettings(1L)).thenReturn(new UserSettingsResponse(List.of(), false, false));
        MockMvcBuilders.standaloneSetup(new UserSettingsController(service)).build()
                .perform(get("/api/v1/users/me/settings").principal(() -> "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.children").isArray())
                .andExpect(jsonPath("$.data.children").isEmpty());
    }
}
