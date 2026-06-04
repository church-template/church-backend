# G2 Global 공통 모듈·RFC 7807 예외 처리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 모든 도메인이 상속·재사용할 횡단 공통 토대(감사 BaseEntity, RFC 7807 예외, 목록 응답 표준)를 한 번에 구축한다.

**Architecture:** `global/common`에 2단 감사 기반 클래스(`BaseTimeEntity`→`BaseEntity`), `global/config`에 JPA Auditing 배관(`JpaConfig`)과 목록 직렬화 설정(`WebConfig`), `global/exception`에 RFC 7807 4부품(`ErrorCode`/`ErrorResponse`/`BusinessException`/`GlobalExceptionHandler`)을 둔다. 도메인 엔티티·컨트롤러는 만들지 않는다. 작성자 컬럼은 `Long`(global→domain 의존 차단), 목록은 Spring Data `PagedModel`을 그대로 채택한다.

**Tech Stack:** Java 21, Spring Boot 4.0.6, Spring Data JPA(Auditing), Spring Data Commons(`PagedModel`), Jakarta Validation, Lombok, JUnit5 + Testcontainers(PostgreSQL), AssertJ.

---

## 설계 출처 / 범위 경계

- 승인 설계: [`docs/superpowers/specs/2026-06-04-g2-common-exception-design.md`](../specs/2026-06-04-g2-common-exception-design.md)
- **이번 이슈에서 하지 않는 것(의도적 deferral):**
  - 보안 유래 401/403(`AUTHENTICATION_FAILED`·`INVALID_TOKEN`·`ACCESS_DENIED`)의 `AuthenticationEntryPoint`/`AccessDeniedHandler` 배선 → **#4**. 본 이슈는 `ErrorCode` 상수만 정의.
  - `AuditorAware`의 SecurityContext 조회 → **#4**. 본 이슈는 `Optional.empty()` 스텁.
  - `MEDIA_IN_USE`의 `references` 페이로드 → **#6(미디어 도메인)**. 본 이슈는 `ErrorResponse`에 검증 오류용 `errors`만 두고, `references`는 #6에서 NON_NULL 필드로 가산.
  - `ConstraintViolationException`/`HandlerMethodValidationException`(파라미터 검증) 매핑 → 해당 경로가 생기는 도메인 이슈에서 같은 advice에 가산. 본 이슈는 본문 검증(`MethodArgumentNotValidException`)만.
- **의도적 편차:** `event`/`department`는 `BaseEntity`를 상속하므로 스펙표엔 없는 `updated_at`+nullable `created_by/updated_by`를 얻는다. D9/D10 마이그레이션이 이 컬럼을 포함해야 한다(설계 문서 매트릭스 절).

## File Structure

| 파일 | 책임 |
|---|---|
| `src/main/java/com/elipair/church/global/common/BaseTimeEntity.java` | `@MappedSuperclass`, `createdAt`만 + 감사 리스너 |
| `src/main/java/com/elipair/church/global/common/BaseEntity.java` | `extends BaseTimeEntity` + `updatedAt`·`createdBy`·`updatedBy`·`deletedAt`·`version` |
| `src/main/java/com/elipair/church/global/config/JpaConfig.java` | `@EnableJpaAuditing` + `AuditorAware<Long>` 스텁 |
| `src/main/java/com/elipair/church/global/config/WebConfig.java` | `@EnableSpringDataWebSupport(VIA_DTO)` — 목록 직렬화 규약 |
| `src/main/java/com/elipair/church/global/exception/ErrorCode.java` | 에러 코드 단일 정의(enum) |
| `src/main/java/com/elipair/church/global/exception/ErrorResponse.java` | RFC 7807 응답 바디(record) |
| `src/main/java/com/elipair/church/global/exception/BusinessException.java` | ErrorCode 보유 업무 예외 |
| `src/main/java/com/elipair/church/global/exception/GlobalExceptionHandler.java` | `@RestControllerAdvice` 매핑 |
| `src/test/java/com/elipair/church/global/common/AuditingTestEntity.java` | 감사 검증용 테스트 전용 엔티티 |
| `src/test/java/com/elipair/church/global/common/BaseEntityAuditingTest.java` | 감사 동작 검증(`@DataJpaTest`) |
| `src/test/java/com/elipair/church/global/common/PagedModelSerializationTest.java` | 목록 응답 JSON 모양 검증 |
| `src/test/java/com/elipair/church/global/exception/ExceptionTestController.java` | 예외를 던지는 테스트 전용 컨트롤러 |
| `src/test/java/com/elipair/church/global/exception/GlobalExceptionHandlerTest.java` | 예외 매핑 검증(`@WebMvcTest`) |

> **환경 요구:** 작업 1·4는 Testcontainers가 PostgreSQL 컨테이너를 띄우므로 **Docker가 실행 중**이어야 한다.

---

## Task 1: 감사 기반 (BaseTimeEntity · BaseEntity · JpaConfig)

**Files:**
- Create: `src/main/java/com/elipair/church/global/common/BaseTimeEntity.java`
- Create: `src/main/java/com/elipair/church/global/common/BaseEntity.java`
- Create: `src/main/java/com/elipair/church/global/config/JpaConfig.java`
- Test: `src/test/java/com/elipair/church/global/common/AuditingTestEntity.java`
- Test: `src/test/java/com/elipair/church/global/common/BaseEntityAuditingTest.java`

- [ ] **Step 1: 테스트 전용 엔티티 + 실패 테스트 작성**

`src/test/java/com/elipair/church/global/common/AuditingTestEntity.java`:
```java
package com.elipair.church.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/** BaseEntity 감사 동작 검증용 테스트 전용 엔티티. */
@Getter
@Entity
@Table(name = "auditing_test_entity")
public class AuditingTestEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    protected AuditingTestEntity() {}

    public AuditingTestEntity(String name) {
        this.name = name;
    }
}
```

`src/test/java/com/elipair/church/global/common/BaseEntityAuditingTest.java`:
```java
package com.elipair.church.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.config.JpaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@TestPropertySource(
        properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class BaseEntityAuditingTest {

    @Autowired
    private TestEntityManager em;

    @Test
    void auditing_populates_timestamps_and_version_on_persist() {
        AuditingTestEntity saved = em.persistFlushFind(new AuditingTestEntity("샘플"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(0L);
        // 작성자는 AuditorAware 스텁이 빈 값을 반환하므로 #4까지 null
        assertThat(saved.getCreatedBy()).isNull();
        assertThat(saved.getUpdatedBy()).isNull();
        assertThat(saved.isDeleted()).isFalse();
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.common.BaseEntityAuditingTest'`
Expected: **컴파일 실패** — `BaseEntity`, `BaseTimeEntity`, `JpaConfig` 심볼을 찾을 수 없음(`cannot find symbol`).

- [ ] **Step 3: BaseTimeEntity 구현**

`src/main/java/com/elipair/church/global/common/BaseTimeEntity.java`:
```java
package com.elipair.church.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 생성 시각만 담는 최상위 감사 기반 클래스(@MappedSuperclass).
 * 마스터·회원·미디어 등 "생성 시각은 필요하나 수정추적/소프트삭제/낙관락은 불필요"한 엔티티가 상속한다.
 * 감사 리스너는 서브클래스(BaseEntity)가 상속하므로, 거기 선언된 @LastModified*도 함께 처리된다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: BaseEntity 구현**

`src/main/java/com/elipair/church/global/common/BaseEntity.java`:
```java
package com.elipair.church.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * 수정가능 콘텐츠(설교·공지·일정·부서·갤러리앨범·주보)가 상속하는 전체 감사 기반 클래스.
 * BaseTimeEntity(createdAt)에 수정추적·작성자·소프트삭제·낙관락을 더한다.
 * created_by/updated_by는 Member 연관이 아니라 member.id(Long) — global→domain 의존을 피한다.
 * 작성자 값은 AuditorAware가 SecurityContext를 읽는 #4부터 채워진다(현재 null).
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseTimeEntity {

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 5: JpaConfig 구현**

`src/main/java/com/elipair/church/global/config/JpaConfig.java`:
```java
package com.elipair.church.global.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화. createdAt/updatedAt는 자동 채워지고, createdBy/updatedBy는 AuditorAware가 공급한다.
 * 현재는 SecurityContext가 없어 빈 값을 반환하는 스텁이며, #4(보안 기반)에서 인증 회원의 id를
 * 반환하도록 본문만 교체한다("배관은 지금, 물은 #4").
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaConfig {

    @Bean
    public AuditorAware<Long> auditorAware() {
        return Optional::empty;
    }
}
```

- [ ] **Step 6: 테스트 실행 → 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.common.BaseEntityAuditingTest'`
Expected: **PASS** (Docker 필요 — Testcontainers PostgreSQL 기동).

- [ ] **Step 7: 포맷 후 커밋**

```bash
./gradlew spotlessApply
git add src/main/java/com/elipair/church/global/common/BaseTimeEntity.java \
        src/main/java/com/elipair/church/global/common/BaseEntity.java \
        src/main/java/com/elipair/church/global/config/JpaConfig.java \
        src/test/java/com/elipair/church/global/common/AuditingTestEntity.java \
        src/test/java/com/elipair/church/global/common/BaseEntityAuditingTest.java
git commit -m "feat: BaseEntity 2단 분리 + JPA Auditing 배관 (#3)"
```

---

## Task 2: RFC 7807 예외 처리 (ErrorCode · ErrorResponse · BusinessException · GlobalExceptionHandler)

**Files:**
- Create: `src/main/java/com/elipair/church/global/exception/ErrorCode.java`
- Create: `src/main/java/com/elipair/church/global/exception/ErrorResponse.java`
- Create: `src/main/java/com/elipair/church/global/exception/BusinessException.java`
- Create: `src/main/java/com/elipair/church/global/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/elipair/church/global/exception/ExceptionTestController.java`
- Test: `src/test/java/com/elipair/church/global/exception/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: 테스트 컨트롤러 + 실패 테스트 작성**

`src/test/java/com/elipair/church/global/exception/ExceptionTestController.java`:
```java
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
```

`src/test/java/com/elipair/church/global/exception/GlobalExceptionHandlerTest.java`:
```java
package com.elipair.church.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ExceptionTestController.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void businessException_maps_to_its_error_code() throws Exception {
        mockMvc.perform(get("/test/business-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("리소스를 찾을 수 없습니다"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/test/business-not-found"));
    }

    @Test
    void validation_failure_maps_to_invalid_input_value_with_errors() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void optimistic_lock_maps_to_conflict() throws Exception {
        mockMvc.perform(get("/test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void unhandled_exception_maps_to_internal_error() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.exception.GlobalExceptionHandlerTest'`
Expected: **컴파일 실패** — `BusinessException`, `ErrorCode` 심볼 없음.

- [ ] **Step 3: ErrorCode 구현**

`src/main/java/com/elipair/church/global/exception/ErrorCode.java`:
```java
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
```

- [ ] **Step 4: ErrorResponse 구현**

`src/main/java/com/elipair/church/global/exception/ErrorResponse.java`:
```java
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
        String errorCode,
        String title,
        int status,
        String detail,
        String instance,
        List<ValidationError> errors) {

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
```

- [ ] **Step 5: BusinessException 구현**

`src/main/java/com/elipair/church/global/exception/BusinessException.java`:
```java
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
```

- [ ] **Step 6: GlobalExceptionHandler 구현**

`src/main/java/com/elipair/church/global/exception/GlobalExceptionHandler.java`:
```java
package com.elipair.church.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 모든 컨트롤러·도메인 계층 예외를 RFC 7807 형식으로 일관 매핑한다(스펙 §5). */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest request) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, e.getMessage(), request.getRequestURI()));
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

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            ObjectOptimisticLockingFailureException e, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.OPTIMISTIC_LOCK_CONFLICT.getStatus())
                .body(ErrorResponse.of(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, request.getRequestURI()));
    }
}
```

- [ ] **Step 7: 테스트 실행 → 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.exception.GlobalExceptionHandlerTest'`
Expected: **PASS** (4개 테스트 모두 green). `@AutoConfigureMockMvc(addFilters = false)`로 보안 필터를 우회하므로 advice가 직접 검증된다. `@RestControllerAdvice`는 `@WebMvcTest`가 자동 포함한다.

- [ ] **Step 8: 포맷 후 커밋**

```bash
./gradlew spotlessApply
git add src/main/java/com/elipair/church/global/exception/ \
        src/test/java/com/elipair/church/global/exception/
git commit -m "feat: RFC 7807 전역 예외 처리 (ErrorCode·ErrorResponse·BusinessException·핸들러) (#3)"
```

---

## Task 3: 목록 응답 표준 (WebConfig · PagedModel)

**Files:**
- Create: `src/main/java/com/elipair/church/global/config/WebConfig.java`
- Test: `src/test/java/com/elipair/church/global/common/PagedModelSerializationTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/com/elipair/church/global/common/PagedModelSerializationTest.java`:
```java
package com.elipair.church.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;

/** 목록 응답 표준이 스펙 §5 JSON({content, page:{size,number,totalElements,totalPages}})과 일치함을 고정한다. */
class PagedModelSerializationTest {

    @Test
    void pagedModel_serializes_to_spec_list_envelope() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 10), 42);

        String json = objectMapper.writeValueAsString(new PagedModel<>(page));
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("content").size()).isEqualTo(2);
        assertThat(root.path("page").path("size").asInt()).isEqualTo(10);
        assertThat(root.path("page").path("number").asInt()).isEqualTo(0);
        assertThat(root.path("page").path("totalElements").asLong()).isEqualTo(42L);
        assertThat(root.path("page").path("totalPages").asLong()).isEqualTo(5L);
    }
}
```

- [ ] **Step 2: 테스트 실행 → 통과 확인 (PagedModel은 이미 클래스패스에 존재)**

Run: `./gradlew test --tests 'com.elipair.church.global.common.PagedModelSerializationTest'`
Expected: **PASS**. (이 테스트는 Spring Data `PagedModel`이 스펙 모양과 정확히 일치함을 증명한다 — 순수 단위 테스트, Docker 불필요.)

> 참고: 이 단계는 RED 없이 바로 GREEN이다 — 외부 라이브러리(`PagedModel`)가 이미 목표 동작을 제공하므로, 테스트의 역할은 "그 동작이 스펙과 일치하며 앞으로도 유지됨"을 회귀 고정하는 것이다.

- [ ] **Step 3: WebConfig 구현 (VIA_DTO 규약 고정)**

`src/main/java/com/elipair/church/global/config/WebConfig.java`:
```java
package com.elipair.church.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/**
 * 목록 응답 표준화. VIA_DTO 모드로 컨트롤러의 Page<T> 반환을 Spring Data PagedModel JSON
 * ({content, page:{size,number,totalElements,totalPages}}, 스펙 §5)으로 직렬화한다.
 * 컨트롤러가 없는 본 이슈에선 규약만 고정하고, 실제 동작은 첫 도메인 컨트롤러(D 이슈)에서 발현된다.
 */
@Configuration(proxyBeanMethods = false)
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class WebConfig {}
```

- [ ] **Step 4: 컨텍스트 로드 회귀 확인 (WebConfig가 기존 컨텍스트를 깨지 않음)**

Run: `./gradlew test --tests 'com.elipair.church.ChurchBackendApplicationTests'`
Expected: **PASS** (Docker 필요). `@EnableSpringDataWebSupport`·`@EnableJpaAuditing` 추가 후에도 전체 애플리케이션 컨텍스트가 정상 로드됨을 확인한다.

- [ ] **Step 5: 포맷 후 커밋**

```bash
./gradlew spotlessApply
git add src/main/java/com/elipair/church/global/config/WebConfig.java \
        src/test/java/com/elipair/church/global/common/PagedModelSerializationTest.java
git commit -m "feat: 목록 응답 표준 PagedModel(VIA_DTO) 채택 (#3)"
```

---

## Task 4: 전체 검증 (빌드·품질 게이트·아키텍처)

**Files:** 없음(검증 전용)

- [ ] **Step 1: 포맷 적용**

Run: `./gradlew spotlessApply`
Expected: 변경 없음(각 작업에서 이미 적용). 변경이 생기면 `git add -A && git commit -m "style: spotless 포맷 적용 (#3)"`.

- [ ] **Step 2: 전체 빌드 실행**

Run: `./gradlew build`
Expected: **BUILD SUCCESSFUL** (Docker 필요). 포함 검증:
- `spotlessCheck` — palantir 포맷 통과
- 신규 테스트 3종 + 기존 테스트(컨텍스트 로드·Actuator·Swagger 토글) green
- `ArchitectureTest` — `global → domain` 의존 0 유지(본 이슈는 domain 미생성이라 자명 통과)
- `jacocoTestReport` 생성(강제 임계치 없음)

- [ ] **Step 3: 아키텍처 규칙 명시 확인**

Run: `./gradlew test --tests 'com.elipair.church.architecture.ArchitectureTest'`
Expected: **PASS**. 신규 `global` 코드 어디에서도 `domain`을 참조하지 않음(작성자 컬럼을 `Long`으로 둔 핵심 이유).

---

## Self-Review

**1. 스펙 coverage:**

| 설계 요구 | 구현 위치 |
|---|---|
| BaseEntity 2단(createdAt → +updatedAt·작성자·deletedAt·version) | Task 1 (BaseTimeEntity, BaseEntity) |
| JPA Auditing 활성화 + AuditorAware 스텁 | Task 1 (JpaConfig) |
| 작성자 = Long(global→domain 차단) | Task 1 (BaseEntity), Task 4 Step 3 검증 |
| 에러코드 8종 + INTERNAL_ERROR | Task 2 (ErrorCode) |
| RFC 7807 바디(errorCode·title·status·detail·instance·errors) | Task 2 (ErrorResponse) |
| BusinessException → ErrorCode 매핑 | Task 2 (BusinessException, 핸들러) |
| 검증 실패→400, 낙관락→409, 폴백→500 | Task 2 (GlobalExceptionHandler + 테스트) |
| 목록 응답 = PagedModel JSON 모양 | Task 3 (PagedModelSerializationTest, WebConfig) |
| 보안 401/403은 정의만·배선 #4 | Task 2 (ErrorCode에 상수만, 핸들러 미배선) |
| #4/#6 deferral(AuditorAware 값·references) | 범위 경계 절 + 코드 주석 |

**2. Placeholder scan:** 모든 스텝에 실제 코드/명령/기대 출력 포함. "TODO/적절히 처리" 류 없음.

**3. Type consistency:**
- `ErrorCode`: `getStatus()`/`getCode()`/`getTitle()` (Lombok @Getter) — `ErrorResponse`·핸들러에서 동일 사용.
- `ErrorResponse.ValidationError(field, reason)` — 핸들러에서 동일 시그니처 생성, 테스트는 `errors[0].field` 검증.
- `BusinessException.getErrorCode()` — 핸들러에서 사용.
- `BaseEntity` 게터: `getCreatedAt/getUpdatedAt/getVersion/getCreatedBy/getUpdatedBy/isDeleted` — 감사 테스트와 일치.
- `AuditorAware<Long>` 빈 이름 `auditorAware` ↔ `@EnableJpaAuditing(auditorAwareRef = "auditorAware")` 일치.
- `PagedModel(Page)` 생성자·`page`/`content`/`size`/`number`/`totalElements`/`totalPages` — 클래스패스(javap)로 확인됨.
