package com.moyamoya.security.parentmode;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyamoya.global.exception.GlobalExceptionHandler;
import com.moyamoya.security.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ParentModeTokenInterceptorTest {

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .addInterceptors(new ParentModeTokenInterceptor(jwtTokenProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void allowsRequestWhenBothTokensBelongToSameUser() throws Exception {
        when(jwtTokenProvider.getUserIdFromParentModeToken("parent-token")).thenReturn(7L);

        mockMvc.perform(get("/parent-only")
                        .principal(() -> "7")
                        .header(ParentModeTokenInterceptor.HEADER_NAME, "parent-token"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsRequestWithoutParentModeToken() throws Exception {
        mockMvc.perform(get("/parent-only").principal(() -> "7"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PARENT_MODE401"))
                .andExpect(jsonPath("$.message").value("보호자 모드 인증이 필요한 요청입니다."));
    }

    @Test
    void rejectsInvalidParentModeToken() throws Exception {
        when(jwtTokenProvider.getUserIdFromParentModeToken("invalid-token"))
                .thenThrow(new JwtException("invalid"));

        mockMvc.perform(get("/parent-only")
                        .principal(() -> "7")
                        .header(ParentModeTokenInterceptor.HEADER_NAME, "invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PARENT_MODE401"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 보호자 모드 토큰입니다."));
    }

    @Test
    void rejectsParentModeTokenIssuedForAnotherUser() throws Exception {
        when(jwtTokenProvider.getUserIdFromParentModeToken("other-user-token")).thenReturn(8L);

        mockMvc.perform(get("/parent-only")
                        .principal(() -> "7")
                        .header(ParentModeTokenInterceptor.HEADER_NAME, "other-user-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PARENT_MODE403"));
    }

    @Test
    void leavesRegularEndpointUnchanged() throws Exception {
        mockMvc.perform(get("/regular"))
                .andExpect(status().isOk());

        verifyNoInteractions(jwtTokenProvider);
    }

    @RestController
    static class TestController {

        @ParentModeRequired
        @GetMapping("/parent-only")
        ResponseEntity<Void> parentOnly() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/regular")
        ResponseEntity<Void> regular() {
            return ResponseEntity.ok().build();
        }
    }
}
