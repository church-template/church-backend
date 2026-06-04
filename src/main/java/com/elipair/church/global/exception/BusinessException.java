package com.elipair.church.global.exception;

import lombok.Getter;

/**
 * 도메인이 의도적으로 던지는 업무 예외. 보유한 ErrorCode로 전역 핸들러가 응답을 만든다.
 * (RESOURCE_NOT_FOUND·DUPLICATE_RESOURCE·MEDIA_IN_USE 등)
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getTitle());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
