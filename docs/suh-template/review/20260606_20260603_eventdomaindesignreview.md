# Event 도메인 설계 리뷰

대상: `docs/superpowers/specs/2026-06-06-event-domain-design.md`

## 전체 평가

Request Changes

- Critical: 0
- Major: 2
- Minor: 1

## Major

### `docs/superpowers/specs/2026-06-06-event-domain-design.md:23`

범위 겹침 조건이 반열림 구간과 맞지 않는다.

문서는 `toExclusive`를 쓰는 반열림 구간으로 날짜 파라미터를 정규화하지만, overlap 조건은 `COALESCE(end_at, start_at) >= from`이다. 이러면 `end_at == from`인 이벤트가 조회 범위에 포함된다. 예를 들어 5월 31일 22:00부터 6월 1일 00:00까지인 행사는 6월 달력의 시작 경계에서 이미 끝났는데도 6월 조회에 노출된다.

영향: 월/기간 달력에서 경계에 걸친 일정이 하루 더 보이는 오프바이원 버그가 생긴다.

제안: 반열림 구간을 유지하려면 종료 조건을 `COALESCE(end_at, start_at) > from`으로 바꾼다. 단, `end_at == start_at`을 허용할지부터 정해야 한다. 0-duration 이벤트를 허용하지 않을 거면 검증을 `endAt == null || endAt.isAfter(startAt)`로 강화한다. 허용할 거면 end_at null 점 이벤트와 end_at=start_at 이벤트의 range 포함 규칙을 테스트에 명시한다.

### `docs/superpowers/specs/2026-06-06-event-domain-design.md:129`

태그만 변경하는 update/patch에서 `@Version`이 증가하지 않을 수 있다.

설계는 `replaceLinks(...) -> repository.flush() -> detail(e)`로 응답 version을 N+1로 보장한다고 적고 있다. 하지만 `flush()`는 dirty 상태인 Event 엔티티만 업데이트한다. PATCH가 `tagIds`만 제공하거나, PUT이 기존 스칼라 필드와 같은 값을 보내고 태그만 바꾸면 Event 엔티티는 dirty가 아니어서 `version`이 증가하지 않는다. `content_tags`만 바뀐다.

영향: 명시적 version 비교가 태그 변경 동시성까지 보호한다는 계약이 깨진다. 두 클라이언트가 같은 version으로 tag-only PATCH를 보내면 둘 다 통과하고 마지막 태그 교체가 이길 수 있다. 또한 테스트가 기대하는 "수정 응답 version N+1"도 tag-only 케이스에서는 보장되지 않는다.

제안: 태그 변경도 리소스 수정으로 볼 거면 `LockModeType.OPTIMISTIC_FORCE_INCREMENT`를 사용하거나, 리포지토리에 version 강제 증가 쿼리를 두는 식으로 Event version을 명시적으로 올린다. 테스트에는 tag-only PATCH/PUT의 version 증가와 stale 요청 409를 추가한다. 태그 변경을 version 대상에서 제외할 거면 `ContentTagService`의 낙관락 보장 전제와 이 문서의 N+1 보장 문구를 낮춰야 한다.

## Minor

### `docs/superpowers/specs/2026-06-06-event-domain-design.md:160`

`year` 값 검증 계약이 빠져 있다.

문서는 `month` 범위, 쌍 누락, `endDate < startDate`는 400으로 정의하지만 `year` 허용 범위는 정의하지 않는다. 컨트롤러가 `LocalDate.of(year, month, 1)`로 변환할 경우 비현실적인 큰 값이나 음수/0 처리 정책이 구현자마다 달라질 수 있다.

제안: 허용 범위를 명시한다. 예: `year`는 1900..2100 또는 1..9999. 범위 밖은 `400 INVALID_INPUT_VALUE`.

## Positive

- Notice 리뷰에서 나온 flush/version 응답 문제와 migration index 테스트 한계를 문서가 이미 반영했다.
- `AuthorDisplayService` 미사용, `view_count` 미보유, `q` 미지원 같은 비목표가 명확해 구현 범위가 잘 제한된다.
- `MigrationIndexTest`, `WebConfig`, `EVENT_WRITE`, `ContentResourceType.EVENT` 등 기존 자산 전제는 실제 코드와 맞다.

## 확인한 근거

- `docs/church-backend-spec.md`
- `docs/superpowers/specs/2026-06-04-g2-common-exception-design.md`
- `src/main/java/com/elipair/church/domain/tag/ContentResourceType.java`
- `src/main/resources/db/migration/V2__create_rbac.sql`
- `src/test/java/com/elipair/church/MigrationIndexTest.java`
- `src/main/java/com/elipair/church/domain/notice/NoticeService.java`
