package com.moyamoya.domain.user.dto.response;

import com.moyamoya.domain.child.dto.response.ChildResponse;
import java.util.List;

public record UserSettingsResponse(
        List<ChildResponse> children,
        boolean voiceProcessingAgreed,
        boolean voiceImprovementAgreed
) {}
