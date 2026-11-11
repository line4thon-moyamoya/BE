package com.moyamoya.domain.word.service;

import com.moyamoya.domain.question.service.MoyaTtsService;
import com.moyamoya.domain.word.dto.response.DialogueResponse;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepetitionDialogueService {
    private final MoyaTtsService moyaTtsService;

    public DialogueResponse repetition(String word, boolean correct) {
        String text = correct ? "우와! " + word + "! 잘했어!" : word + "! " + word + "! 다음에 또 해보자!";
        return synthesize(text);
    }

    public DialogueResponse acquisition(boolean newAcquisition, long couponCount, boolean songRegistered) {
        if (!newAcquisition) {
            return synthesize("이미 수첩에 있는 단어야!");
        }
        if (songRegistered) {
            return synthesize("쿠폰 다섯 장을 다 모았어!");
        }
        String remaining = switch ((int) couponCount) {
            case 1 -> "네";
            case 2 -> "세";
            case 3 -> "두";
            case 4 -> "한";
            default -> null;
        };
        return synthesize(remaining == null ? "쿠폰 받았다! 짝짝!"
                : "쿠폰 받았다! 짝짝! 노래까지 " + remaining + " 개 남았어!");
    }

    private DialogueResponse synthesize(String text) {
        try {
            byte[] audio = moyaTtsService.synthesize(text);
            if (audio != null && audio.length > 0) {
                return new DialogueResponse(text, Base64.getEncoder().encodeToString(audio), "audio/wav");
            }
            log.warn("따라 말하기 안내 TTS가 빈 음성을 반환했습니다.");
        } catch (RuntimeException exception) {
            log.warn("따라 말하기 안내 TTS 생성 실패: {}", exception.getClass().getSimpleName());
        }
        return new DialogueResponse(text, null, null);
    }
}
