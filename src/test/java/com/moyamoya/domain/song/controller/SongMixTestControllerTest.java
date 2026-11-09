package com.moyamoya.domain.song.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.moyamoya.domain.song.model.SongMixResult;
import com.moyamoya.domain.song.service.SongMixingService;
import com.moyamoya.global.exception.GlobalExceptionHandler;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SongMixTestControllerTest {
    private final SongMixingService service = mock(SongMixingService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SongMixTestController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    private static final String PATH = "/api/v1/test/songs/mix";

    @Test
    void returnsDownloadableWav() throws Exception {
        byte[] wav = {82, 73, 70, 70, 1};
        var words = List.of("용기", "적금", "소방차", "알림장", "국회의원");
        when(service.mix(words)).thenReturn(new SongMixResult(Path.of("local-song-output/song-test.wav"), wav));

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"words":["용기","적금","소방차","알림장","국회의원"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/wav"))
                .andExpect(content().bytes(wav))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"song-test.wav\""));
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
