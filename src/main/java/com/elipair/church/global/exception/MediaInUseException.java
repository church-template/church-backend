package com.elipair.church.global.exception;

import com.elipair.church.global.common.ContentRef;
import java.util.List;
import lombok.Getter;

/**
 * 미디어 삭제 차단 예외(스펙 §5.10). 참조가 하나라도 있으면 던지며, 참조 목록을 보유해
 * GlobalExceptionHandler가 409 MEDIA_IN_USE 응답 본문에 references로 동봉한다.
 * BusinessException은 추가 payload를 못 실어 별도 예외로 둔다(타입은 global이라 ArchUnit 위반 없음).
 */
@Getter
public class MediaInUseException extends RuntimeException {

    private final transient List<ContentRef> references;

    public MediaInUseException(List<ContentRef> references) {
        super(ErrorCode.MEDIA_IN_USE.getTitle());
        this.references = references;
    }
}
