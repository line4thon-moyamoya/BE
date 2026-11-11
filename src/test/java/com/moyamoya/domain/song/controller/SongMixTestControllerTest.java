package com.moyamoya.domain.song.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.moyamoya.domain.song.dto.response.SongMixResponse;
import com.moyamoya.domain.song.service.SongMixingService;
import com.moyamoya.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SongMixTestControllerTest {
    private final SongMixingService service = mock(SongMixingService.class);
    private final SongMixTestController controller = new SongMixTestController(service);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    private static final String PATH = "/api/v1/test/songs/mix";

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev", "", "local/prod"})
    void refusesNonLocalPrefixBeforeGenerating(String prefix) throws Exception {
        ReflectionTestUtils.setField(controller, "s3Prefix", prefix);
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"words\":[\"a\",\"b\",\"c\",\"d\",\"e\"]}"))
                .andExpect(status().isInternalServerError());
        verifyNoInteractions(service);
    }

    @BeforeEach
    void localPrefix() { ReflectionTestUtils.setField(controller, "s3Prefix", "local"); }

    @Test
    void returnsS3KeyAndPlayableUrl() throws Exception {
        var words = List.of("용기", "적금", "소방차", "알림장", "국회의원");
        when(service.mix(words)).thenReturn(new SongMixResponse("local/songs/generated/test.wav", "https://audio.test/song"));

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"words":["용기","적금","소방차","알림장","국회의원"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.audioKey").value("local/songs/generated/test.wav"))
                .andExpect(jsonPath("$.data.audioUrl").value("https://audio.test/song"));
        verify(service).mix(words);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"words\":null}", "{\"words\":[]}", "{\"words\":[\"적금\"]}",
            "{\"words\":[\"가\",\"나\",\"다\",\"라\",null]}", "{\"words\":[\"가\",\"나\",\"다\",\"라\",\" \" ]}"})
    void invalidWordsReturn400BeforeTts(String json) throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
