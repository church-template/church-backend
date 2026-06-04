# G5 · 직분 (Position) 도메인 설계

> 작성일: 2026-06-04
> 대상 이슈: GitHub #6 (직분 Position 도메인) — 로드맵 Phase 2 · 첫 순서
> 출처 스펙: [`docs/church-backend-spec.md`](../../church-backend-spec.md) §5.3 (직분 API), §3.2 (positions 테이블), §6 (인덱스), §7 (패키지 구조)
> 상위 로드맵: [`2026-06-04-church-backend-workflow-design.md`](./2026-06-04-church-backend-workflow-design.md)
> 선행: [G2 공통·예외](./2026-06-04-g2-common-exception-design.md) (`BaseTimeEntity`·`ErrorCode`·`BusinessException` 재사용), [G3 보안 기반](./2026-06-04-g3-security-foundation-design.md) (`@PreAuthorize` 메서드 보안·경로 3분법·JWT 인가)
> 후속: #7 역할·권한(RBAC — `POSITION_MANAGE` 권한을 실제 역할에 시드·부여), #8 회원(`members.position_id` FK + `ON DELETE SET NULL` 추가)

## 목표 / 성공 기준

스펙 §5.3의 직분 CRUD를 **첫 비즈니스 도메인**으로 구현한다. 직분(목사·장로·권사…)은 권한과 독립된 별개의 축이며 회원의 **선택값**(`position_id` NULL 허용)이다. 코드 수정 없이 데이터로 추가/삭제할 수 있도록 한다.

> 범위 확정(브레인스토밍): 직분은 **단순 마스터 CRUD**다. `members.position_id` FK·`ON DELETE SET NULL`은 **members 테이블에 의존**(로드맵 #8)하므로 이번 마이그레이션엔 positions 테이블만 만들고, FK 계약은 후속 members 마이그레이션이 추가한다. 직분 삭제는 물리 삭제이며, members가 생기면 DB의 `ON DELETE SET NULL`이 자동 해제를 책임진다 — 앱 레벨 cascade 로직은 두지 않는다.

성공 기준:
1. `Position` 엔티티(`BaseTimeEntity` 상속)와 `V1__create_positions.sql` 마이그레이션이 `name UNIQUE`·`sort_order`·`created_at`을 갖고 `ddl-auto: validate`를 통과한다.
2. `GET /api/positions` (공개)가 `sort_order ASC` 정렬된 **전체 배열**(비페이징 `List<PositionResponse>`)을 반환한다.
3. `POST/PATCH/DELETE /api/admin/positions` 3종이 `@PreAuthorize("hasAuthority('POSITION_MANAGE')")` 가드를 갖는다.
4. 생성 시 `sortOrder`는 선택 입력 — 누락 시 `max(sort_order)+10`(빈 테이블 10)으로 맨 뒤에 자동 배치한다(간격 번호로 중간 삽입 여지 확보).
5. 중복 `name` → `409 DUPLICATE_RESOURCE`, 없는 `id` → `404 RESOURCE_NOT_FOUND`. 신규 `ErrorCode` 없이 기존 코드를 재사용한다.
6. `domain → global` 단방향 의존 유지(ArchUnit green), 빌드·테스트 green, 신규 코드 커버리지 80%+.

## 핵심 결정

브레인스토밍에서 확정한 갈림길(권장안 채택):

1. **`members.position_id` FK는 후속(members 마이그레이션)으로 미룬다.** 로드맵 순서가 **#6 직분 → #7 권한 → #8 회원**이라 직분이 members보다 먼저 구현된다. `V1`은 positions 테이블만 생성하고, FK 컬럼·제약(`ON DELETE SET NULL`)은 members 마이그레이션이 추가한다. 직분 DELETE는 **물리 삭제**(hard delete) — members가 생기면 DB의 `ON DELETE SET NULL`이 자동 해제를 책임지므로 앱 레벨에서 회원을 조회·null 처리하지 않는다.
   - 반려안 B(앱 레벨 회원 null 처리): members가 없어 불가하고, 스펙은 DB 제약을 명시.
   - 반려안 C(참조 시 삭제 차단, media식): 스펙(자동 해제)과 모순.
2. **목록 = 비페이징 전체 배열.** 직분은 가입 폼·필터 드롭다운용 소규모 마스터 목록(보통 10~20행)이라 `List<PositionResponse>`를 `sort_order ASC`로 전부 반환한다. 스펙 §5의 "모든 목록 페이지네이션" 일반 규칙에서 **마스터 목록(직분·태그류)만 의도적 예외**로 둔다(드롭다운에 페이지 봉투는 부적합). 후속 태그(§5.11)도 같은 패턴으로 통일.
3. **`sort_order` = 선택 입력 + 누락 시 맨 뒤 자동(간격 번호).** `POST` 요청에 `sortOrder`가 있으면 그 값, 없으면 `max(sort_order)+10`(빈 테이블 10)으로 끝에 추가. `PATCH`로 값 변경 가능. **간격을 10으로 두는 이유:** 관리자가 두 직분 사이에 새 직분을 끼워 넣을 때(예: 10과 20 사이 → 15) 다른 행을 재번호하지 않아도 된다 — 연속(0,1,2)이면 사이에 정수가 없어 즉시 재번호가 필요하다. 비용 0의 유지보수 여유. 스펙에 별도 "순서 재정렬" 엔드포인트가 없으므로 만들지 않는다(YAGNI — `PATCH`의 `sortOrder`로 충분). `sort_order`는 UNIQUE가 아니므로 동률 허용(동률 시 정렬 순서는 미정의).
4. **단순 도메인 = 플랫 패키지.** 스펙 §7의 "단순하면 하위 폴더 없이 파일만" 지침을 따라 `controller/service/repository/entity` 하위 폴더를 두지 않고 `domain/position` 아래 파일을 직접 둔다. DTO만 `dto/` 하위에 묶는다.
5. **매핑 = 수동 정적 팩토리.** 3필드 trivial 매핑이라 `PositionResponse.from(Position)`(immutable record)을 쓰고, MapStruct는 더 복잡한 엔티티↔DTO(설교·공지 등)용으로 유보한다 — 생성 코드·애너테이션 프로세서 오버헤드를 trivial 케이스에 끌어들이지 않는다(simplicity-first).
6. **soft delete·낙관락·작성자 없음.** 스펙 §3.2 positions 테이블은 `id·name·sort_order·created_at`만 가진다 — `deleted_at`·`version`·`created_by`/`updated_by`가 없다. 따라서 `BaseEntity`가 아니라 **`BaseTimeEntity`**(createdAt만) 상속이 정확하다. 직분은 마스터 데이터라 물리 삭제가 자연스럽다.

## 산출물 (파일)

신규 — `domain/position/`:

```text
Position.java                     // @Entity extends BaseTimeEntity (name·sortOrder)
PositionRepository.java           // JpaRepository<Position, Long>
PositionService.java              // @Service @Transactional — list/create/update/delete
PositionController.java           // GET /api/positions(공개) + admin 3종(@PreAuthorize)
dto/PositionResponse.java         // record(id, name, sortOrder, createdAt) + static from()
dto/PositionCreateRequest.java    // record(name @NotBlank @Size(max=50), sortOrder @PositiveOrZero?) — name 필수
dto/PositionUpdateRequest.java    // record(name @Size(max=50)?, sortOrder @PositiveOrZero?) — name nullable(미변경), @NotBlank 금지
```

신규 — 마이그레이션:

```text
src/main/resources/db/migration/V1__create_positions.sql   // 첫 Flyway 마이그레이션
```

신규 — 테스트:

```text
test/.../domain/position/PositionServiceTest.java       // 단위(repo mock)
test/.../domain/position/PositionRepositoryTest.java    // @DataJpaTest + Testcontainers
test/.../domain/position/PositionControllerTest.java    // @WebMvcTest (인가·검증·응답)
test/.../domain/position/PositionIntegrationTest.java   // @SpringBootTest + Testcontainers (해피패스 1건, 선택)
```

수정 — 없음(신규 클래스만 추가). `ErrorCode`는 기존 `DUPLICATE_RESOURCE`·`RESOURCE_NOT_FOUND`·`INVALID_INPUT_VALUE`를 그대로 재사용한다. **`GlobalExceptionHandler`도 수정하지 않는다** — UNIQUE 경합의 `DataIntegrityViolationException`→`DUPLICATE_RESOURCE` 변환은 **서비스 내부 `saveAndFlush()` + `try/catch`** 로 처리한다(positions의 유일 제약이 `name` UNIQUE라 매핑이 모호하지 않음). 전역 핸들러에 광범위한 `DataIntegrityViolationException`→409 매핑을 두면 후속 도메인의 NOT NULL·FK 위반까지 409로 오인하므로 두지 않는다.

> `db/migration` 디렉터리는 현재 비어 있다(G2~G4는 테이블을 만들지 않음 — 공통 모듈·Redis·파일시스템). 직분이 **첫 DB 테이블이자 첫 Flyway 마이그레이션**이라 버전은 `V1`로 시작한다.

## 엔티티 / 마이그레이션

**Position** (`BaseTimeEntity` 상속 — `created_at`만 추가 감사):

| 필드 | 컬럼 | 매핑 | 비고 |
|---|---|---|---|
| id | id | `@Id @GeneratedValue(IDENTITY)` Long | 내부 PK |
| name | name | `@Column(nullable=false, unique=true, length=50)` String | 한글 표시명(목사·장로…), 유니크 |
| sortOrder | sort_order | `@Column(name="sort_order", nullable=false)` Integer | 표시 순서 |
| createdAt | created_at | `BaseTimeEntity` 상속 | JPA Auditing |

- 무분별한 `@Setter` 금지 — 생성은 정적 팩토리 `Position.of(name, sortOrder)`, 수정은 도메인 메서드 `update(String name, Integer sortOrder)`(null 인자는 미변경)로만 한다(immutable 지향).
- `name`은 저장 전 `trim()` 정규화한다.

**V1__create_positions.sql** (엔티티와 정확히 일치 — `ddl-auto: validate`):

```sql
CREATE TABLE positions (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name       VARCHAR(50) NOT NULL,
    sort_order INTEGER     NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    CONSTRAINT uq_positions_name UNIQUE (name)
);

-- 직분 목록 정렬용(스펙 §6). positions엔 deleted_at이 없어 부분 인덱스가 아니다.
CREATE INDEX idx_positions_sort_order ON positions (sort_order);

-- NOTE(#8 members): members.position_id BIGINT FK → positions(id) ON DELETE SET NULL은
--   members 테이블 생성 마이그레이션에서 추가한다(직분이 members보다 먼저 구현되므로 여기선 보류).
```

- `GENERATED BY DEFAULT AS IDENTITY`로 Hibernate `IDENTITY` 전략과 정합(Hibernate가 id를 지정하지 않고 INSERT).
- `created_at`은 엔티티 `LocalDateTime NOT NULL` → `TIMESTAMP`(without time zone)로 검증 통과.

## API 계약

| 메서드 | 경로 | 권한 | 요청 | 성공 응답 |
|---|---|---|---|---|
| GET | /api/positions | 공개 | — | 200 `List<PositionResponse>` (sort_order ASC) |
| POST | /api/admin/positions | `POSITION_MANAGE` | `{name, sortOrder?}` | 201 `PositionResponse` |
| PATCH | /api/admin/positions/{id} | `POSITION_MANAGE` | `{name?, sortOrder?}` | 200 `PositionResponse` |
| DELETE | /api/admin/positions/{id} | `POSITION_MANAGE` | — | 204 No Content |

**PositionResponse** `{ "id": 1, "name": "장로", "sortOrder": 10, "createdAt": "2026-06-04T..." }`

- **인가:** `/api/positions`는 `SecurityConfig`의 `anyRequest().permitAll()` 갈래로 공개. admin 3종은 `/api/admin/**` → `authenticated()` + 메서드 `@PreAuthorize("hasAuthority('POSITION_MANAGE')")`(G3 경로 3분법 그대로).
- **검증(`@Valid`) — Create와 Update가 다르다:**
  - **Create**(`PositionCreateRequest`): `name` `@NotBlank @Size(max=50)`(null·공백·공백만 모두 거부), `sortOrder` `@PositiveOrZero`(선택).
  - **Update**(`PositionUpdateRequest`): `name` `@Size(max=50)`만(`@NotBlank` 금지 — null은 "미변경"을 의미하므로 `sortOrder`만 바꾸는 PATCH가 깨지면 안 됨), `sortOrder` `@PositiveOrZero`(선택).
  - `@Size`는 `"   "` 같은 공백만 문자열을 통과시키므로(trim 후 빈 문자열 저장 위험), Update에서 **name이 들어온 경우** 서비스의 `normalizeName`이 `trim` 후 blank면 `INVALID_INPUT_VALUE`로 거부한다(Create는 `@NotBlank`가 경계에서 1차 차단 + `normalizeName` 2차 방어).
  - 위반 시 G2 `GlobalExceptionHandler`가 `400 INVALID_INPUT_VALUE`로 매핑(`@Valid`는 필드 오류 배열, 서비스 blank 거부는 `BusinessException`).
- **중복 name:** `existsByName(name)` 선검사 → `409 DUPLICATE_RESOURCE`(일반 경로, 예외 없이 깔끔). DB `UNIQUE`는 **데이터 무결성 백스톱** — 동시 생성 경합처럼 선검사를 빠져나간 경우, 서비스가 `saveAndFlush()`로 즉시 flush해 `DataIntegrityViolationException`을 그 자리에서 `catch`해 `DUPLICATE_RESOURCE`로 변환한다(전역 핸들러 변경 없이 서비스가 소유). `saveAndFlush`를 쓰는 이유: 일반 `save()`는 INSERT가 flush/commit 시점으로 미뤄질 수 있어 서비스 `try/catch` 밖에서 터지면 500으로 새기 때문 — flush를 메서드 안으로 끌어와 catch 지점을 보장한다.
- **없는 id:** `PATCH`/`DELETE`에서 `404 RESOURCE_NOT_FOUND`.
- **스펙에 공개 단건 조회(GET /{id})가 없으므로** 만들지 않는다(YAGNI). 직분 정보는 목록·회원 응답으로 충분.

## 서비스 동작 (`@Transactional`)

공통 헬퍼 — `normalizeName(String raw)`: `raw.trim()` 후 `isBlank()`면 `INVALID_INPUT_VALUE`, 아니면 trim된 값 반환. name이 들어오는 모든 경로(create는 항상, update는 `name != null`일 때)가 이 헬퍼를 거쳐 trim·blank 거부·유니크 검사 대상값을 일관 산출한다.

- **list()** `findAllByOrderBySortOrderAsc()` → `PositionResponse.from` 매핑. (읽기 전용 트랜잭션)
- **create(req)**
  1. `name = normalizeName(req.name())`; `existsByName(name)` → `DUPLICATE_RESOURCE`.
  2. `sortOrder = req.sortOrder() != null ? req.sortOrder() : repository.findMaxSortOrder().map(m -> m + 10).orElse(10)`.
  3. `saveAndFlush(Position.of(name, sortOrder))`를 `try/catch(DataIntegrityViolationException)`로 감싸 경합 시 `DUPLICATE_RESOURCE`로 변환 → 응답.
- **update(id, req)**
  1. `findById(id)` → `RESOURCE_NOT_FOUND`.
  2. `req.name() != null`이면 `name = normalizeName(req.name())`; 기존과 다르면 `existsByName(name)` 중복 검사 → `DUPLICATE_RESOURCE`.
  3. `position.update(name, req.sortOrder())`(각 인자 null은 미변경) → `saveAndFlush(position)`를 `try/catch(DataIntegrityViolationException)`로 감싸 경합 시 `DUPLICATE_RESOURCE` 변환 → 응답.
- **delete(id)** `existsById` → 없으면 `RESOURCE_NOT_FOUND`, 있으면 `deleteById`(물리 삭제). members FK(`ON DELETE SET NULL`)는 후속 members가 생긴 뒤 DB가 자동 처리.

> `saveAndFlush`는 INSERT/UPDATE를 메서드 안에서 즉시 flush해 UNIQUE 위반이 서비스 `try/catch` 지점에서 잡히도록 보장한다 — 일반 `save()`는 flush가 트랜잭션 commit 시점으로 밀려 catch 밖에서 터지면 `GlobalExceptionHandler`의 catch-all로 가 **500 INTERNAL_ERROR**가 되기 때문이다(현재 전역 핸들러엔 `DataIntegrityViolationException` 핸들러가 없음, 확인함). `existsByName` 선검사가 일반 케이스를 담당하고, `saveAndFlush` catch는 드문 동시성 경합만 막는 백스톱이다.

**Repository 메서드:** `findAllByOrderBySortOrderAsc()`, `existsByName(String)`, `@Query("select max(p.sortOrder) from Position p") Optional<Integer> findMaxSortOrder()`.

## 권한 주의 (설계 노트)

`POSITION_MANAGE` 권한은 #7(권한 시드)·#8(회원·역할 부여) 전엔 실제로 누구에게도 부여되지 않는다. 그래도 `@PreAuthorize` 가드는 지금 **구조적으로 정확**하며, 권한 인프라가 갖춰지면 그대로 동작한다(코드 변경 0). admin 엔드포인트 테스트는 G3 `JwtTokenProvider`로 `POSITION_MANAGE` 클레임을 담은 토큰을 발급하거나(`@SpringBootTest`), `@WithMockUser(authorities = "POSITION_MANAGE")`(`@WebMvcTest`)로 검증한다.

## 테스트 (TDD, 80%+)

**PositionServiceTest** (repo mock, Spring 불필요):

| # | 케이스 | 기대 |
|---|---|---|
| 1 | list 정렬 | repo `findAllByOrderBySortOrderAsc` 결과를 응답으로 매핑 |
| 2 | create — sortOrder 지정 | 그 값으로 저장 |
| 3 | create — sortOrder 누락(기존 있음) | `max+10`으로 저장 |
| 4 | create — sortOrder 누락(빈 테이블) | `10`으로 저장 |
| 5 | create — 중복 name(선검사) | `DUPLICATE_RESOURCE` |
| 6 | create — `saveAndFlush`가 UNIQUE 경합으로 `DataIntegrityViolationException`(mock) | `DUPLICATE_RESOURCE`(서비스 catch 변환) |
| 7 | create — name 앞뒤 공백 | trim되어 저장 |
| 8 | create — name 공백만(`"   "`) | `INVALID_INPUT_VALUE`(`normalizeName`) |
| 9 | update — name만 | name 변경, sortOrder 유지 |
| 10 | update — sortOrder만(name=null) | sortOrder 변경, name 유지(`@NotBlank` 불요 확인) |
| 11 | update — name 공백만(`"   "`) | `INVALID_INPUT_VALUE`(`normalizeName`) |
| 12 | update — 없는 id | `RESOURCE_NOT_FOUND` |
| 13 | update — 다른 직분과 중복 name | `DUPLICATE_RESOURCE` |
| 14 | delete — 존재 | `deleteById` 호출 |
| 15 | delete — 없는 id | `RESOURCE_NOT_FOUND` |

**PositionRepositoryTest** (`@DataJpaTest` + Testcontainers, 실제 Flyway V1):

| # | 케이스 | 기대 |
|---|---|---|
| 13 | `findAllByOrderBySortOrderAsc` | sort_order 오름차순 |
| 14 | `existsByName` | true/false |
| 15 | `findMaxSortOrder` | 최대값 / 빈 테이블 `Optional.empty` |
| 16 | name UNIQUE 위반 | `DataIntegrityViolationException` |

**PositionControllerTest** (`@WebMvcTest` + service mock):

| # | 케이스 | 기대 |
|---|---|---|
| 17 | GET /api/positions 무인증 | 200, JSON 배열, sort_order 순 |
| 18 | POST 무인증(익명) | 401 `INVALID_TOKEN` (G3 핸들러) |
| 19 | POST 권한 부족(다른 authority) | 403 `ACCESS_DENIED` |
| 20 | POST `POSITION_MANAGE` 정상 | 201, 응답 형태 |
| 21 | POST name 공백/누락 | 400 `INVALID_INPUT_VALUE` |
| 22 | PATCH `POSITION_MANAGE` | 200 |
| 23 | DELETE `POSITION_MANAGE` | 204 No Content |

**PositionIntegrationTest** (`@SpringBootTest` + Testcontainers + `RestClient`, 선택): 실제 JWT로 생성→목록→수정→삭제 end-to-end 1건.

## 미루는 것 (명시적 비범위)

- `members` 테이블·엔티티와 `members.position_id` FK·`ON DELETE SET NULL`(로드맵 #8).
- `POSITION_MANAGE` 권한의 역할 시드·부여(로드맵 #7 RBAC).
- 직분 "순서 재정렬" 전용 엔드포인트(스펙에 없음, YAGNI — `PATCH`의 `sortOrder`로 충분).
- 공개 단건 조회 `GET /api/positions/{id}`(스펙에 없음).
- MapStruct 기반 매핑(더 복잡한 콘텐츠 도메인에서 도입).
