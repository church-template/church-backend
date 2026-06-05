package com.elipair.church.domain.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MimeTypeValidatorTest {

    private final MimeTypeValidator validator = new MimeTypeValidator();

    @Test
    void detects_jpeg() {
        assertThat(validator.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}))
                .contains("image/jpeg");
    }

    @Test
    void detects_png() {
        assertThat(validator.detect(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}))
                .contains("image/png");
    }

    @Test
    void detects_gif() {
        assertThat(validator.detect(new byte[] {0x47, 0x49, 0x46, 0x38, 0x39, 0x61}))
                .contains("image/gif");
    }

    @Test
    void detects_webp() {
        byte[] webp = {0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
        assertThat(validator.detect(webp)).contains("image/webp");
    }

    @Test
    void detects_pdf() {
        assertThat(validator.detect(new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31}))
                .contains("application/pdf");
    }

    @Test
    void riff_without_webp_marker_is_rejected() {
        // RIFF 컨테이너지만 WAV 등(8–11이 WEBP 아님) → 거부
        byte[] wav = {0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x41, 0x56, 0x45};
        assertThat(validator.detect(wav)).isEmpty();
    }

    @Test
    void unknown_bytes_are_rejected() {
        assertThat(validator.detect(new byte[] {0x00, 0x01, 0x02, 0x03, 0x04, 0x05}))
                .isEmpty();
    }

    @Test
    void truncated_header_is_rejected() {
        assertThat(validator.detect(new byte[] {(byte) 0xFF})).isEmpty(); // JPEG 시그니처 미만
        assertThat(validator.detect(new byte[] {0x52, 0x49, 0x46, 0x46})).isEmpty(); // RIFF만, WEBP 마커 없음
    }

    @Test
    void null_and_empty_are_rejected() {
        assertThat(validator.detect(null)).isEmpty();
        assertThat(validator.detect(new byte[0])).isEmpty();
    }
}
