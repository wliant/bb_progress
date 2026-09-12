package com.bb.progress.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MediaServiceTest {

    /**
     * Browsers record with types like "audio/webm;codecs=opus". Keying on the raw string would
     * refuse a recording made in the app itself.
     */
    @ParameterizedTest
    @CsvSource({
        "'audio/webm;codecs=opus', audio/webm",
        "'audio/mp4; codecs=mp4a.40.2', audio/mp4",
        "'video/webm;codecs=vp8,opus', video/webm",
        "AUDIO/WEBM, audio/webm",
        "image/jpeg, image/jpeg",
    })
    void stripsParametersAndCaseFromTheContentType(String raw, String expected) {
        assertThat(MediaService.baseContentType(raw)).isEqualTo(expected);
    }

    @Test
    void nullContentTypeStaysNull() {
        assertThat(MediaService.baseContentType(null)).isNull();
    }
}
