package com.elipair.church.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * RFC 7807 기반 공통 에러 응답 바디(스펙 §5).
 * Spring ProblemDetail 대신 커스텀 record를 쓴다 — type 필드 강제·errorCode 비1급 문제 회피.
 * errors는 검증 실패 시에만 채워지고, 그 외엔 NON_NULL로 생략된다.
 * (MEDIA_IN_USE의 references 필드는 #6에서 가산.)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String errorCode, String title, int status, String detail, String instance, List<ValidationError> errors) {

    public record ValidationError(String field, String reason) {}

    public static ErrorResponse of(ErrorCode code, String instance) {
        return new ErrorResponse(
                code.getCode(), code.getTitle(), code.getStatus().value(), code.getTitle(), instance, null);
    }

    public static ErrorResponse of(ErrorCode code, String detail, String instance) {
        return new ErrorResponse(
                code.getCode(), code.getTitle(), code.getStatus().value(), detail, instance, null);
    }

    public static ErrorResponse ofValidation(ErrorCode code, String instance, List<ValidationError> errors) {
        return new ErrorResponse(
                code.getCode(), code.getTitle(), code.getStatus().value(), code.getTitle(), instance, errors);
    }
}
