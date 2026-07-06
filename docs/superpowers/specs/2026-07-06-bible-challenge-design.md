# D10 — 성경 통독 챌린지 도메인 설계

> 2026-07-06 브레인스토밍 확정본. 관리자가 공동 통독 챌린지를 개설하고,
> 승인된 교인(MEMBER)이 참여해 매일 "오늘 N장 읽음"을 기록한다.
> 이 기능은 `docs/church-backend-spec.md`에 없는 신규 도메인이다.

## 1. 핵심 결정 사항

| 갈림길 | 결정 | 근거 |
|---|---|---|
| 추적 단위 | **장(章) 단위, 순차 통독 포인터** | 성경 구조(66권 1189장)는 불변 상수 → 테이블·관리자 입력 불필요. 장 추적이 "매일 체크인"과 "일별 플랜"을 흡수 |
| 완료 방식 | **가변 분량**: "오늘 N장 읽음" (기본값 = 해당 날짜의 남은 목표치) | 오늘 10장, 내일 15장 가능해야 함 |
| 기록 범위 | **날짜별 로그** (참여당 하루 1행) | 스트릭·달력 히트맵·페이스·회독별 기간이 전부 여기서 파생 |
| 생성 주체 | **관리자 공동 챌린지** (개인 챌린지 없음) | 교회 행사와 연동, 함께 읽는 모델 |
| 범위(scope) | **연속 권 구간** `start_book ~ end_book` (1~66) | 전체(1~66)·구약(1~39)·신약(40~66)·복음서(40~43) 등을 두 필드로 표현. 비연속 조합은 YAGNI |
| 참여 자격 | **MEMBER 역할** (`CHALLENGE_PARTICIPATE` 권한, 갤러리 패턴 동일) | 승인된 교인 전용 |
| 챌린지 종료 | **파생 상태** (UPCOMING/ONGOING/ENDED) — 종료 조작 없음 | 날짜에서 계산. 다음 챌린지는 새로 개설 (기록이 챌린지에 귀속) |
| 소급 입력 | **허용** — `date` 파라미터 (챌린지 시작일~오늘) | 어르신이 체크를 까먹는 건 일상. 백필로 스트릭 치유 |

## 2. 데이터 모델

### BibleStructure (코드 상수 — 테이블 아님)

`domain/challenge/BibleStructure.java`. 개신교 정경 66권의 한글 이름 + 권별 장 수 + 누적합.

- 헬퍼: `chapterCount(startBook, endBook)` → 구간 총 장 수, `locate(startBook, offset)` → "출애굽기 7장" (권 경계는 누적합 산술로 자동 처리).
- 검증 데이터: 구약(1~39권) 929장, 신약(40~66권) 260장, 전체 1,189장.
- 권별 장 수 (1→66):
  `50,40,27,36,34,24,21,4,31,24,22,25,29,36,10,13,10,42,150,31,12,8,66,52,5,48,12,14,3,9,1,4,7,3,3,3,2,14,4` (구약 39권)
  `28,16,24,21,28,16,16,13,6,6,4,4,5,3,6,4,3,1,13,5,5,3,5,1,1,1,22` (신약 27권)

### bible_challenges — BaseEntity 상속 (감사·소프트삭제·낙관락)

| 컬럼 | 타입 | 내용 |
|---|---|---|
| id | BIGSERIAL PK | |
| title | VARCHAR(100) NOT NULL | "2026 사순절 100일 통독" |
| description | TEXT NULL | 소개 (마크다운 raw — 기존 관례) |
| start_book | SMALLINT NOT NULL | 1~66 |
| end_book | SMALLINT NOT NULL | 1~66, `start_book <= end_book` CHECK |
| start_date | DATE NOT NULL | |
| target_days | INT NOT NULL | > 0. 종료일 = `start_date + target_days - 1`(포함), 하루 목표 = `⌈구간 장 수 / target_days⌉` — 둘 다 파생, 저장 안 함 |
| (BaseEntity) | | created_at/updated_at/created_by/updated_by/deleted_at/version |

- 파생 상태: 오늘 < start_date → `UPCOMING`, 종료일까지 → `ONGOING`, 이후 → `ENDED`. 컬럼 없음.
- 부분 인덱스: `idx_bible_challenges_list ON (start_date DESC) WHERE deleted_at IS NULL`.

### challenge_participations — BaseEntity 상속

| 컬럼 | 타입 | 내용 |
|---|---|---|
| id | BIGSERIAL PK | |
| challenge_id | BIGINT NOT NULL FK → bible_challenges | |
| member_id | BIGINT NOT NULL FK → members | BaseEntity 관례대로 Long 참조 (연관 아님) |
| chapters_read | INT NOT NULL DEFAULT 0 | 현재 회독의 포인터 (0 ~ 구간 장 수) |
| rounds_completed | INT NOT NULL DEFAULT 0 | 완독 횟수 |
| (BaseEntity) | | 참여 취소 = 소프트삭제. `@Version`이 동시 클릭 방어 |

- 부분 유니크 인덱스: `(challenge_id, member_id) WHERE deleted_at IS NULL` — 중복 참여 방지, 취소 후 재참여 허용.
- 참여일 = created_at 재사용 (별도 joined_at 없음).

### challenge_reading_logs — BaseTimeEntity만 (소프트삭제·낙관락 불필요)

| 컬럼 | 타입 | 내용 |
|---|---|---|
| id | BIGSERIAL PK | |
| participation_id | BIGINT NOT NULL FK → challenge_participations | |
| read_date | DATE NOT NULL | |
| chapters | INT NOT NULL | > 0. 같은 날 추가 읽기 = 기존 행에 누적 |

- 유니크: `(participation_id, read_date)`. 취소 시 물리 삭제(하드) — 로그는 포인터의 파생 기록이라 소프트삭제 부적합.
- 스트릭·히트맵·페이스·회독별 기간 전부 이 로그에서 계산. 별도 회독 이력 테이블 없음.

## 3. API

### 관리자 (`CHALLENGE_MANAGE`)

```
POST   /api/admin/bible-challenges           개설 {title, description?, startBook, endBook, startDate, targetDays}
PATCH  /api/admin/bible-challenges/{id}      부분 수정 (version 필수 — 409 OPTIMISTIC_LOCK_CONFLICT)
DELETE /api/admin/bible-challenges/{id}      소프트삭제
```

- 참여자가 있는 챌린지의 start_book/end_book/target_days 수정은 진행률 의미를 깨뜨리므로 **참여자 존재 시 범위·기간 필드 수정 거부** (409 `DUPLICATE_RESOURCE` 아님 — 400 `INVALID_INPUT_VALUE`, detail로 사유 명시). title/description은 항상 수정 가능.

### 회원 (`CHALLENGE_PARTICIPATE` — MEMBER 역할에 시드)

```
GET    /api/bible-challenges                          목록 (페이지 envelope, 기본 정렬 start_date DESC, status 파생 포함)
GET    /api/bible-challenges/{id}                     상세 (내 참여 여부 포함)
POST   /api/bible-challenges/{id}/join                참여
POST   /api/bible-challenges/{id}/read                {chapters?, date?} 읽음 기록
DELETE /api/bible-challenges/{id}/read?date=          해당 날짜 기록 취소 (기본 오늘)
GET    /api/bible-challenges/{id}/my-progress         대시보드 원샷
GET    /api/bible-challenges/{id}/my-logs?from=&to=   달력 히트맵용 날짜별 로그 (from/to 생략 시 챌린지 기간 전체, 페이지 아님 — 배열 반환)
GET    /api/bible-challenges/my-participations        마이페이지: 내 참여 이력 전체 (페이지 envelope)
```

### read 동작 (핵심 로직)

1. `date` 기본값 오늘. 허용 범위 `[challenge.start_date, 오늘]` — 벗어나면 400.
2. `chapters` 기본값 = 해당 날짜의 남은 목표치 `max(dailyGoal - 그날 이미 기록한 장 수, 0)`; 계산 결과 0이면 400 (명시값 요구). 명시값은 > 0.
3. 로그 upsert: 해당 날짜 행에 chapters 누적.
4. 포인터 전진: `chapters_read += chapters`. 구간 장 수 도달/초과 시 `rounds_completed += 1`, 포인터는 초과분 이월 (여러 회독 한 번에 가능 — 루프).
5. 참여하지 않았으면 404, ENDED 챌린지도 기록 허용 (늦은 완주 응원 — 차단 없음).

### read 취소 동작

해당 날짜 로그 행 삭제 + 포인터를 그 장 수만큼 롤백. 포인터가 음수로 내려가면 `rounds_completed -= 1`, 포인터 `+= 구간 장 수` (회독 경계 역이월). 해당 날짜 로그 없으면 404.

### my-progress 응답

```json
{
  "progressRate": 34.5,
  "currentPosition": { "book": "출애굽기", "chapter": 7 },
  "chaptersRead": 410, "totalChapters": 1189,
  "todayChapters": 10, "dailyGoal": 12, "todayDone": false,
  "streakDays": 23, "roundsCompleted": 1,
  "paceDays": -3,
  "challenge": { "id": 1, "title": "…", "startDate": "…", "endDate": "…", "status": "ONGOING" }
}
```

- `streakDays`: 로그가 있는 연속 일수. 오늘 기록이 아직 없으면 어제까지 이어진 스트릭 유지 (오늘은 아직 기회 있음).
- `paceDays`: `(실제 누적 장 수 - 예정 누적 장 수) / dailyGoal` 반올림. 예정 = `min(경과일, target_days) × dailyGoal`. 늦은 참여자도 챌린지 시작일 기준 (공동 챌린지). ENDED면 null.
- `progressRate`: 현재 회독 기준 `chapters_read / 구간 장 수 × 100`.

### my-participations 응답 항목 (마이페이지)

챌린지 요약(title/기간/status/totalChapters) + created_at(참여일) + progressRate + roundsCompleted + chaptersRead + completed(rounds ≥ 1) + streakDays.

## 4. 권한·경로

| 경로 | 규칙 |
|---|---|
| `/api/admin/bible-challenges/**` | `CHALLENGE_MANAGE` |
| `/api/bible-challenges/**` | 로그인 + `CHALLENGE_PARTICIPATE` (갤러리 패턴 — 공개 아님) |

- 새 권한 시드 2건: `CHALLENGE_MANAGE`('통독 챌린지 관리') → ADMIN 이상, `CHALLENGE_PARTICIPATE`('통독 챌린지 참여') → MEMBER. V2 시드 패턴 그대로.
- 컨트롤러 `@PreAuthorize("hasAuthority('…')")` — 권한 단위, 역할 아님.

## 5. 동작 규칙 / 엣지

- **"오늘"** = `APP_TIMEZONE` env (기본 `Asia/Seoul`) 기준 LocalDate. Clock 주입으로 테스트 가능하게.
- **동시 클릭**: participation `@Version` → 409 `OPTIMISTIC_LOCK_CONFLICT`.
- **중복 참여**: 409 `DUPLICATE_RESOURCE`.
- **미참여 상태의 read/progress/logs**: 404 `RESOURCE_NOT_FOUND`.
- **UPCOMING 챌린지 read**: 400 (date 범위 검증에서 자연 차단 — start_date 이전이므로).
- **에러 코드**: 전부 기존 canonical 재사용, 신규 코드 없음.
- **뷰카운트·태그·미디어**: 무관 — 연동 없음.

## 6. 마이그레이션 & 테스트

- **V13__create_bible_challenges.sql**: 테이블 3개 + 부분 인덱스 + CHECK 제약 + 권한 시드 2건.
- **MigrationIndexTest**: 부분 인덱스 회귀 케이스 추가 (신규 도메인 관례).
- **TDD 순서**:
  1. `BibleStructure` 순수 단위 테스트 — 총합(929/260/1189), 권 경계(창세기 50→출애굽기 1), 양끝(창세기 1, 요한계시록 22), locate 역산.
  2. 서비스 테스트 — read 기본값/누적/회독 이월(다중 회독 포함), 취소 롤백(회독 경계 역이월), 스트릭(백필 치유·오늘 미기록 유지), 페이스, 파생 status.
  3. 컨트롤러 슬라이스 — 권한 3단계, 400/403/404/409 계약, 페이지 envelope.
  4. 수정 응답에 version 포함 시 flush 필요 (Notice/Sermon 선례).

## 7. 제외 (YAGNI — 필요해지면 그때)

- 개인(회원 생성) 챌린지, 비연속 권 조합(시편+잠언), 챌린지 복제 API(프론트 프리필로 충분), 회독별 소요기간 상세 응답(로그에서 파생 가능), 리더보드/랭킹, 알림·리마인더(SMTP 없음 — 템플릿 규율), 지난 날짜 로그 "수정"(취소 후 재기록으로 충분).
