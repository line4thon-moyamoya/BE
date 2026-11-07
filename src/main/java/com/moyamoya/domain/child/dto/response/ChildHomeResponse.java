package com.moyamoya.domain.child.dto.response;

public record ChildHomeResponse(
        Long childId,
        String nickname,
        String ageGroup,
        String moyaType,
        int songCouponCount
) {
}
