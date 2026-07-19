# 설교 회원전용 전환 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 설교 조회 API(`GET /api/sermons`, `GET /api/sermons/{id}`)를 신설 권한 `SERMON_VIEW` 기준 회원전용으로 전환한다.

**Architecture:** 갤러리(`GALLERY_VIEW`)·성경통독(`CHALLENGE_PARTICIPATE`)이 쓰는 기존 패턴을 그대로 답습한다 — (1) Flyway 마이그레이션으로 `SERMON_VIEW` 권한을 신설하고 `SUPER_ADMIN`·`ADMIN`·`MEMBER`에 부여, (2) `SecurityConfig`에 `/api/sermons/**` → `hasAuthority("SERMON_VIEW")` 매처를 `anyRequest().permitAll()` 앞에 추가. 에러 시맨틱(401/403)은 기존 `JwtAuthenticationEntryPoint`/`JwtAccessDeniedHandler`가 자동 처리하므로 신규 인프라 코드는 없다.

**Tech Stack:** Spring Boot 4.0.6 / Java 21 / Spring Security / Flyway(PostgreSQL) / JUnit5 + MockMvc + Testcontainers.

## Global Constraints

- 커밋 컨벤션: `<type> : <설명> #53` (콜론 앞 공백, 한국어, 이슈번호 #53 부착). **Co-Authored-By 금지.**
- `version.yml` / `build.gradle` version / `CHANGELOG.*` 는 자동화 소유 — 손대지 않는다.
- 마이그레이션은 append-only. 최신 = `V14`, 신규 = **`V15`**. 기존 마이그레이션 파일 수정 금지.
- SB4 스타터 명(`spring-boot-starter-webmvc`, `*-test`)은 이미 배선됨 — 의존성 추가 없음.
- 권한/역할 이름은 코드-facing English(`SERMON_VIEW`), 설명은 한국어.
- push 금지 · 커밋은 사용자 요청 시에만. (계획의 커밋 스텝은 실행 단계용 지침이다.)

---

## Task 1: `SERMON_VIEW` 권한 시드(V15) + DB 시드 단언 테스트 갱신

`SERMON_VIEW`를 16번째 권한으로 신설하고 `SUPER_ADMIN`·`ADMIN`·`MEMBER`에 부여한다. DB 시드를 하드코딩으로 단언하던 4개 테스트(권한 개수·역할 권한 집합)를 16종/신규 집합에 맞춘다.

**Files:**
- Create: `src/main/resources/db/migration/V15__sermon_view_permission.sql`
- Modify: `src/test/java/com/elipair/church/domain/role/RbacSeedIntegrityTest.java`
- Modify: `src/test/java/com/elipair/church/domain/role/PermissionApiTest.java`
- Modify: `src/test/java/com/elipair/church/domain/role/RoleApiTest.java`
- Modify: `src/test/java/com/elipair/church/domain/member/MeApiTest.java`

**Interfaces:**
- Consumes: 기존 스키마 `permissions(name, description)`, `roles(name)`, `role_permissions(role_id, permission_id)` (V2 정의).
- Produces: 권한명 문자열 `"SERMON_VIEW"` — Task 2의 `SecurityConfig` 매처와 `SermonApiTest` 토큰이 이 문자열을 사용한다. 시드 후 `MEMBER` 권한 집합 = `{GALLERY_VIEW, CHALLENGE_PARTICIPATE, SERMON_VIEW}`, `SUPER_ADMIN`/`ADMIN` = 전 16권한.

- [ ] **Step 1: 시드 단언 테스트를 16종/신규 집합으로 갱신 (RED)**

`RbacSeedIntegrityTest.java` — 메서드 `seeds_fifteen_permissions()`를 아래로 교체(개명 + `SERMON_VIEW` 추가):

```java
    @Test
    void seeds_sixteen_permissions() {
        assertThat(permissionRepository.findAllByOrderByNameAsc())
                .extracting(Permission::getName)
                .containsExactlyInAnyOrder(
                        "SERMON_WRITE",
                        "NOTICE_WRITE",
                        "EVENT_WRITE",
                        "DEPT_WRITE",
                        "MEMBER_MANAGE",
                        "ROLE_MANAGE",
                        "POSITION_MANAGE",
                        "MEDIA_MANAGE",
                        "TAG_MANAGE",
                        "GALLERY_WRITE",
                        "GALLERY_VIEW",
                        "BULLETIN_WRITE",
                        "CHALLENGE_MANAGE",
                        "CHALLENGE_PARTICIPATE",
                        "INQUIRY_MANAGE",
                        "SERMON_VIEW");
    }
```

`RbacSeedIntegrityTest.java` — 메서드 `role_permission_matrix_matches_spec()`의 세 단언을 교체:

```java
        assertThat(byName.get("SUPER_ADMIN").getPermissions()).hasSize(16);
        assertThat(byName.get("ADMIN").getPermissions()).hasSize(16);
        assertThat(byName.get("MEMBER").getPermissions())
                .extracting(Permission::getName)
                .containsExactlyInAnyOrder("GALLERY_VIEW", "CHALLENGE_PARTICIPATE", "SERMON_VIEW");
```

`PermissionApiTest.java` — 메서드 `lists_fifteen_permissions_sorted_by_name()`를 아래로 교체(개명 + `.value(16)`; `$[0].name`은 알파벳 첫 항목이라 `BULLETIN_WRITE` 그대로):

```java
    @Test
    void lists_sixteen_permissions_sorted_by_name() throws Exception {
        mockMvc.perform(get("/api/admin/permissions").header("Authorization", roleManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(16)) // V13 CHALLENGE_* 2건 + V14 INQUIRY_MANAGE + V15 SERMON_VIEW 포함
                .andExpect(jsonPath("$[0].name").value("BULLETIN_WRITE")); // name ASC 첫 항목
    }
```

`RoleApiTest.java` — 메서드 `lists_seed_roles_priority_desc_with_permissions()`의 permissions 길이 단언을 교체:

```java
                .andExpect(
                        jsonPath("$[0].permissions.length()").value(16)) // V13 2건 + V14 INQUIRY_MANAGE + V15 SERMON_VIEW 포함
```

`MeApiTest.java` — 메서드 `me_returns_db_latest_with_roles_and_permissions()`의 permissions 집합 단언을 교체:

```java
                // V15 이후 MEMBER = GALLERY_VIEW + CHALLENGE_PARTICIPATE + SERMON_VIEW, 순서는 계약이 아니라 전체 집합으로 단언
                .andExpect(jsonPath(
                        "$.permissions", containsInAnyOrder("GALLERY_VIEW", "CHALLENGE_PARTICIPATE", "SERMON_VIEW")))
```

- [ ] **Step 2: 실패 확인 (RED)**

Run: `./gradlew test --tests '*RbacSeedIntegrityTest' --tests '*PermissionApiTest' --tests '*RoleApiTest' --tests '*MeApiTest'`
Expected: FAIL — 현재 DB에 권한 15종·MEMBER에 `SERMON_VIEW` 없음(단언 개수 16/신규 집합 불일치).

- [ ] **Step 3: V15 마이그레이션 추가 (GREEN 구현)**

Create `src/main/resources/db/migration/V15__sermon_view_permission.sql`:

```sql
-- 설교 회원전용 전환: SERMON_VIEW 신설 (V13 패턴).
-- 관리자에겐 조회+쓰기 모두, MEMBER(승인 교인)에겐 조회만.
INSERT INTO permissions (name, description) VALUES
    ('SERMON_VIEW', '설교 조회(회원 전용 열람)');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r
JOIN permissions p ON p.name = 'SERMON_VIEW'
WHERE r.name IN ('SUPER_ADMIN', 'ADMIN', 'MEMBER');
```

- [ ] **Step 4: 통과 확인 (GREEN)**

Run: `./gradlew test --tests '*RbacSeedIntegrityTest' --tests '*PermissionApiTest' --tests '*RoleApiTest' --tests '*MeApiTest'`
Expected: PASS — 4개 테스트 클래스 전부 그린(권한 16종, MEMBER 3권한).

- [ ] **Step 5: 커밋**

```bash
git add src/main/resources/db/migration/V15__sermon_view_permission.sql \
        src/test/java/com/elipair/church/domain/role/RbacSeedIntegrityTest.java \
        src/test/java/com/elipair/church/domain/role/PermissionApiTest.java \
        src/test/java/com/elipair/church/domain/role/RoleApiTest.java \
        src/test/java/com/elipair/church/domain/member/MeApiTest.java
git commit -m "feat : 설교 회원전용용 SERMON_VIEW 권한 신설·시드 #53"
```

---

## Task 2: `/api/sermons/**` 회원전용 게이트 + `SermonApiTest` 전환 + 문서 갱신

`SecurityConfig`에 설교 조회 인가 매처를 추가하고, 무인증으로 설교 GET을 호출하던 `SermonApiTest`를 `SERMON_VIEW` 뷰어 토큰 기반으로 전환하며 401/403 케이스를 추가한다. 컨트롤러·시큐리티 문서 문구를 회원전용으로 갱신한다(OpenAPI 신호).

**Files:**
- Modify: `src/main/java/com/elipair/church/global/config/SecurityConfig.java`
- Modify: `src/main/java/com/elipair/church/domain/sermon/SermonController.java`
- Test: `src/test/java/com/elipair/church/domain/sermon/SermonApiTest.java`

**Interfaces:**
- Consumes: Task 1의 권한명 `"SERMON_VIEW"`. `SermonApiTest`의 기존 헬퍼 `token(Long memberId, String permission)`(maxPriority 1000, `List.of(permission)`으로 액세스 토큰 발급), 필드 `authorId`.
- Produces: 없음(정책 변경 종착). `/api/sermons/**`는 이후 `SERMON_VIEW` 보유자만 200, 무인증 401, 권한없음 403.

- [ ] **Step 1: `SermonApiTest`에 뷰어 토큰 헬퍼 추가 + 신규 401/403 케이스 + 공개 GET 전환 (RED)**

`SermonApiTest.java` — `adminToken()` 헬퍼(67–69줄) 바로 아래에 뷰어 토큰 헬퍼 추가:

```java
    private String viewerToken() {
        return token(authorId, "SERMON_VIEW");
    }
```

`SermonApiTest.java` — 신규 인가 테스트 2건 추가(클래스 내 아무 위치, 예: `detail_unknown_is_404` 뒤):

```java
    @Test
    void list_anonymous_is_401() throws Exception {
        mockMvc.perform(get("/api/sermons"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void list_without_sermon_view_is_403() throws Exception {
        mockMvc.perform(get("/api/sermons").header("Authorization", token(authorId, "MEDIA_MANAGE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
```

`SermonApiTest.java` — 기존 공개 GET 테스트 3건을 개명 + 뷰어 토큰 부착으로 교체:

메서드 `public_list_paginates_and_omits_content` → 아래로 교체:

```java
    @Test
    void members_list_paginates_and_omits_content() throws Exception {
        createSermon();
        createSermon();

        mockMvc.perform(get("/api/sermons").header("Authorization", viewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].title").exists())
                .andExpect(jsonPath("$.content[0].author").value("관리목사"))
                .andExpect(jsonPath("$.content[0].content").doesNotExist());
    }
```

메서드 `public_list_filters_by_preacher` → 아래로 교체:

```java
    @Test
    void members_list_filters_by_preacher() throws Exception {
        createSermon(); // 김목사
        mockMvc.perform(get("/api/sermons").param("preacher", "김목사").header("Authorization", viewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mockMvc.perform(get("/api/sermons").param("preacher", "없는목사").header("Authorization", viewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }
```

메서드 `public_detail_increments_view_count` → 아래로 교체:

```java
    @Test
    void members_detail_increments_view_count() throws Exception {
        long id = createSermon();

        mockMvc.perform(get("/api/sermons/" + id).header("Authorization", viewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(1));
        mockMvc.perform(get("/api/sermons/" + id).header("Authorization", viewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(2));
    }
```

`SermonApiTest.java` — 나머지 3개 GET 호출에 뷰어 토큰 부착(메서드명 유지):

`detail_unknown_is_404` 안:

```java
        mockMvc.perform(get("/api/sermons/999999").header("Authorization", viewerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
```

`delete_soft_deletes_then_detail_404` 안, 삭제 후 상세 조회 줄:

```java
        mockMvc.perform(get("/api/sermons/" + id).header("Authorization", viewerToken()))
                .andExpect(status().isNotFound());
```

`author_is_masked_when_member_withdrawn` 안:

```java
        mockMvc.perform(get("/api/sermons/" + id).header("Authorization", viewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("(탈퇴한 사용자)"));
```

- [ ] **Step 2: 실패 확인 (RED)**

Run: `./gradlew test --tests '*SermonApiTest'`
Expected: FAIL — `list_anonymous_is_401`(현재 permitAll이라 200)·`list_without_sermon_view_is_403`(현재 200) 2건 실패. 전환된 뷰어 토큰 GET들은 permitAll 하에서도 200이라 통과(회귀 가드).

- [ ] **Step 3: `SecurityConfig`에 설교 인가 매처 추가 (GREEN 구현)**

`SecurityConfig.java` — `.requestMatchers("/api/bible-challenges/**").hasAuthority("CHALLENGE_PARTICIPATE")` 와 `.anyRequest().permitAll()` **사이에** 삽입:

```java
                        .requestMatchers("/api/sermons/**")
                        .hasAuthority("SERMON_VIEW")
```

삽입 후 해당 구간은 다음과 같아야 한다:

```java
                        .requestMatchers("/api/bible-challenges/**")
                        .hasAuthority("CHALLENGE_PARTICIPATE")
                        .requestMatchers("/api/sermons/**")
                        .hasAuthority("SERMON_VIEW")
                        .anyRequest()
                        .permitAll())
```

- [ ] **Step 4: 통과 확인 (GREEN)**

Run: `./gradlew test --tests '*SermonApiTest'`
Expected: PASS — 무인증 401·권한없음 403·`SERMON_VIEW` 200(목록/상세/viewCount 증가)·어드민 쓰기 회귀 없음.

- [ ] **Step 5: 컨트롤러·시큐리티 문서 문구 회원전용으로 갱신**

`SecurityConfig.java` — 클래스 Javadoc(27–30줄)을 아래로 교체:

```java
/**
 * 경로 인가(스펙 §4.3): /api/admin/** 인증(세부 권한은 메서드 @PreAuthorize), /api/gallery/** GALLERY_VIEW,
 * /api/bible-challenges/** CHALLENGE_PARTICIPATE, /api/sermons/** SERMON_VIEW(회원 전용), 그 외 공개.
 * JWT 필터·인증 실패 RFC 7807 변환·CORS·메서드 보안을 배선한다.
 */
```

`SermonController.java` — 클래스 Javadoc(19줄)·`@Tag`(20줄)을 교체:

```java
/** 설교 회원전용 조회 API(스펙 §5.5). SecurityConfig에서 SERMON_VIEW 요구. */
@Tag(name = "설교", description = "설교 회원전용 조회/관리 API(스펙 §5.5)")
```

`SermonController.java` — 목록 `@Operation`(30–36줄)의 summary·인증 문구:

```java
    @Operation(summary = "설교 목록(회원전용)", description = """
                    설교 카드 목록을 필터·검색·페이지네이션으로 조회한다.

                    - 인증(JWT): 필요 — `SERMON_VIEW` (회원 전용)
                    - 요청 파라미터: `preacher`·`series` — 설교자/시리즈 필터; `from`·`to` — 설교일 범위(yyyy-MM-dd); `q` — 제목/내용 검색어; `tagId` — 태그 필터; `page`·`size`·`sort` — 페이지네이션(기본 `preachedAt,desc`)
                    - 반환값: `Page<SermonCardResponse>` — 카드 메타만(본문 `content` 제외)·페이지네이션
                    """)
```

`SermonController.java` — 상세 `@Operation`(55–62줄)의 summary·인증 문구:

```java
    @Operation(summary = "설교 상세(회원전용)", description = """
                    설교 한 건의 상세를 조회한다(본문·태그·`version` 포함).

                    - 인증(JWT): 필요 — `SERMON_VIEW` (회원 전용)
                    - 경로 변수: `id` — 조회할 설교 ID
                    - 반환값: `SermonDetailResponse` — 본문 `content` 포함 상세
                    - 부수효과: 조회수 버퍼 +1(버퍼 누적분을 합산해 응답)
                    """)
```

- [ ] **Step 6: 문서 갱신 후 회귀 확인**

Run: `./gradlew test --tests '*SermonApiTest' --tests '*OpenApiOperationCustomizerTest'`
Expected: PASS — 문구 변경은 동작 무영향, OpenAPI 400 주입 단언(`/api/sermons` get 400) 유지.

- [ ] **Step 7: 전체 스위트 그린 확인**

Run: `./gradlew test`
Expected: PASS — 전 도메인 회귀 없음.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/elipair/church/global/config/SecurityConfig.java \
        src/main/java/com/elipair/church/domain/sermon/SermonController.java \
        src/test/java/com/elipair/church/domain/sermon/SermonApiTest.java
git commit -m "feat : 설교 조회를 SERMON_VIEW 회원전용으로 게이트 #53"
```

---

## Task 3: OpenAPI 재생성분 프론트 핸드오프 (수동)

백엔드 코드 변경으로 설교 조회의 인증 문구가 갱신됐다. church-frontend가 `docs/api-docs.json`을 교체할 수 있도록 재생성 스펙을 전달한다. (리포에 커밋된 산출물은 없음 — 런타임 `/v3/api-docs` 생성.)

**Files:** 없음(코드 산출물 아님).

- [ ] **Step 1: 앱 기동 후 OpenAPI 스펙 수집**

Run(별도 터미널에서 앱 기동 후): `curl -s http://localhost:8080/v3/api-docs -o /tmp/api-docs.json`
Expected: `paths['/api/sermons'].get`·`paths['/api/sermons/{id}'].get`의 description에 "인증(JWT): 필요 — SERMON_VIEW (회원 전용)" 문구 포함, summary에 "(회원전용)" 부기 확인.

- [ ] **Step 2: 프론트 전달**

수집한 `api-docs.json`을 church-frontend에 전달 → 프론트가 `church-frontend/docs/api-docs.json` 교체. (백엔드 작업 범위 종료.)

---

## Self-Review

**Spec coverage** (스펙 `2026-07-19-sermon-member-only-design.md` 대비):
- §3.1 V15 마이그레이션 → Task 1 Step 3 ✓
- §3.2 SecurityConfig 매처 → Task 2 Step 3 ✓; 클래스 Javadoc → Task 2 Step 5 ✓
- §3.3 SermonController 문구·summary → Task 2 Step 5 ✓
- §4.1 카운트 단언 5곳(RbacSeedIntegrity ×2, PermissionApi, RoleApi, MeApi) → Task 1 Step 1 ✓
- §4.2 SermonApiTest viewerToken·6건 전환·개명·401/403 신규 → Task 2 Step 1 ✓
- §5 검수 1~4(401/403/200/viewCount) → Task 2 Step 4; 검수 5(members/me) → Task 1 Step 1(MeApiTest); 검수 6(어드민 회귀) → Task 2 Step 4·7; 검수 7(전체 그린) → Task 2 Step 7; 검수 8(OpenAPI) → Task 3 ✓
- §6 변경 불필요(MigrationIndexTest·OpenApiOperationCustomizerTest·버전파일) → 어느 태스크도 손대지 않음 ✓ (OpenApiOperationCustomizerTest는 Task 2 Step 6에서 그린 확인만)

**Placeholder scan:** TBD/TODO/"적절히 처리" 없음 — 모든 코드 스텝은 실제 코드 블록. ✓

**Type consistency:** `viewerToken()` = `token(authorId, "SERMON_VIEW")` (기존 헬퍼 시그니처 `token(Long, String)` 재사용). 권한 문자열 `"SERMON_VIEW"`가 V15 시드·SecurityConfig 매처·토큰 발급에서 일관. 카운트 15→16이 4개 파일에서 일관. ✓
