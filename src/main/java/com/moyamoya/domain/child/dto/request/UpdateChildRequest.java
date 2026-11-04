package com.moyamoya.domain.child.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "아이 프로필 수정 요청. 생략한 필드는 유지하며 null은 허용하지 않습니다.")
public class UpdateChildRequest {
    @Schema(description = "아이 별명. 앞뒤 공백 제외 1~50자", example = "지우")
    private String nickname;
    @Schema(description = "나이 구간. AGE_3_4 또는 AGE_5", example = "AGE_3_4")
    private String ageGroup;
    @Schema(description = "모야 유형. DEFAULT, CAR, PRINCESS", example = "DEFAULT")
    private String moyaType;

    @Getter(lombok.AccessLevel.NONE)
    private boolean nicknamePresent;
    @Getter(lombok.AccessLevel.NONE)
    private boolean ageGroupPresent;
    @Getter(lombok.AccessLevel.NONE)
    private boolean moyaTypePresent;

    @JsonSetter("nickname")
    public void readNickname(String nickname) {
        this.nickname = nickname;
        this.nicknamePresent = true;
    }

    @JsonSetter("ageGroup")
    public void readAgeGroup(String ageGroup) {
        this.ageGroup = ageGroup;
        this.ageGroupPresent = true;
    }

    @JsonSetter("moyaType")
    public void readMoyaType(String moyaType) {
        this.moyaType = moyaType;
        this.moyaTypePresent = true;
    }

    public boolean hasNickname() { return nicknamePresent; }
    public boolean hasAgeGroup() { return ageGroupPresent; }
    public boolean hasMoyaType() { return moyaTypePresent; }
}
