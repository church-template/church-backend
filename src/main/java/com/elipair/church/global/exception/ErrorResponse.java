package com.elipair.church.global.exception;

import com.elipair.church.global.common.ContentRef;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * RFC 7807 기반 공통 에러 응답 바디(스펙 §5).
 * Spring ProblemDetail 대신 커스텀 record를 쓴다 — type 필드 강제·errorCode 비1급 문제 회피.
 * errors는 검증 실패 시에만, references는 MEDIA_IN_USE에만 채워지고 그 외엔 NON_NULL로 생략된다(스펙 §5.10).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String errorCode,
        String title,
        int status,
        String detail,
        String instance,
        List<ValidationError> errors,
        List<ContentRef> references) {

    public record ValidationError(String field, String reason) {}

    public static ErrorResponse of(ErrorCode code, String instance) {
        return new ErrorResponse(
                code.getCode(), code.getTitle(), code.getStatus().value(), code.getTitle(), instance, null, null);
    }

    public static ErrorResponse of(ErrorCode code, String detail, String instance) {
        return new ErrorResponse(
                code.getCode(), code.getTitle(), code.getStatus().value(), detail, instance, null, null);
    }

    public static ErrorResponse ofValidation(ErrorCode code, String instance, List<ValidationError> errors) {
        return new ErrorResponse(
                code.getCode(), code.getTitle(), code.getStatus().value(), code.getTitle(), instance, errors, null);
    }

    /** 미디어 삭제 차단(409 MEDIA_IN_USE): 본문에 참조 목록(references)을 동봉한다. */
    public static ErrorResponse ofMediaInUse(
            ErrorCode code, String detail, String instance, List<ContentRef> references) {
        return new ErrorResponse(
                code.getCode(), code.getTitle(), code.getStatus().value(), detail, instance, null, references);
    }
}
