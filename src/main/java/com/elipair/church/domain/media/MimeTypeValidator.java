package com.elipair.church.domain.media;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 업로드 파일의 실제 바이트 시그니처(매직바이트)로 형식을 판별한다(스펙 §5.10, 보안 경계 검증).
 * Content-Type 헤더는 위조 가능하므로, 스니핑 결과를 저장 mime_type의 권위 소스로 삼는다.
 * 허용 5종(이미지 4 + PDF)만 인식하며, 그 외/판별불가는 empty → 호출자가 업로드를 거부한다.
 * 무거운 의존성(Tika) 없이 고정 5종 시그니처만 수동 검사한다.
 */
@Component
public class MimeTypeValidator {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] GIF = {0x47, 0x49, 0x46, 0x38}; // "GIF8"
    private static final byte[] RIFF = {0x52, 0x49, 0x46, 0x46}; // "RIFF" (WEBP 컨테이너 0–3)
    private static final byte[] WEBP = {0x57, 0x45, 0x42, 0x50}; // "WEBP" (8–11)
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46}; // "%PDF"

    /** 매직바이트 검사 후 표준 mime을 반환한다. 허용 5종이 아니거나 헤더가 짧으면 empty. */
    public Optional<String> detect(byte[] header) {
        if (header == null) {
            return Optional.empty();
        }
        if (startsWith(header, JPEG)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(header, PNG)) {
            return Optional.of("image/png");
        }
        if (startsWith(header, GIF)) {
            return Optional.of("image/gif");
        }
        if (startsWith(header, RIFF) && matchesAt(header, 8, WEBP)) {
            return Optional.of("image/webp");
        }
        if (startsWith(header, PDF)) {
            return Optional.of("application/pdf");
        }
        return Optional.empty();
    }

    private boolean startsWith(byte[] data, byte[] signature) {
        return matchesAt(data, 0, signature);
    }

    private boolean matchesAt(byte[] data, int offset, byte[] signature) {
        if (data.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (data[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
