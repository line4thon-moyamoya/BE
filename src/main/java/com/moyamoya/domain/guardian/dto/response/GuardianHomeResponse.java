package com.moyamoya.domain.guardian.dto.response;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.word.entity.WordRequest;

import java.time.LocalDate;
import java.util.List;

public record GuardianHomeResponse(
        List<ChildInfo> children,
        Long selectedChildId,
        String childName,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        long questionCount,
        long unrecognizedCount,
        long pendingRequestCount,
        List<PendingRequestPreview> pendingRequestPreview
) {

    public record ChildInfo(
            Long childId,
            String name
    ) {
        public static ChildInfo from(Child child) {
            return new ChildInfo(
                    child.getId(),
                    child.getNickname()
            );
        }
    }

    public record PendingRequestPreview(
            Long wordRequestId,
            String recognizedText
    ) {
        public static PendingRequestPreview from(WordRequest request) {
            return new PendingRequestPreview(
                    request.getId(),
                    request.getRecognizedText()
            );
        }
    }

    public static GuardianHomeResponse empty() {
        return new GuardianHomeResponse(
                List.of(),
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                List.of()
        );
    }
}

