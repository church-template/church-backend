package com.elipair.church.global.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 전역 예외 핸들러 검증용 테스트 전용 컨트롤러 — 각 예외 유형을 의도적으로 던진다. */
@RestController
class ExceptionTestController {

    @GetMapping("/test/business-not-found")
    void businessNotFound() {
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @GetMapping("/test/optimistic-lock")
    void optimisticLock() {
        throw new ObjectOptimisticLockingFailureException("충돌", new RuntimeException());
    }

    @GetMapping("/test/boom")
    void boom() {
        throw new IllegalStateException("예상치 못한 오류");
    }

    @PostMapping("/test/validate")
    void validate(@Valid @RequestBody SampleRequest request) {
        // 검증 통과 시 동작 없음
    }

    record SampleRequest(@NotBlank String name) {}
}
