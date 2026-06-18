# 회원 이름·전화번호 검색 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /api/members`에 `?q=` 검색어를 추가해 관리자가 회원을 이름 또는 전화번호 부분일치로 찾을 수 있게 한다.

**Architecture:** 기존 `*Specifications` 패턴을 그대로 따른다 — `MemberSpecifications.filter(q)`가 `Specification<Member>`(항상 `deletedAt IS NULL`, null/blank q는 술어 제외)를 반환하고, `MemberRepository`가 `JpaSpecificationExecutor`를 상속한다. 서비스는 q 유무로 2-경로 분기(blank=기존 fetch-join 경로 무변경, 검색=Specification 경로). 전화 매칭은 숫자만 정규화 후 부분일치하며, q에 숫자가 없으면 전화 술어를 붙이지 않는다.

**Tech Stack:** Spring Boot 4 / Java 21 / Spring Data JPA Specification / JUnit5 + AssertJ + MockMvc / Testcontainers PostgreSQL.

**설계 문서:** `docs/superpowers/specs/2026-06-18-member-search-design.md`

---

## File Structure

- `src/main/java/com/elipair/church/domain/member/PhoneNumbers.java` — (수정) 비예외 헬퍼 `extractDigits` 추가, `normalize` 내부 정리.
- `src/main/java/com/elipair/church/domain/member/MemberSpecifications.java` — (신규) `filter(q)` 조건 빌더.
- `src/main/java/com/elipair/church/domain/member/MemberRepository.java` — (수정) `JpaSpecificationExecutor<Member>` 상속 추가.
- `src/main/java/com/elipair/church/domain/member/MemberService.java` — (수정) `list(q, pageable)` 분기.
- `src/main/java/com/elipair/church/domain/member/controller/MemberQueryController.java` — (수정) `@RequestParam(required=false) String q` 추가.
- `src/test/java/com/elipair/church/domain/member/PhoneNumbersTest.java` — (수정) `extractDigits` 테스트.
- `src/test/java/com/elipair/church/domain/member/MemberRepositoryTest.java` — (수정) Specification 검색 테스트.
- `src/test/java/com/elipair/church/domain/member/MemberAdminApiTest.java` — (수정) `?q=` API 테스트.

작업 순서는 컴파일 의존성에 따라 Task 1 → 2 → 3. (`MemberSpecifications`가 `extractDigits`를 쓰고, 서비스가 `MemberSpecifications`를 쓴다.)

---

## Task 1: PhoneNumbers.extractDigits (비예외 정규화 헬퍼)

**Files:**
- Modify: `src/main/java/com/elipair/church/domain/member/PhoneNumbers.java`
- Test: `src/test/java/com/elipair/church/domain/member/PhoneNumbersTest.java`

- [ ] **Step 1: Write the failing test**

`PhoneNumbersTest.java`의 클래스 본문(기존 `rejects_null_or_empty_after_strip` 테스트 아래, 닫는 `}` 직전)에 추가:

```java
    @Test
    void extract_digits_keeps_only_numbers() {
        assertThat(PhoneNumbers.extractDigits("010-1234-5678")).isEqualTo("01012345678");
        assertThat(PhoneNumbers.extractDigits("김철수")).isEmpty();
        assertThat(PhoneNumbers.extractDigits(null)).isEmpty();
        assertThat(PhoneNumbers.extractDigits("  ")).isEmpty();
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'com.elipair.church.domain.member.PhoneNumbersTest'`
Expected: 컴파일 실패 — `cannot find symbol: method extractDigits(...)`.

- [ ] **Step 3: Write minimal implementation**

`PhoneNumbers.java`의 `normalize` 메서드(현재 11-20행)를 아래로 교체. `extractDigits`를 추가하고 `normalize`가 이를 재사용하게 한다(공개 시그니처·예외 메시지 불변).

```java
    /** 숫자만 추출(비예외). 숫자가 없거나 null이면 빈 문자열. 검색 q→전화 부분일치에 사용. */
    public static String extractDigits(String raw) {
        return raw == null ? "" : raw.replaceAll("\\D", "");
    }

    public static String normalize(String raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "전화번호는 필수입니다");
        }
        String digits = extractDigits(raw);
        if (digits.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 전화번호입니다");
        }
        return digits;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'com.elipair.church.domain.member.PhoneNumbersTest'`
Expected: PASS (신규 `extract_digits_keeps_only_numbers` + 기존 `strips_non_digits`·`rejects_null_or_empty_after_strip` 모두 통과 — 회귀 없음).

- [ ] **Step 5: 커밋하지 않음**

이 작업에서는 태스크별 커밋을 하지 않는다(사용자 지시). 코드 작성 + 테스트 통과까지만. 커밋은 모든 태스크 완료 후 Final Verification에서 1회 일괄 수행한다.

---

## Task 2: MemberSpecifications.filter(q) + 리포지토리 Specification 지원

**Files:**
- Create: `src/main/java/com/elipair/church/domain/member/MemberSpecifications.java`
- Modify: `src/main/java/com/elipair/church/domain/member/MemberRepository.java`
- Test: `src/test/java/com/elipair/church/domain/member/MemberRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

`MemberRepositoryTest.java` 상단 import에 추가:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
```

클래스 본문(닫는 `}` 직전)에 추가:

```java
    @Test
    void search_by_name_substring_case_insensitive() {
        save("01011112222", "김철수");
        save("01033334444", "이영희");

        Page<Member> result = memberRepository.findAll(MemberSpecifications.filter("철수"), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Member::getName).containsExactly("김철수");
    }

    @Test
    void search_by_phone_with_hyphen_input() {
        save("01012345678", "김철수");
        save("01099998888", "이영희");

        Page<Member> result = memberRepository.findAll(MemberSpecifications.filter("010-1234"), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Member::getName).containsExactly("김철수");
    }

    @Test
    void search_name_only_query_skips_phone_predicate() {
        save("01012345678", "김철수");

        // "철"은 숫자가 없어 전화 술어가 붙지 않는다 → 이름으로만 매칭.
        Page<Member> result = memberRepository.findAll(MemberSpecifications.filter("철"), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Member::getName).containsExactly("김철수");
    }

    @Test
    void search_blank_query_returns_all_active() {
        save("01012345678", "김철수");
        save("01099998888", "이영희");

        Page<Member> result = memberRepository.findAll(MemberSpecifications.filter("  "), PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void search_excludes_soft_deleted() {
        Member m = save("01012345678", "김철수");
        m.softDelete();
        memberRepository.saveAndFlush(m);

        Page<Member> result = memberRepository.findAll(MemberSpecifications.filter("김철수"), PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'com.elipair.church.domain.member.MemberRepositoryTest'`
Expected: 컴파일 실패 — `MemberSpecifications` 클래스 없음 + `findAll(Specification, Pageable)` 미존재(`MemberRepository`가 아직 `JpaSpecificationExecutor` 미상속).

- [ ] **Step 3: Write minimal implementation**

(3a) 신규 파일 `MemberSpecifications.java` 생성:

```java
package com.elipair.church.domain.member;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * 회원 검색 동적 필터. null/blank q는 술어 제외. 항상 미삭제만(deletedAt IS NULL).
 * q는 이름(부분일치, 대소문자 무시) 또는 전화번호(숫자 정규화 후 부분일치)에 OR 매칭.
 * q에 숫자가 없으면 전화 술어를 붙이지 않는다(이름 검색의 전화 헛매칭 방지).
 */
final class MemberSpecifications {

    private MemberSpecifications() {}

    static Specification<Member> filter(String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (q != null && !q.isBlank()) {
                List<Predicate> match = new ArrayList<>();
                match.add(cb.like(cb.lower(root.get("name")), "%" + q.toLowerCase(Locale.ROOT) + "%"));
                String digits = PhoneNumbers.extractDigits(q);
                if (!digits.isEmpty()) {
                    match.add(cb.like(root.get("phone"), "%" + digits + "%"));
                }
                predicates.add(cb.or(match.toArray(new Predicate[0])));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

(3b) `MemberRepository.java` 수정 — 인터페이스 선언(현재 15행)에 `JpaSpecificationExecutor<Member>` 상속 추가하고 import 추가.

import 블록에 추가:
```java
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
```

인터페이스 선언 교체:
```java
public interface MemberRepository extends JpaRepository<Member, Long>, JpaSpecificationExecutor<Member> {
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'com.elipair.church.domain.member.MemberRepositoryTest'`
Expected: PASS (신규 검색 테스트 5건 + 기존 회원 리포지토리 테스트 모두 통과).

- [ ] **Step 5: 커밋하지 않음**

태스크별 커밋 금지(사용자 지시). 코드 작성 + 테스트 통과까지만.

---

## Task 3: 서비스 분기 + 컨트롤러 q 파라미터 배선

**Files:**
- Modify: `src/main/java/com/elipair/church/domain/member/MemberService.java:93-95`
- Modify: `src/main/java/com/elipair/church/domain/member/controller/MemberQueryController.java`
- Test: `src/test/java/com/elipair/church/domain/member/MemberAdminApiTest.java`

- [ ] **Step 1: Write the failing test**

`MemberAdminApiTest.java` 클래스 본문(닫는 `}` 직전)에 추가. (Korean q는 URL 인코딩 문제를 피하려 `.param(...)` 사용.)

```java
    @Test
    void search_members_by_name() throws Exception {
        persist("01011112222", "김철수");
        persist("01033334444", "이영희");

        mockMvc.perform(get("/api/members").param("q", "철수").header("Authorization", memberManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("김철수"));
    }

    @Test
    void search_members_by_phone_with_hyphen() throws Exception {
        persist("01012345678", "김철수");
        persist("01099998888", "이영희");

        mockMvc.perform(get("/api/members").param("q", "010-1234").header("Authorization", memberManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("김철수"));
    }

    @Test
    void search_without_permission_is_403() throws Exception {
        mockMvc.perform(get("/api/members").param("q", "철수").header("Authorization", plainUser()))
                .andExpect(status().isForbidden());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'com.elipair.church.domain.member.MemberAdminApiTest'`
Expected: 신규 검색 테스트 실패 — q가 무시되어 `totalElements`가 1이 아니라 2(전체 반환). (컨트롤러가 아직 q를 받지 않음.)

- [ ] **Step 3: Write minimal implementation**

(3a) `MemberService.java`의 `list` 메서드(현재 93-95행) 교체:

```java
    public Page<MemberCardResponse> list(String q, Pageable pageable) {
        Page<Member> page = (q == null || q.isBlank())
                ? memberRepository.findByDeletedAtIsNull(pageable)
                : memberRepository.findAll(MemberSpecifications.filter(q), pageable);
        return page.map(MemberCardResponse::from);
    }
```

(3b) `MemberQueryController.java` 수정.

import 블록에 추가:
```java
import org.springframework.web.bind.annotation.RequestParam;
```

`list` 핸들러(현재 37-40행)와 그 `@Operation`을 교체:
```java
    @Operation(summary = "교인 목록", description = """
                    전체 회원 카드 목록 조회(소프트 삭제 제외). 가입 승인·역할 관리용.

                    - 인증(JWT): 필요 — `MEMBER_MANAGE`
                    - 요청 파라미터: `q`(선택) — 이름 또는 전화번호 부분검색(없으면 전체) · `page`·`size`·`sort` — 페이지네이션
                    - 반환값: `Page<MemberCardResponse>` — uuid·이름·전화번호·직분·역할·`approved`(MEMBER 보유 여부)·createdAt 카드 목록
                    """)
    @GetMapping
    public Page<MemberCardResponse> list(@RequestParam(required = false) String q, Pageable pageable) {
        return service.list(q, pageable);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests 'com.elipair.church.domain.member.MemberAdminApiTest'`
Expected: PASS (신규 검색 3건 + 기존 `list_members_paginated_for_manager`·`list_members_without_permission_is_403` 등 모두 통과 — q 미지정 시 기존 동작 보존).

- [ ] **Step 5: 커밋하지 않음**

태스크별 커밋 금지(사용자 지시). 코드 작성 + 테스트 통과까지만.

---

## Final Verification

- [ ] **포맷 정리(spotless)**

Run: `./gradlew spotlessApply`
이유: 이 repo는 `com.diffplug.spotless` + `palantirJavaFormat`을 쓰며 `build`가 `spotlessCheck`를 포함한다. 포맷 불일치 시 빌드가 깨지므로 먼저 정렬한다.

- [ ] **전체 빌드 그린 확인**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — 전체 테스트 통과(컴파일·spotlessCheck·테스트 포함).

- [ ] **최종 일괄 커밋(1회)**

모든 태스크가 끝나고 빌드가 그린이면, 변경 소스/테스트를 한 번에 커밋한다(태스크 사이 커밋 없음 — 사용자 지시).

```bash
git add src/main/java/com/elipair/church/domain/member/PhoneNumbers.java \
        src/main/java/com/elipair/church/domain/member/MemberSpecifications.java \
        src/main/java/com/elipair/church/domain/member/MemberRepository.java \
        src/main/java/com/elipair/church/domain/member/MemberService.java \
        src/main/java/com/elipair/church/domain/member/controller/MemberQueryController.java \
        src/test/java/com/elipair/church/domain/member/PhoneNumbersTest.java \
        src/test/java/com/elipair/church/domain/member/MemberRepositoryTest.java \
        src/test/java/com/elipair/church/domain/member/MemberAdminApiTest.java
git commit -m "feat : 회원 이름·전화번호 검색(q) 기능 추가"
```

검색 동작 수동 확인(선택): 시드된 dev 프로필로 `GET /api/members?q=홍길동` 호출 시 이름 매칭 회원만 반환, `?q=0101` 호출 시 해당 전화 부분 매칭 회원만 반환, q 없으면 전체 목록.

---

## Self-Review (작성자 점검 결과)

1. **스펙 커버리지**
   - API 계약(`?q=`, 응답 불변): Task 3.
   - 검색 술어(이름 OR 전화, 숫자 없으면 전화 스킵, 미삭제만): Task 2 + 검증 테스트.
   - 전화 정규화 재사용(`extractDigits`): Task 1.
   - 2-경로 서비스 분기(blank 경로 무변경): Task 3 (3a).
   - 신규 인덱스/마이그레이션 없음: 추가 작업 없음(의도).
   - 에러처리(신규 코드 없음, 403은 기존 `@PreAuthorize`): Task 3 `search_without_permission_is_403`로 확인.
   - 테스트 3종(extractDigits 단위 / Specification 슬라이스 / 컨트롤러 슬라이스): Task 1·2·3.
2. **플레이스홀더 스캔**: 없음. 모든 코드 블록 실제 내용 포함.
3. **타입 일관성**: `MemberSpecifications.filter(String) → Specification<Member>` (Task 2 정의) ↔ `memberRepository.findAll(MemberSpecifications.filter(q), pageable)` (Task 2 테스트·Task 3 서비스) 일치. `PhoneNumbers.extractDigits(String) → String` (Task 1) ↔ `MemberSpecifications`에서 호출(Task 2) 일치. `service.list(String, Pageable)` (Task 3 서비스) ↔ 컨트롤러 호출(Task 3) 일치, 호출처는 컨트롤러 1곳뿐(직접 단위테스트 없음).
