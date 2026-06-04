package com.elipair.church.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 전 도메인이 공유하는 에러 코드 단일 정의(스펙 §5).
 * code는 클라이언트 분기용 영문 식별자, title은 사용자 표시용 한글.
 * 보안 유래 3종(AUTHENTICATION_FAILED·INVALID_TOKEN·ACCESS_DENIED)은 여기서 "정의만" 하고,
 * 실제 응답 배선(AuthenticationEntryPoint/AccessDeniedHandler)은 #4에서 본 코드를 재사용한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "INVALID_INPUT_VALUE", "유효하지 않은 입력값"),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "인증에 실패했습니다"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않은 토큰입니다"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "접근 권한이 없습니다"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "리소스를 찾을 수 없습니다"),
    MEDIA_IN_USE(HttpStatus.CONFLICT, "MEDIA_IN_USE", "사용 중인 미디어입니다"),
    OPTIMISTIC_LOCK_CONFLICT(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", "다른 사용자가 먼저 수정했습니다"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", "이미 존재하는 리소스입니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다");

    private final HttpStatus status;
    private final String code;
    private final String title;
}
