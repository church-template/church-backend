# Notice 도메인 설계 리뷰

대상: `docs/superpowers/specs/2026-06-06-notice-domain-design.md`

## 전체 평가

~~Request Changes~~ → **해결 완료 (Resolved)** — 아래 Major 2 · Minor 1을 모두 구현에 반영함.

- Critical: 0
- Major: 2 → **0** (반영)
- Minor: 1 → **0** (반영)

> **반영 요약 (구현 시점):**
> - **Major(stale version):** `NoticeService.update/patch`에 변경 직후 `repository.flush()` 추가 → 응답 `version`이 post-increment. 회귀 가드: 단위(`verify(repository).flush()` / stale 경로 `never().flush()`) + E2E `patch_response_version_allows_immediate_next_edit`. 동일 결함이던 `SermonService`도 함께 수정(`fix : 설교 수정응답 version flush 정합 #13`).
> - **Major(부분 인덱스 검증):** `NoticeRepositoryTest`는 인덱스 검증을 주장하지 않도록 정정하고, **별도 `MigrationIndexTest`**(Flyway-on + `pg_indexes`)로 `idx_notices_pinned_created`·`idx_sermons_preached_at`·`uq_members_phone_active`의 부분 조건(`WHERE deleted_at IS NULL`)을 검증.
> - **Minor(조회수 0-row UPDATE→404):** 의도된 trade-off로 설계 문서 §5에 명시.

## Major

### `docs/superpowers/specs/2026-06-06-notice-domain-design.md:119`

PATCH 응답의 `version`이 stale 값으로 내려갈 수 있다.

설계는 `patch`에서 `tagIds`가 제공된 경우에만 `replaceLinks`를 호출하고 바로 detail 응답을 만든다. 기존 `SermonService`도 같은 구조이며, DTO 생성 시 `s.getVersion()`을 태그/작성자 조회보다 먼저 읽는다. JPA `@Version` 증가는 flush 시점에 반영되므로, 태그 변경이 없는 PATCH는 응답에 이전 version을 담을 가능성이 있다.

영향: 클라이언트가 PATCH 응답의 version을 그대로 다음 PUT/PATCH에 보내면 즉시 409가 날 수 있다. `NoticeDetailResponse`가 "편집 재전송용 version"이라고 명시한 계약과 충돌한다.

제안: 수정 응답을 만들기 전 명시적으로 flush/reload 하거나, 서비스 메서드가 저장소 flush 후 재조회한 엔티티로 detail을 만들도록 설계에 명시한다. 테스트에는 "PATCH(tagIds 미제공) 응답 version이 증가하고, 그 version으로 즉시 다음 수정이 성공한다" 케이스를 추가한다.

### `docs/superpowers/specs/2026-06-06-notice-domain-design.md:162`

부분 인덱스 테스트 계획이 기존 repository 테스트 패턴과 맞지 않는다.

문서는 `NoticeRepositoryTest(@DataJpaTest 슬라이스)`에서 부분 인덱스를 검증한다고 되어 있다. 하지만 현재 `SermonRepositoryTest`는 `spring.flyway.enabled=false`, `spring.jpa.hibernate.ddl-auto=create-drop` 패턴이라 Flyway 마이그레이션으로 만든 부분 인덱스가 테스트 DB에 생성되지 않는다.

영향: 계획대로 구현하면 `idx_notices_pinned_created ... WHERE deleted_at IS NULL` 존재 여부를 검증하지 못한 채 테스트가 통과한다. 마이그레이션 회귀를 놓칠 수 있다.

제안: 부분 인덱스 검증은 Flyway를 켠 migration 기반 테스트로 분리하거나, `pg_indexes`/`DatabaseMetaData`를 조회하는 테스트의 설정을 문서에 명확히 적는다. 일반 Specification/Repository 동작 테스트는 기존 create-drop 슬라이스로 유지해도 된다.

## Minor

### `docs/superpowers/specs/2026-06-06-notice-domain-design.md:111`

상세 GET에서 존재 여부 확인 전에 조회수 UPDATE를 실행한다.

기존 Sermon 패턴을 그대로 따르는 결정이라 치명적이지는 않지만, 없는 id나 soft-deleted id에도 `UPDATE ... WHERE ...` 0-row 쿼리 후 SELECT를 한 번 더 수행한다. 동시 삭제 상황에서는 조회수 증가와 404 판정의 의미가 애매해질 수 있다.

제안: Sermon과 완전히 일관되게 가는 것이 목표라면 현 설계를 유지하되, 이 동작을 의도한 trade-off로 남긴다. 더 엄격한 계약을 원하면 `incrementViewCount`의 반환값 0을 바로 404로 매핑하는 방식을 고려한다.

## Positive

- 기존 Sermon 구현의 재사용 경계를 잘 따라가며, Tag/Author/Media SPI 전제가 실제 코드와 대체로 맞다.
- 미디어 참조 정규식 경계와 soft-delete 제외 조건이 명확해 삭제 차단 오탐 위험을 낮춘다.
- PATCH의 태그 유지/null 의미, PUT의 전체 교체 의미가 명확하다.

## 확인한 근거

- `src/main/java/com/elipair/church/domain/sermon/SermonService.java`
- `src/main/java/com/elipair/church/domain/tag/ContentTagRepository.java`
- `src/test/java/com/elipair/church/domain/sermon/SermonRepositoryTest.java`
- `src/main/java/com/elipair/church/global/config/SecurityConfig.java`
- `src/main/java/com/elipair/church/global/exception/GlobalExceptionHandler.java`
