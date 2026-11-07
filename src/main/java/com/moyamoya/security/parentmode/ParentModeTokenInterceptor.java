package com.moyamoya.security.parentmode;

import com.moyamoya.global.exception.CustomException;
import com.moyamoya.security.JwtTokenProvider;
import com.moyamoya.security.exception.SecurityErrorCode;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class ParentModeTokenInterceptor implements HandlerInterceptor {

    public static final String HEADER_NAME = "X-Parent-Mode-Token";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        if (!(handler instanceof HandlerMethod handlerMethod) || !requiresParentMode(handlerMethod)) {
            return true;
        }

        Principal principal = request.getUserPrincipal();
        if (principal == null) {
            throw new CustomException(SecurityErrorCode.AUTHENTICATION_REQUIRED);
        }

        String parentModeToken = request.getHeader(HEADER_NAME);
        if (parentModeToken == null || parentModeToken.isBlank()) {
            throw new CustomException(SecurityErrorCode.PARENT_MODE_TOKEN_REQUIRED);
        }

        Long accessTokenUserId = parseAccessTokenUserId(principal);
        Long parentModeUserId = parseParentModeToken(parentModeToken.trim());
        if (!accessTokenUserId.equals(parentModeUserId)) {
            throw new CustomException(SecurityErrorCode.PARENT_MODE_USER_MISMATCH);
        }

        return true;
    }

    private boolean requiresParentMode(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), ParentModeRequired.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), ParentModeRequired.class);
    }

    private Long parseAccessTokenUserId(Principal principal) {
        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException exception) {
            throw new CustomException(SecurityErrorCode.INVALID_ACCESS_TOKEN);
        }
    }

    private Long parseParentModeToken(String token) {
        try {
            return jwtTokenProvider.getUserIdFromParentModeToken(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new CustomException(SecurityErrorCode.INVALID_PARENT_MODE_TOKEN);
        }
    }
}
