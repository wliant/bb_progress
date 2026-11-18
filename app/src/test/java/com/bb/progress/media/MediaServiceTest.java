package com.bb.progress.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;

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

    /**
     * The head shape upload path decides whether to refuse before anything is stored, so the
     * kind has to be derivable from the upload alone.
     */
    @ParameterizedTest
    @CsvSource({
        "image/png, PHOTO",
        "'image/jpeg; charset=binary', PHOTO",
        "video/mp4, VIDEO",
        "'audio/webm;codecs=opus', AUDIO",
    })
    void derivesTheKindFromAnUpload(String contentType, MediaKind expected) {
        assertThat(MediaService.kindOf(upload(contentType))).isEqualTo(expected);
    }

    @Test
    void anUnknownTypeHasNoKind() {
        assertThat(MediaService.kindOf(upload("application/pdf"))).isNull();
        assertThat(MediaService.kindOf(upload(null))).isNull();
    }

    private static MockMultipartFile upload(String contentType) {
        return new MockMultipartFile("files", "f", contentType, "bytes".getBytes());
    }
}
