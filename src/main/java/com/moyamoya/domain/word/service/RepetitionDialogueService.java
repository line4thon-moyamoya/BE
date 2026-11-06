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

    public DialogueResponse acquisition(boolean newAcquisition) {
        String text = newAcquisition ? "새로운 단어 카드를 받았어! 수첩에서 확인해 봐!" : "이미 수첩에 있는 단어야!";
        return synthesize(text);
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
