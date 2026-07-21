# 차량운행 신청 설계 (domain/vehicle)

- 날짜: 2026-07-21
- 상태: 승인됨 (브레인스토밍 완료)

## 배경 / 문제

주일·토요일 차량운행 시 중등부/고등부가 나뉘어 있어 "누가 어디서 타는지" 정보가 단편화되어 매주 혼선이 발생한다. 홈페이지에서 차량이 필요한 사람이 직접 신청하고, 운영자가 **날짜별 통합 명단**을 한 곳에서 보게 한다.

## 확정된 요구사항 (브레인스토밍 결정)

| 항목 | 결정 |
|---|---|
| 신청자 모델 | 회원 로그인 신청, **승인 교인(MEMBER) 전용** (갤러리·설교·통독과 동일 관례) |
| 신청 단위 | **날짜별 신청** — 매주 타는 사람만 그 주에 신청 |
| 신청 대상 | 중·고등학생에 한정하지 않음 — 청년·일반 성도 포함 **모든 승인 교인** (부서 하드코딩 없음) |
| 운행 일정 | **관리자가 운행일 사전 등록**, 회원은 열린 운행일 중 선택해 신청 |
| 관리 범위 | **통합 명단 조회까지만** — 차량·배차·탑승확인 없음 |

## 데이터 모델

테이블 2개, 둘 다 `BaseEntity` 상속 (`created_at`/`updated_at`/`created_by`/`updated_by`/`deleted_at`/`version`).

### `vehicle_runs` (운행일)

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `departs_at` | TIMESTAMP NOT NULL | 운행 일시. 신청 마감 시각을 겸함 (저장소 관례: TIMESTAMP + 앱 Clock) |
| `note` | TEXT NULL | 예: "토요일 오후, 학원 앞 경유" |

### `vehicle_requests` (신청)

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `run_id` | BIGINT FK → vehicle_runs NOT NULL | |
| `member_id` | BIGINT FK → members NOT NULL | 신청자. 이름·연락처는 members join으로 표시 |
| `pickup_location` | VARCHAR(200) NOT NULL | 픽업 장소 자유 텍스트 |
| `note` | TEXT NULL | 동승 인원·특이사항 |

### 인덱스·제약 (관례: 전부 partial)

- **부분 유니크**: `(run_id, member_id) WHERE deleted_at IS NULL` — 중복 신청 차단, 취소 후 재신청 자연 허용
- 목록 인덱스: `vehicle_runs(departs_at) WHERE deleted_at IS NULL`, `vehicle_requests(run_id) WHERE deleted_at IS NULL`
- 취소 = soft delete. 운행 취소("이번 주 운행 없음") = 운행일 미등록 또는 삭제 — 별도 상태 플래그 없음. 신청 cascade 없음(삭제된 run은 조회에서 자연 제외)

## API

공통: 목록은 `{content, page:{...}}` envelope, 오류는 RFC 7807 기존 코드 재사용.

### 회원용 — `/api/vehicle-runs/**` (경로 권한 `VEHICLE_APPLY`)

| 메서드/경로 | 동작 |
|---|---|
| `GET /api/vehicle-runs` | 다가오는 운행일 목록(`departs_at >= now`, 페이지네이션). 항목마다 `myRequest: {pickupLocation, note} \| null` 포함 — 별도 "내 신청 조회" 엔드포인트 없음 |
| `POST /api/vehicle-runs/{id}/requests` | 신청 `{pickupLocation, note}`. 중복 → `409 DUPLICATE_RESOURCE`, 지난 운행 → `400 INVALID_INPUT_VALUE`, 없는 운행 → `404 RESOURCE_NOT_FOUND` |
| `DELETE /api/vehicle-runs/{id}/requests/me` | 본인 신청 취소 (소유권 암묵 — request id 불필요). 신청 없으면 `404` |

### 관리자용 — `/api/admin/vehicle-runs/**` (메서드 `@PreAuthorize("hasAuthority('VEHICLE_MANAGE')")`)

| 메서드/경로 | 동작 |
|---|---|
| `POST /api/admin/vehicle-runs` | 운행일 등록 `{departsAt, note}` |
| `PATCH /api/admin/vehicle-runs/{id}` | 수정 (version 필수, 충돌 → `409 OPTIMISTIC_LOCK_CONFLICT`) |
| `DELETE /api/admin/vehicle-runs/{id}` | soft delete |
| `GET /api/admin/vehicle-runs` | 전체 목록 (지난 운행 포함) |
| `GET /api/admin/vehicle-runs/{id}/requests` | **통합 명단**: 이름·연락처(phone)·픽업 장소·메모·신청 시각. 연락처=개인정보 → 조회부터 `VEHICLE_MANAGE` (inquiry 관례) |

## 권한·보안

- 새 권한 2개: `VEHICLE_APPLY`(→ MEMBER 시드), `VEHICLE_MANAGE`(→ ADMIN·SUPER_ADMIN 시드) — V2·V13·V15 시드 패턴
- `SecurityConfig` 매처 체인: `anyRequest().permitAll()` **앞에** `/api/vehicle-runs/**` → `hasAuthority('VEHICLE_APPLY')` 추가
- 문서 동기화: `.claude/rules/rbac-authorization.md` 경로 표 + CLAUDE.md 요약 갱신 (관례상 필수)
- 마이그레이션 1건(다음 버전 번호): 테이블 2 + 부분 인덱스 + 권한 시드

## 테스트 계획 (TDD)

- 통합 테스트(MockMvc): 신청 성공 / 중복 409 / 지난 운행 400 / 취소 후 재신청 / 명단 조회 성공 / USER·익명 차단(403·401) / 운행일 CRUD·낙관적 락 409
- 회귀 갱신(관례상 필수):
  - 시드 하드코딩 테스트 3건 갱신 — `RbacSeedIntegrity`·`PermissionApi`·`RoleApi`
  - `MigrationIndexTest`에 vehicle 부분 인덱스 검증 추가 (flyway-on)
- 커버리지 80%+

## 제외 (YAGNI — 필요 시 확장)

정원(capacity)·조기 마감·탑승 확인·차량/배차·알림·부서 컬럼. 전부 현 스키마에서 컬럼/테이블 추가로 확장 가능. 태그·미디어·조회수 미사용(ContentRef provider 불필요).
