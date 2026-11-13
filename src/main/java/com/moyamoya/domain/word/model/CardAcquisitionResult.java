package com.moyamoya.domain.word.model;

public record CardAcquisitionResult(
        Long learnedWordId,
        boolean isNewAcquisition,
        boolean stickerEarned,
        long couponCount,
        Long songId
) {
}
