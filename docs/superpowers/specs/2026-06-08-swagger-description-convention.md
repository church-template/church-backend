# Swagger @Operation description 보강 컨벤션 (#36)

전 도메인 컨트롤러 72개 엔드포인트의 `@Operation(description)`을 아래 구조화 포맷으로 통일·보강한다. summary는 기존 한 줄 유지, description만 교체.

## 포맷 (Java 텍스트블록 + 마크다운 불릿)

springdoc은 description을 Swagger UI에서 마크다운으로 렌더하므로 불릿/줄바꿈이 그대로 보인다. 각 description은 다음 구조를 따른다:

```
<한 줄 맥락 — 무엇을 하는 엔드포인트인지>

- 인증(JWT): <불필요 | 필요 — `권한명`>
- 경로 변수: `name` — 의미   (없으면 줄 생략)
- 요청 파라미터: `name` — 의미; ...   (없으면 줄 생략)
- 요청 본문: `DtoName` — 핵심 필드 요약   (본문 없으면 줄 생략)
- 반환값: `ResponseDto` — 무엇을 반환   (없으면 "없음(204)")
- 부수효과: <soft delete / 낙관락(version·409) / 차단형 삭제 / 캐시 무효화 등 동작상 주의점>   (없으면 줄 생략)
```

Java 작성 예:

```java
@Operation(summary = "설교 전체 수정", description = """
    설교 전체 교체(PUT). 제공한 값으로 모든 필드를 덮어쓴다.

    - 인증(JWT): 필요 — `SERMON_WRITE`
    - 경로 변수: `id` — 수정할 설교 ID
    - 요청 본문: `SermonUpdateRequest` — 제목·설교자·성경·본문·영상/오디오 URL·설교일·`tagIds`·`version`
    - 반환값: `SermonDetailResponse` — 수정된 설교 상세
    - 부수효과: `version` 불일치 시 409 OPTIMISTIC_LOCK_CONFLICT · `tagIds`로 태그 재연결
    """)
```

## 규칙

1. 정확성 우선 — 추측 금지. 각 엔드포인트의 실제 인증/파라미터/반환/부수효과를 알기 위해 컨트롤러 + 해당 서비스 메서드 + 요청/응답 DTO를 읽고 사실대로 기술한다.
2. 인증(JWT) 표기:
   - 공개(SecurityConfig `permitAll`) → `불필요`
   - 관리(`/api/admin/**`, 클래스 `@PreAuthorize`) → `필요 — `권한명``(예: SERMON_WRITE)
   - 갤러리 조회 → `필요 — 로그인 + `GALLERY_VIEW``
   - `/api/members/me` 등 본인 인증 → `필요 — 로그인(본인)`
3. 공통 에러 응답(400/401/403/404/409)은 OperationCustomizer가 이미 전역 주입하므로 description에 일반 에러코드를 나열하지 않는다. 단 도메인 고유 의미가 있는 것(409 MEDIA_IN_USE, 409 OPTIMISTIC_LOCK_CONFLICT, 409 DEPARTMENT_HAS_CHILDREN, 413 FILE_SIZE_EXCEEDED 등)은 "부수효과"에 한 줄로 적는다.
4. 카드 목록 응답은 "카드 메타만(본문 제외), 페이지네이션" 같은 특성을 반환값에 명시한다.
5. summary는 변경하지 않는다(기존 한 줄 유지). `@Tag`도 유지.
6. 코드 변경 금지 — 메서드 시그니처·로직·매핑·반환타입 불변. `@Operation`의 description 문자열과(필요 시) 텍스트블록 전환만. `io.swagger...Operation`은 이미 import됨.
7. 텍스트블록 들여쓰기는 spotless(palantir)가 정리하므로 자연스럽게 작성; 마지막에 `./gradlew spotlessApply`로 일괄 정리.

## 대상 인벤토리 (25 컨트롤러 / 72 엔드포인트) — 도메인별 그룹

- 그룹 A (인증·메인·회원): `auth/controller/AuthController`(4), `main/MainController`(1), `member/controller/MeController`(4), `MemberQueryController`(2), `MemberAdminController`(4), `AgreementAdminController`(1)
- 그룹 B (역할·직분·태그): `role/RoleController`(5), `role/PermissionController`(1), `position/PositionController`(4), `tag/TagController`(1), `tag/AdminTagController`(3)
- 그룹 C (설교·공지): `sermon/SermonController`(2), `sermon/AdminSermonController`(4), `notice/NoticeController`(2), `notice/AdminNoticeController`(4)
- 그룹 D (일정·부서): `event/EventController`(2), `event/AdminEventController`(4), `department/DepartmentController`(2), `department/AdminDepartmentController`(4)
- 그룹 E (미디어·갤러리·주보): `media/MediaController`(1), `media/AdminMediaController`(5), `gallery/GalleryAlbumController`(2), `gallery/AdminGalleryController`(5), `bulletin/BulletinController`(2), `bulletin/AdminBulletinController`(3)

## 검증

- `./gradlew compileJava` → `spotlessApply` → `./gradlew build`(전체 그린)
- 라이브: `GET /v3/api-docs`에서 대표 엔드포인트 description에 구조화 항목(인증/반환 등)이 포함됐는지 확인, Swagger UI(`/docs/swagger-ui.html`) 노출 확인
