# 회원 직분 부여·해제 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자가 회원에게 직분(목사·집사 등)을 지정/변경/해제하는 API를 추가한다.

**Architecture:** 회원 관리 영역에 per-action 전용 엔드포인트 `PUT /api/admin/members/{uuid}/position`을 추가한다. `MemberService.changePosition`이 직분을 조회해 기존 엔티티 메서드 `Member.changePosition(Position)`을 호출하고, `positionId: null`이면 직분을 해제한다. 직분은 권한과 분리된 축이라 위계 검증은 없다.

**Tech Stack:** Spring Boot 4 (Java 21), Spring Security `@PreAuthorize`, JPA, JUnit5 + MockMvc + Testcontainers(Postgres).

## Global Constraints

- 인가는 per-permission `@PreAuthorize("hasAuthority('MEMBER_MANAGE')")`. 직분은 권한과 분리 — priority/위계 검증 금지.
- 에러는 RFC 7807 단일 envelope. 미존재는 `ErrorCode.RESOURCE_NOT_FOUND`(404). 새 에러코드 만들지 말 것.
- 직분 미지정(해제) 표현은 `positionId: null`. DTO에 `@NotNull` 붙이지 말 것.
- 응답 DTO는 기존 `MemberDetailResponse` 재사용(직분은 `position` 필드=직분 한글 name 또는 null).
- 커밋 메시지: 한글 `<type> : <설명> #44`, Co-Authored-By/GPG 서명 금지, push 금지.
- 테스트 실행: `./gradlew test --tests 'com.elipair.church.domain.member.MemberPositionApiTest'`.

---

## File Structure

- Create: `src/main/java/com/elipair/church/domain/member/dto/PositionAssignRequest.java` — 요청 DTO(`Long positionId`, nullable).
- Modify: `src/main/java/com/elipair/church/domain/member/MemberService.java` — `PositionRepository` 의존성 + `changePosition(UUID, Long)` 메서드 추가.
- Modify: `src/main/java/com/elipair/church/domain/member/controller/MemberAdminController.java` — `PUT /{uuid}/position` 핸들러 추가.
- Create: `src/test/java/com/elipair/church/domain/member/MemberPositionApiTest.java` — 통합 테스트(직분 부여/변경/해제/404/403/자기지정).
- Modify: `docs/church-backend-spec.md` — §5.2에 엔드포인트 1줄 추가.
- 재사용(무변경): `Member.changePosition(Position)`(기존), `MemberDetailResponse.from(Member)`(기존), `PositionRepository`(기존 `JpaRepository<Position, Long>`).

---

### Task 1: 직분 부여·해제 엔드포인트 (DTO + 서비스 + 컨트롤러 + 해피패스 테스트)

**Files:**
- Create: `src/main/java/com/elipair/church/domain/member/dto/PositionAssignRequest.java`
- Modify: `src/main/java/com/elipair/church/domain/member/MemberService.java` (필드/생성자 + 신규 메서드)
- Modify: `src/main/java/com/elipair/church/domain/member/controller/MemberAdminController.java`
- Test: `src/test/java/com/elipair/church/domain/member/MemberPositionApiTest.java`

**Interfaces:**
- Consumes: `Member.changePosition(Position)`(기존 엔티티), `MemberDetailResponse.from(Member)`(기존), `PositionRepository.findById(Long)`(기존), `MemberService.findActiveByUuid(UUID)`(기존 private 헬퍼, 미존재 시 `RESOURCE_NOT_FOUND`).
- Produces: `MemberService.changePosition(UUID uuid, Long positionId) -> MemberDetailResponse`; 엔드포인트 `PUT /api/admin/members/{uuid}/position` (body `PositionAssignRequest{Long positionId}`).

- [ ] **Step 1: 테스트 파일 생성 (해피패스 실패 테스트)**

Create `src/test/java/com/elipair/church/domain/member/MemberPositionApiTest.java`:

```java
package com.elipair.church.domain.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.position.Position;
import com.elipair.church.domain.position.PositionRepository;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MemberPositionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PositionRepository positionRepository;

    @AfterEach
    void cleanup() {
        memberRepository.deleteAll(memberRepository.findAll()); // 회원 먼저(position FK 참조 해소)
        positionRepository.deleteAll();
    }

    private String memberManager() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(9L, UUID.randomUUID().toString(), "관리자", 900),
                        null,
                        List.of("MEMBER_MANAGE"));
    }

    private String plainUser() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(8L, UUID.randomUUID().toString(), "사용자", 0), null, List.of());
    }

    private Member persist(String phone, String name) {
        return memberRepository.saveAndFlush(Member.create(phone, name, "{enc}", null, null, true, true));
    }

    private Long positionId(String name, int sortOrder) {
        return positionRepository.saveAndFlush(Position.of(name, sortOrder)).getId();
    }

    @Test
    void assign_position_returns_detail_with_position() throws Exception {
        Member target = persist("01055556666", "직분대상");
        Long deacon = positionId("집사", 5);

        mockMvc.perform(put("/api/admin/members/" + target.getUuid() + "/position")
                        .header("Authorization", memberManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionId\":" + deacon + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value("집사"));
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.member.MemberPositionApiTest'`
Expected: 컴파일 실패 또는 404 — `PUT /{uuid}/position` 핸들러·DTO·서비스 메서드가 아직 없음.

- [ ] **Step 3: 요청 DTO 생성**

Create `src/main/java/com/elipair/church/domain/member/dto/PositionAssignRequest.java`:

```java
package com.elipair.church.domain.member.dto;

/** 회원 직분 지정/해제. positionId가 null이면 직분 해제(@NotNull 금지). */
public record PositionAssignRequest(Long positionId) {}
```

- [ ] **Step 4: MemberService에 PositionRepository 의존성 + changePosition 추가**

`MemberService.java` — import 2줄 추가(클래스 상단 import 블록):

```java
import com.elipair.church.domain.position.Position;
import com.elipair.church.domain.position.PositionRepository;
```

필드 추가(`private final RoleHierarchyValidator hierarchyValidator;` 아래):

```java
    private final PositionRepository positionRepository;
```

생성자 시그니처/본문에 파라미터 추가(기존 5개 → 6개):

```java
    public MemberService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenStore refreshTokenStore,
            AccessTokenBlacklister accessTokenBlacklister,
            RoleHierarchyValidator hierarchyValidator,
            PositionRepository positionRepository) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenStore = refreshTokenStore;
        this.accessTokenBlacklister = accessTokenBlacklister;
        this.hierarchyValidator = hierarchyValidator;
        this.positionRepository = positionRepository;
    }
```

메서드 추가(`detail(UUID uuid)` 메서드 근처, 클래스 내부). 클래스 기본이 `@Transactional(readOnly = true)`이므로 쓰기 트랜잭션을 명시:

```java
    @Transactional
    public MemberDetailResponse changePosition(UUID uuid, Long positionId) {
        Member member = findActiveByUuid(uuid); // 미존재/탈퇴 → 404
        Position position = positionId == null
                ? null
                : positionRepository
                        .findById(positionId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)); // 미존재 직분 → 404
        member.changePosition(position); // null이면 직분 해제
        return MemberDetailResponse.from(member);
    }
```

- [ ] **Step 5: MemberAdminController에 PUT /{uuid}/position 추가**

`MemberAdminController.java` — import 2줄 추가:

```java
import com.elipair.church.domain.member.dto.PositionAssignRequest;
import org.springframework.web.bind.annotation.PutMapping;
```

핸들러 추가(클래스 내부, 기존 핸들러 사이/끝):

```java
    @Operation(summary = "직분 부여/해제", description = """
                    대상 회원의 직분을 지정·변경하거나 해제한다(`positionId`가 null이면 해제).

                    - 인증(JWT): 필요 — `MEMBER_MANAGE`
                    - 경로 변수: `uuid` — 대상 회원 uuid
                    - 요청 본문: `PositionAssignRequest` — `positionId`(null이면 직분 해제)
                    - 반환값: `MemberDetailResponse` — 직분 반영된 회원 상세
                    - 부수효과: 직분은 권한과 무관(위계 검증 없음) · 회원/직분 미존재 시 404
                    """)
    @PutMapping("/{uuid}/position")
    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    public MemberDetailResponse changePosition(
            @PathVariable UUID uuid, @RequestBody PositionAssignRequest request) {
        return memberService.changePosition(uuid, request.positionId());
    }
```

- [ ] **Step 6: 테스트 실행 → 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.member.MemberPositionApiTest'`
Expected: PASS (`assign_position_returns_detail_with_position`).

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/elipair/church/domain/member/dto/PositionAssignRequest.java \
        src/main/java/com/elipair/church/domain/member/MemberService.java \
        src/main/java/com/elipair/church/domain/member/controller/MemberAdminController.java \
        src/test/java/com/elipair/church/domain/member/MemberPositionApiTest.java
git commit --no-gpg-sign -m "feat : 회원 직분 부여·해제 API #44"
```

---

### Task 2: 엣지 케이스 (해제·404·403·자기지정)

**Files:**
- Test: `src/test/java/com/elipair/church/domain/member/MemberPositionApiTest.java` (테스트 메서드 추가)

**Interfaces:**
- Consumes: Task 1의 `PUT /api/admin/members/{uuid}/position`, `MemberService.changePosition`.
- Produces: 없음(행위 고정 테스트).

- [ ] **Step 1: 엣지 케이스 테스트 5건 추가**

`MemberPositionApiTest`에 메서드 추가(클래스 내부, 해피패스 테스트 아래). 상단 import에 다음 2줄 추가:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import com.elipair.church.domain.role.Role;
```

(참고: `get`은 해제 후 재조회 검증에 쓰지 않으면 생략 가능. 아래 테스트는 응답 본문으로만 검증하므로 `get` import는 불필요하면 추가하지 말 것. `Role` import도 아래 테스트에서 쓰지 않으면 추가하지 말 것.)

```java
    @Test
    void clear_position_with_null_returns_detail_without_position() throws Exception {
        Member target = persist("01066667777", "해제대상");
        Long elder = positionId("장로", 3);
        // 먼저 직분 부여
        mockMvc.perform(put("/api/admin/members/" + target.getUuid() + "/position")
                        .header("Authorization", memberManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionId\":" + elder + "}"))
                .andExpect(status().isOk());
        // null로 해제
        mockMvc.perform(put("/api/admin/members/" + target.getUuid() + "/position")
                        .header("Authorization", memberManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void assign_nonexistent_position_is_404() throws Exception {
        Member target = persist("01077778888", "대상");

        mockMvc.perform(put("/api/admin/members/" + target.getUuid() + "/position")
                        .header("Authorization", memberManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void assign_position_to_nonexistent_member_is_404() throws Exception {
        Long deacon = positionId("집사", 5);

        mockMvc.perform(put("/api/admin/members/" + UUID.randomUUID() + "/position")
                        .header("Authorization", memberManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionId\":" + deacon + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void assign_position_without_member_manage_is_403() throws Exception {
        Member target = persist("01088889999", "대상");
        Long deacon = positionId("집사", 5);

        mockMvc.perform(put("/api/admin/members/" + target.getUuid() + "/position")
                        .header("Authorization", plainUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionId\":" + deacon + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void assign_own_position_is_allowed() throws Exception {
        // 직분은 권한과 무관하므로 자기 자신에게도 지정 가능(self-protection 없음).
        Member self = persist("01099990000", "본인");
        Long pastor = positionId("목사", 1);

        mockMvc.perform(put("/api/admin/members/" + self.getUuid() + "/position")
                        .header("Authorization",
                                "Bearer " + provider.issueAccess(
                                        new MemberPrincipal(self.getId(), self.getUuid().toString(), "본인", 900),
                                        null, List.of("MEMBER_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionId\":" + pastor + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value("목사"));
    }
```

- [ ] **Step 2: 테스트 실행 → 전부 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.member.MemberPositionApiTest'`
Expected: PASS (해피패스 1 + 엣지 5 = 6건). (구현은 Task 1에서 이미 모든 분기를 처리하므로 추가 구현 없이 통과해야 한다. 실패하면 Task 1 로직을 점검.)

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/com/elipair/church/domain/member/MemberPositionApiTest.java
git commit --no-gpg-sign -m "test : 회원 직분 부여·해제 엣지 케이스(해제·404·403·자기지정) #44"
```

---

### Task 3: 스펙 문서 갱신

**Files:**
- Modify: `docs/church-backend-spec.md` (§5.2 회원 관리 섹션)

**Interfaces:**
- Consumes: 없음. Produces: 없음(문서).

- [ ] **Step 1: §5.2에 엔드포인트 추가**

`docs/church-backend-spec.md`의 회원 관리(§5.2) 엔드포인트 목록에 다음 줄을 추가(인접 엔드포인트 서술 형식에 맞춰):

```
- `PUT /api/admin/members/{uuid}/position`(`MEMBER_MANAGE`) → 회원 직분 지정/변경/해제. body `{positionId}`(null이면 해제). 직분은 권한과 분리되어 위계 검증 없음. 미존재 회원/직분 → 404. 응답 `MemberDetailResponse`.
```

(정확한 삽입 위치: §5.2에서 역할 부여/회수(`/{uuid}/roles`) 또는 비밀번호 초기화(`/{uuid}/reset-password`) 항목 바로 아래. 해당 섹션을 열어 인접 형식을 확인 후 한 줄 추가.)

- [ ] **Step 2: 전체 테스트 회귀 확인**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (기존 + 신규 6건 모두 통과).

- [ ] **Step 3: 커밋**

```bash
git add docs/church-backend-spec.md
git commit --no-gpg-sign -m "docs : 회원 직분 부여·해제 엔드포인트 스펙 반영 #44"
```

---

## Self-Review

- **Spec coverage:** 엔드포인트(§2)=Task1, 컴포넌트(§3)=Task1, 서비스 로직/데이터흐름(§4)=Task1 Step4, 가드/에러(§5)=Task1(로직)+Task2(테스트), 테스트(§6)=Task1+Task2, 문서(§7)=Task3, 범위외(§8)=계획에 미포함(준수). 누락 없음.
- **Placeholder scan:** 모든 코드 스텝에 실제 코드 포함, "TBD/TODO" 없음. Task2 Step1의 import 안내는 "쓰지 않으면 추가하지 말 것"으로 명확화(미사용 import 경고 회피).
- **Type consistency:** `changePosition(UUID, Long)` 시그니처가 컨트롤러 호출(`memberService.changePosition(uuid, request.positionId())`)과 일치. `PositionAssignRequest.positionId()` 접근자(record) 일치. `Position.of(String, Integer)`·`PositionRepository.findById(Long)`·`Member.changePosition(Position)`·`MemberDetailResponse.from(Member)` 모두 기존 시그니처와 일치.

## 도커 반영(구현 후)

코드/마이그레이션이 아닌 순수 코드 변경이므로, 로컬 확인은 `docker compose up -d --build backend`로 재빌드 후 `/v3/api-docs`에 `PUT /api/admin/members/{uuid}/position`이 노출되는지 확인(프론트 타입 수급용).
