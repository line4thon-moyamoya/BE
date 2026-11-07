package com.moyamoya.domain.question.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioMimeTypeSupportTest {

    @Test
    void resolvesWavContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "audio",
                "voice.wav",
                "audio/wav",
                wavBytes()
        );

        String contentType = AudioMimeTypeSupport
                .validateAndResolveForAnalyze(file);

        assertThat(contentType).isEqualTo("audio/wav");
        assertThat(AudioMimeTypeSupport.extensionOf(contentType)).isEqualTo("wav");
    }

    @Test
    void resolvesMp3ContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "audio",
                "voice.mp3",
                "audio/mpeg",
                mp3Bytes()
        );

        String contentType = AudioMimeTypeSupport
                .validateAndResolveForAnalyze(file);

        assertThat(contentType).isEqualTo("audio/mpeg");
        assertThat(AudioMimeTypeSupport.extensionOf(contentType)).isEqualTo("mp3");
    }

    @Test
    void resolvesWebmContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "audio",
                "voice.webm",
                "audio/webm",
                webmBytes()
        );

        String contentType = AudioMimeTypeSupport
                .validateAndResolveForAnalyze(file);

        assertThat(contentType).isEqualTo("audio/webm");
        assertThat(AudioMimeTypeSupport.extensionOf(contentType)).isEqualTo("webm");
    }

    @Test
    void resolvesM4aContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "audio",
                "voice.m4a",
                "audio/mp4",
                m4aBytes()
        );

        String contentType = AudioMimeTypeSupport
                .validateAndResolveForAnalyze(file);

        assertThat(contentType).isEqualTo("audio/mp4");
        assertThat(AudioMimeTypeSupport.extensionOf(contentType)).isEqualTo("m4a");
    }

    @Test
    void rejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "audio",
                "voice.ogg",
                "audio/ogg",
                new byte[]{0x01, 0x02, 0x03}
        );

        assertThatThrownBy(() -> AudioMimeTypeSupport.validateAndResolveForAnalyze(file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMismatchedContentTypeAndActualBytes() {
        MockMultipartFile file = new MockMultipartFile(
                "audio",
                "voice.wav",
                "audio/wav",
                mp3Bytes()
        );

        assertThatThrownBy(() -> AudioMimeTypeSupport.validateAndResolveForAnalyze(file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidBytesEvenWhenDeclaredAsWav() {
        byte[] invalidBytes = "not an audio file".getBytes();

        assertThatThrownBy(() -> AudioMimeTypeSupport.validateAndResolve(
                "audio/wav",
                invalidBytes
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAdtsEvenWhenDeclaredAsMp3() {
        MockMultipartFile file = new MockMultipartFile(
                "audio",
                "voice.mp3",
                "audio/mpeg",
                adtsBytes()
        );

        assertThatThrownBy(() -> AudioMimeTypeSupport.validateAndResolveForAnalyze(file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private byte[] wavBytes() {
        return new byte[]{
                'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00,
                'W', 'A', 'V', 'E', 'f', 'm', 't', ' '
        };
    }

    private byte[] mp3Bytes() {
        return new byte[]{
                'I', 'D', '3', 0x04, 0x00, 0x00, 0x00
        };
    }

    private byte[] webmBytes() {
        return new byte[]{
                0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x42, (byte) 0x82
        };
    }

    private byte[] m4aBytes() {
        return new byte[]{
                0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p',
                'M', '4', 'A', ' ', 0x00, 0x00, 0x00, 0x00
        };
    }

    private byte[] adtsBytes() {
        return new byte[]{
                (byte) 0xFF, (byte) 0xF1, 0x50, (byte) 0x80,
                0x00, 0x1F, (byte) 0xFC, 0x00
        };
    }
}
