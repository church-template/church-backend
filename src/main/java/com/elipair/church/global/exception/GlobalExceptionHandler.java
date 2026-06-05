package com.elipair.church.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 모든 컨트롤러·도메인 계층 예외를 RFC 7807 형식으로 일관 매핑한다(스펙 §5). */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 메서드 보안(@PreAuthorize) 거부 — 익명이면 401 INVALID_TOKEN, 인증됐으나 권한부족이면 403 ACCESS_DENIED. */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationDenied(
            AuthorizationDeniedException e, HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated();
        ErrorCode code = anonymous ? ErrorCode.INVALID_TOKEN : ErrorCode.ACCESS_DENIED;
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, request.getRequestURI()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest request) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, e.getMessage(), request.getRequestURI()));
    }

    /** 미디어 삭제 차단(스펙 §5.10) — 409 MEDIA_IN_USE + 참조 목록(references) 동봉. */
    @ExceptionHandler(MediaInUseException.class)
    public ResponseEntity<ErrorResponse> handleMediaInUse(MediaInUseException e, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.MEDIA_IN_USE.getStatus())
                .body(ErrorResponse.ofMediaInUse(
                        ErrorCode.MEDIA_IN_USE,
                        "이 미디어를 참조하는 콘텐츠가 있어 삭제할 수 없습니다.",
                        request.getRequestURI(),
                        e.getReferences()));
    }

    /** 멀티파트 한도 초과(서블릿이 본문 파싱 전에 거부) — FileStorage 내부 검증과 같은 413 FILE_SIZE_EXCEEDED로 통일. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException e, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.FILE_SIZE_EXCEEDED.getStatus())
                .body(ErrorResponse.of(ErrorCode.FILE_SIZE_EXCEEDED, request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        List<ErrorResponse.ValidationError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.ValidationError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ErrorResponse.ofValidation(ErrorCode.INVALID_INPUT_VALUE, request.getRequestURI(), errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, "요청 본문을 읽을 수 없습니다", request.getRequestURI()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            ObjectOptimisticLockingFailureException e, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.OPTIMISTIC_LOCK_CONFLICT.getStatus())
                .body(ErrorResponse.of(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, request.getRequestURI()));
    }

    /** 존재하지 않는 핸들러·리소스 경로 요청 — 404 매핑. */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception e, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND, request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, request.getRequestURI()));
    }
}
