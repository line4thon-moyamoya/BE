package com.moyamoya.domain.question.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MoyaTtsService {

    private final GoogleTtsService googleTtsService;
    private final PythonAudioProcessorClient processorClient;

    public byte[] synthesize(String text) {
        byte[] originalWav = googleTtsService.synthesize(text);
        return processorClient.process(originalWav);
    }

    public String getContentType() {
        return "audio/wav";
    }
}
