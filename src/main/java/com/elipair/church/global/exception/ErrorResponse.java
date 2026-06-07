package com.elipair.church.global.exception;

import com.elipair.church.global.common.ContentRef;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * RFC 7807 기반 공통 에러 응답 바디(스펙 §5).
 * Spring ProblemDetail 대신 커스텀 record를 쓴다 — type 필드 강제·errorCode 비1급 문제 회피.
 * errors는 검증 실패 시에만, references는 MEDIA_IN_USE에만 채워지고 그 외엔 NON_NULL로 생략된다(스펙 §5.10).
 */
@Schema(name = "ErrorResponse", description = "RFC 7807 공통 에러 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @Schema(description = "클라이언트 분기용 코드", example = "INVALID_INPUT_VALUE")
        String errorCode,

        @Schema(description = "사용자 표시용 한글 제목", example = "유효하지 않은 입력값")
        String title,

        @Schema(description = "HTTP 상태", example = "400") int status,

        @Schema(description = "상세 설명", example = "입력값이 유효성 검사를 통과하지 못했습니다")
        String detail,

        @Schema(description = "오류가 난 요청 경로", example = "/api/auth/login")
        String instance,

        @Schema(description = "검증 실패 항목(검증 오류 시에만)") List<ValidationError> errors,
        @Schema(description = "미디어 참조 목록(MEDIA_IN_USE 시에만)") List<ContentRef> references) {

    public record ValidationError(
            @Schema(description = "검증 실패 필드명", example = "phone")
            String field,

            @Schema(description = "실패 사유", example = "전화번호 형식이 올바르지 않습니다")
            String reason) {}

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
