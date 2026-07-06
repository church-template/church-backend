# 성경 통독 챌린지(D10) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자가 공동 통독 챌린지를 개설하고 승인된 교인(MEMBER)이 매일 "오늘 N장 읽음"을 기록하는 API — 스펙 `docs/superpowers/specs/2026-07-06-bible-challenge-design.md` 구현.

**Architecture:** 성경 구조(66권 1189장)는 코드 상수(`BibleStructure`), 진행은 참여당 포인터(`chapters_read`) + 날짜별 로그(`challenge_reading_logs`). 테이블 3개(V13), 서비스 2개(관리 CRUD / 진행 기록), 컨트롤러 2개(admin / member). 권한 2건 신규 시드(`CHALLENGE_MANAGE`, `CHALLENGE_PARTICIPATE`).

**Tech Stack:** Spring Boot 4.0.6 / Java 21 / JPA + PostgreSQL(Flyway V13) / JUnit5 + AssertJ + Mockito + Testcontainers.

## Global Constraints

- **Spring Boot 4** 좌표: web = `spring-boot-starter-webmvc`, 테스트는 모듈별 `*-test` 스타터. **이 계획은 신규 의존성 추가 없음.**
- **버전 파일 금지**: `version.yml`, `build.gradle`의 `version`, `CHANGELOG.*` 절대 수정 금지 (자동화 소유).
- **커밋**: `<type> : <한글 설명>` (콜론 앞 공백), 본문 한국어. **Co-Authored-By 태그 금지.**
- **에러**: 기존 `ErrorCode`만 재사용 (`INVALID_INPUT_VALUE`/`RESOURCE_NOT_FOUND`/`DUPLICATE_RESOURCE`/`OPTIMISTIC_LOCK_CONFLICT`/`ACCESS_DENIED`). 신규 코드 금지.
- **인가**: `@PreAuthorize("hasAuthority('…'))` 권한 단위 — 역할/직분 검사 금지.
- **소프트삭제**: 모든 읽기 `deleted_at IS NULL` 필터, 목록 인덱스는 부분 인덱스. 신규 부분 인덱스는 `MigrationIndexTest`에 검증 추가.
- **패키지**: `com.elipair.church.domain.challenge` (+`dto/`). 의존 방향 domain→global 일방향.
- **테스트 실행**: `./gradlew test --tests '<FQCN>'`, 최종 `./gradlew build`.
- 마지막 회독 검증: 구약 929장 · 신약 260장 · 전체 1,189장 (스펙 §2의 권별 장 수 배열이 유일한 진실).

## File Structure

```
src/main/java/com/elipair/church/
├── domain/challenge/
│   ├── BibleStructure.java              # 66권 상수 + 구간 장수/위치 역산 (Task 1)
│   ├── ChallengeStatus.java             # UPCOMING/ONGOING/ENDED 파생 enum (Task 3)
│   ├── BibleChallenge.java              # 챌린지 엔티티 (Task 3)
│   ├── ChallengeParticipation.java      # 참여 엔티티 — 포인터 산술 (Task 3)
│   ├── ChallengeReadingLog.java         # 날짜별 로그 엔티티 (Task 3)
│   ├── BibleChallengeRepository.java    # (Task 3)
│   ├── ChallengeParticipationRepository.java  # (Task 3)
│   ├── ChallengeReadingLogRepository.java     # (Task 3)
│   ├── BibleChallengeService.java       # 관리 CRUD + 목록/상세 (Task 6)
│   ├── ChallengeProgressService.java    # join/read/cancel/progress/logs/participations (Task 7)
│   ├── BibleChallengeController.java    # 회원 API (Task 8)
│   ├── AdminBibleChallengeController.java  # 관리자 API (Task 8)
│   └── dto/ (Task 6·7에서 생성)
│       ├── ChallengeCreateRequest.java / ChallengePatchRequest.java
│       ├── ChallengeCardResponse.java / ChallengeDetailResponse.java / ChallengeSummaryResponse.java
│       ├── ChallengeReadRequest.java / MyProgressResponse.java / BiblePositionResponse.java
│       ├── ReadingLogResponse.java / MyParticipationResponse.java
├── global/config/ClockConfig.java       # Clock 빈 — APP_TIMEZONE (Task 4)
└── global/config/SecurityConfig.java    # 경로 규칙 1줄 추가 (Task 5)

src/main/resources/
├── application.yml                      # app.timezone 추가 (Task 4)
├── db/migration/V13__create_bible_challenges.sql  # (Task 2)
└── db/dev/afterMigrate__seed.sql        # 챌린지 시드 추가 (Task 9)

src/test/java/com/elipair/church/
├── domain/challenge/
│   ├── BibleStructureTest.java          # (Task 1)
│   ├── ChallengeParticipationTest.java  # (Task 3)
│   ├── BibleChallengeServiceTest.java   # (Task 6)
│   ├── ChallengeProgressServiceTest.java # (Task 7)
│   └── BibleChallengeApiTest.java       # (Task 8)
├── MigrationIndexTest.java              # 검증 2건 추가 (Task 2)
├── domain/role/RbacSeedIntegrityTest.java  # 12→14 권한 갱신 (Task 2)
└── global/security/
    ├── SecuredTestController.java       # /api/bible-challenges/ping 추가 (Task 5)
    └── SecurityConfigPathRulesTest.java # 경로 규칙 테스트 3건 추가 (Task 5)
```

**실행 준비**: main에서 작업 브랜치를 먼저 만든다 — `git checkout -b 20260706_성경통독챌린지` (이슈 번호가 있으면 저장소 관행대로 `20260706_#NN_...`).

---

### Task 1: BibleStructure 상수 + 단위 테스트

**Files:**
- Create: `src/main/java/com/elipair/church/domain/challenge/BibleStructure.java`
- Test: `src/test/java/com/elipair/church/domain/challenge/BibleStructureTest.java`

**Interfaces:**
- Produces: `BibleStructure.chapterCount(int startBook, int endBook) → int`, `BibleStructure.locate(int startBook, int ordinal) → BiblePosition(String book, int chapter)`, `BibleStructure.validateRange(int, int)` (위반 시 `IllegalArgumentException`), `BibleStructure.BOOK_COUNT = 66`. Task 3(엔티티 파생값)·Task 7(currentPosition)이 사용.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.elipair.church.domain.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BibleStructureTest {

    // ---- 구간 장 수 (스펙 §2 검증 데이터) ----

    @Test
    void full_bible_is_1189_chapters() {
        assertThat(BibleStructure.chapterCount(1, 66)).isEqualTo(1189);
    }

    @Test
    void old_testament_is_929_chapters() {
        assertThat(BibleStructure.chapterCount(1, 39)).isEqualTo(929);
    }

    @Test
    void new_testament_is_260_chapters() {
        assertThat(BibleStructure.chapterCount(40, 66)).isEqualTo(260);
    }

    @Test
    void single_book_psalms_is_150() {
        assertThat(BibleStructure.chapterCount(19, 19)).isEqualTo(150);
    }

    @Test
    void gospels_matthew_to_john_is_89() {
        assertThat(BibleStructure.chapterCount(40, 43)).isEqualTo(89); // 28+16+24+21
    }

    // ---- 위치 역산 (권 경계 = 누적합 산술) ----

    @Test
    void locate_first_chapter_is_genesis_1() {
        assertThat(BibleStructure.locate(1, 1)).isEqualTo(new BibleStructure.BiblePosition("창세기", 1));
    }

    @Test
    void locate_50th_is_genesis_50_and_51st_crosses_into_exodus_1() {
        assertThat(BibleStructure.locate(1, 50)).isEqualTo(new BibleStructure.BiblePosition("창세기", 50));
        assertThat(BibleStructure.locate(1, 51)).isEqualTo(new BibleStructure.BiblePosition("출애굽기", 1));
    }

    @Test
    void locate_last_chapter_is_revelation_22() {
        assertThat(BibleStructure.locate(1, 1189)).isEqualTo(new BibleStructure.BiblePosition("요한계시록", 22));
    }

    @Test
    void locate_respects_scope_start_book() {
        assertThat(BibleStructure.locate(40, 1)).isEqualTo(new BibleStructure.BiblePosition("마태복음", 1));
        assertThat(BibleStructure.locate(40, 260)).isEqualTo(new BibleStructure.BiblePosition("요한계시록", 22));
        assertThat(BibleStructure.locate(19, 151)).isEqualTo(new BibleStructure.BiblePosition("잠언", 1)); // 시편 150 다음
    }

    // ---- 검증 ----

    @Test
    void invalid_range_throws() {
        assertThatThrownBy(() -> BibleStructure.chapterCount(0, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BibleStructure.chapterCount(5, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BibleStructure.chapterCount(1, 67)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void locate_out_of_bounds_throws() {
        assertThatThrownBy(() -> BibleStructure.locate(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BibleStructure.locate(1, 1190)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BibleStructure.locate(67, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.challenge.BibleStructureTest'`
Expected: 컴파일 실패 — `BibleStructure` 미존재.

- [ ] **Step 3: 구현**

```java
package com.elipair.church.domain.challenge;

/**
 * 개신교 정경 66권 1189장 — 불변 상수(설계 §2, 테이블 없음).
 * 성경을 한 줄로 펼친 누적합으로 권 경계를 산술 처리한다: 구간 장 수·포인터→(권,장) 역산.
 */
public final class BibleStructure {

    /** 구간 내 위치 — 한글 권 이름 + 장 번호(사용자 표시용). */
    public record BiblePosition(String book, int chapter) {}

    public static final int BOOK_COUNT = 66;

    private static final String[] NAMES = {
        "창세기", "출애굽기", "레위기", "민수기", "신명기", "여호수아", "사사기", "룻기",
        "사무엘상", "사무엘하", "열왕기상", "열왕기하", "역대상", "역대하", "에스라", "느헤미야",
        "에스더", "욥기", "시편", "잠언", "전도서", "아가", "이사야", "예레미야",
        "예레미야애가", "에스겔", "다니엘", "호세아", "요엘", "아모스", "오바댜", "요나",
        "미가", "나훔", "하박국", "스바냐", "학개", "스가랴", "말라기",
        "마태복음", "마가복음", "누가복음", "요한복음", "사도행전", "로마서", "고린도전서",
        "고린도후서", "갈라디아서", "에베소서", "빌립보서", "골로새서", "데살로니가전서",
        "데살로니가후서", "디모데전서", "디모데후서", "디도서", "빌레몬서", "히브리서",
        "야고보서", "베드로전서", "베드로후서", "요한일서", "요한이서", "요한삼서", "유다서", "요한계시록"
    };

    private static final int[] CHAPTERS = {
        50, 40, 27, 36, 34, 24, 21, 4, 31, 24, 22, 25, 29, 36, 10, 13,
        10, 42, 150, 31, 12, 8, 66, 52, 5, 48, 12, 14, 3, 9, 1, 4,
        7, 3, 3, 3, 2, 14, 4,
        28, 16, 24, 21, 28, 16, 16, 13, 6, 6, 4, 4, 5, 3, 6, 4, 3,
        1, 13, 5, 5, 3, 5, 1, 1, 1, 22
    };

    /** CUMULATIVE[b] = 1~b권 장 수 합 (CUMULATIVE[0]=0, CUMULATIVE[66]=1189). */
    private static final int[] CUMULATIVE = new int[BOOK_COUNT + 1];

    static {
        for (int i = 0; i < BOOK_COUNT; i++) {
            CUMULATIVE[i + 1] = CUMULATIVE[i] + CHAPTERS[i];
        }
    }

    private BibleStructure() {}

    /** 구간 [startBook, endBook]의 총 장 수. */
    public static int chapterCount(int startBook, int endBook) {
        validateRange(startBook, endBook);
        return CUMULATIVE[endBook] - CUMULATIVE[startBook - 1];
    }

    /**
     * 구간 시작권 기준 ordinal(1-based)번째 장의 (권 이름, 장 번호).
     * 호출자는 ordinal ≤ 구간 장 수 불변식을 유지한다(구간 초과·성경 범위 내는 검증하지 않음).
     */
    public static BiblePosition locate(int startBook, int ordinal) {
        validateRange(startBook, startBook);
        int global = CUMULATIVE[startBook - 1] + ordinal; // 전역 1-based 장 번호
        if (ordinal < 1 || global > CUMULATIVE[BOOK_COUNT]) {
            throw new IllegalArgumentException("성경 범위를 벗어난 위치: startBook=" + startBook + ", ordinal=" + ordinal);
        }
        int book = 1;
        while (CUMULATIVE[book] < global) { // 최대 66회 선형 탐색 — 충분히 싸다
            book++;
        }
        return new BiblePosition(NAMES[book - 1], global - CUMULATIVE[book - 1]);
    }

    public static void validateRange(int startBook, int endBook) {
        if (startBook < 1 || endBook > BOOK_COUNT || startBook > endBook) {
            throw new IllegalArgumentException("잘못된 성경 구간: " + startBook + "~" + endBook);
        }
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.challenge.BibleStructureTest'`
Expected: PASS (12 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/elipair/church/domain/challenge/BibleStructure.java src/test/java/com/elipair/church/domain/challenge/BibleStructureTest.java
git commit -m "feat : 성경 66권 1189장 구조 상수(BibleStructure) 추가"
```

---

### Task 2: V13 마이그레이션 + 시드/인덱스 회귀 테스트 갱신

**Files:**
- Create: `src/main/resources/db/migration/V13__create_bible_challenges.sql`
- Modify: `src/test/java/com/elipair/church/MigrationIndexTest.java` (검증 2건 추가)
- Modify: `src/test/java/com/elipair/church/domain/role/RbacSeedIntegrityTest.java` (권한 12→14)

**Interfaces:**
- Produces: 테이블 `bible_challenges`·`challenge_participations`·`challenge_reading_logs`, 권한 `CHALLENGE_MANAGE`(SUPER_ADMIN·ADMIN)·`CHALLENGE_PARTICIPATE`(SUPER_ADMIN·ADMIN·MEMBER). Task 3 엔티티가 이 스키마에 매핑, Task 5·8 인가가 이 권한을 사용.

- [ ] **Step 1: 실패하는 테스트 먼저 — MigrationIndexTest에 2건 추가**

`MigrationIndexTest.java` 클래스 끝(기존 `bulletins_media_id_fk_is_on_delete_set_null` 테스트 뒤)에 추가:

```java
    @Test
    void bible_challenges_start_date_is_partial_on_active_rows() {
        assertThat(indexDef("idx_bible_challenges_start_date"))
                .as("V13 챌린지 목록 인덱스")
                .isNotNull()
                .contains("start_date")
                .contains("deleted_at IS NULL");
    }

    @Test
    void challenge_participations_unique_is_partial_on_active_rows() {
        assertThat(indexDef("uq_challenge_participations_active"))
                .as("V13 참여 부분 유니크(취소 후 재참여 허용)")
                .isNotNull()
                .contains("challenge_id")
                .contains("member_id")
                .contains("deleted_at IS NULL");
    }
```

- [ ] **Step 2: RbacSeedIntegrityTest 갱신 (14권한 기대로)**

`seeds_twelve_permissions` 테스트를 다음으로 교체(메서드명 포함):

```java
    @Test
    void seeds_fourteen_permissions() {
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
                        "CHALLENGE_PARTICIPATE");
    }
```

`role_permission_matrix_matches_spec`의 단언 3줄을 다음으로 교체:

```java
        assertThat(byName.get("SUPER_ADMIN").getPermissions()).hasSize(14);
        assertThat(byName.get("ADMIN").getPermissions()).hasSize(14);
        assertThat(byName.get("MEMBER").getPermissions())
                .extracting(Permission::getName)
                .containsExactlyInAnyOrder("GALLERY_VIEW", "CHALLENGE_PARTICIPATE");
```

(`USER` 단언 `isEmpty()`는 그대로.)

- [ ] **Step 3: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.MigrationIndexTest' --tests 'com.elipair.church.domain.role.RbacSeedIntegrityTest'`
Expected: FAIL — 신규 인덱스 없음 + 권한 14개 아님.

- [ ] **Step 4: V13 마이그레이션 작성**

`src/main/resources/db/migration/V13__create_bible_challenges.sql`:

```sql
-- 성경 통독 챌린지 D10 (docs/superpowers/specs/2026-07-06-bible-challenge-design.md).
-- 성경 구조(66권 1189장)는 코드 상수(BibleStructure) — 테이블 없음.
-- 종료일·하루 목표는 start_date/target_days에서 파생 — 저장하지 않는다.
CREATE TABLE bible_challenges (
    id          BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    title       VARCHAR(100) NOT NULL,
    description TEXT,
    start_book  INT          NOT NULL, -- 최종반영: 엔티티 int 매핑 정합(SMALLINT는 validate 불일치 — Task 4에서 수정)
    end_book    INT          NOT NULL,
    start_date  DATE         NOT NULL,
    target_days INT          NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP,
    created_by  BIGINT       REFERENCES members (id),
    updated_by  BIGINT       REFERENCES members (id),
    deleted_at  TIMESTAMP,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_bible_challenges_book_range
        CHECK (start_book BETWEEN 1 AND 66 AND end_book BETWEEN 1 AND 66 AND start_book <= end_book),
    CONSTRAINT ck_bible_challenges_target_days CHECK (target_days > 0)
);

-- 목록 기본 정렬 start_date DESC, 미삭제만(스펙 §6 부분 인덱스 관례).
CREATE INDEX idx_bible_challenges_start_date ON bible_challenges (start_date DESC) WHERE deleted_at IS NULL;

-- 참여 = 회원×챌린지, 현재 회독 포인터(chapters_read) + 완독 횟수(rounds_completed).
CREATE TABLE challenge_participations (
    id               BIGINT    GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    challenge_id     BIGINT    NOT NULL REFERENCES bible_challenges (id),
    member_id        BIGINT    NOT NULL REFERENCES members (id),
    chapters_read    INT       NOT NULL DEFAULT 0,
    rounds_completed INT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP,
    created_by       BIGINT    REFERENCES members (id),
    updated_by       BIGINT    REFERENCES members (id),
    deleted_at       TIMESTAMP,
    version          BIGINT    NOT NULL DEFAULT 0,
    CONSTRAINT ck_challenge_participations_counts CHECK (chapters_read >= 0 AND rounds_completed >= 0)
);

-- 중복 참여 방지 + 취소 후 재참여 허용(부분 유니크 — members.phone 관례).
CREATE UNIQUE INDEX uq_challenge_participations_active
    ON challenge_participations (challenge_id, member_id) WHERE deleted_at IS NULL;
-- 마이페이지 참여 이력 조회.
CREATE INDEX idx_challenge_participations_member
    ON challenge_participations (member_id) WHERE deleted_at IS NULL;

-- 날짜별 읽음 로그 — 하루 1행 누적, 취소는 물리 삭제(포인터의 파생 기록이라 소프트삭제 부적합).
CREATE TABLE challenge_reading_logs (
    id               BIGINT    GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    participation_id BIGINT    NOT NULL REFERENCES challenge_participations (id),
    read_date        DATE      NOT NULL,
    chapters         INT       NOT NULL,
    created_at       TIMESTAMP NOT NULL,
    CONSTRAINT ck_challenge_reading_logs_chapters CHECK (chapters > 0),
    CONSTRAINT uq_challenge_reading_logs_day UNIQUE (participation_id, read_date)
);

-- 권한 시드 2건 (V2 패턴): 관리자에겐 관리+참여 모두, MEMBER(승인 교인)에겐 참여만.
INSERT INTO permissions (name, description) VALUES
    ('CHALLENGE_MANAGE',      '통독 챌린지 관리'),
    ('CHALLENGE_PARTICIPATE', '통독 챌린지 참여');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r
JOIN permissions p ON p.name IN ('CHALLENGE_MANAGE', 'CHALLENGE_PARTICIPATE')
WHERE r.name IN ('SUPER_ADMIN', 'ADMIN');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r
JOIN permissions p ON p.name = 'CHALLENGE_PARTICIPATE'
WHERE r.name = 'MEMBER';
```

- [ ] **Step 5: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.MigrationIndexTest' --tests 'com.elipair.church.domain.role.RbacSeedIntegrityTest'`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V13__create_bible_challenges.sql src/test/java/com/elipair/church/MigrationIndexTest.java src/test/java/com/elipair/church/domain/role/RbacSeedIntegrityTest.java
git commit -m "feat : V13 통독 챌린지 테이블 3종·권한 2종 시드 추가"
```

---

### Task 3: 엔티티 3종 + ChallengeStatus + 리포지토리 3종

**Files:**
- Create: `src/main/java/com/elipair/church/domain/challenge/ChallengeStatus.java`
- Create: `src/main/java/com/elipair/church/domain/challenge/BibleChallenge.java`
- Create: `src/main/java/com/elipair/church/domain/challenge/ChallengeParticipation.java`
- Create: `src/main/java/com/elipair/church/domain/challenge/ChallengeReadingLog.java`
- Create: `src/main/java/com/elipair/church/domain/challenge/BibleChallengeRepository.java`
- Create: `src/main/java/com/elipair/church/domain/challenge/ChallengeParticipationRepository.java`
- Create: `src/main/java/com/elipair/church/domain/challenge/ChallengeReadingLogRepository.java`
- Test: `src/test/java/com/elipair/church/domain/challenge/ChallengeParticipationTest.java`

**Interfaces:**
- Consumes: Task 1의 `BibleStructure`, 기존 `global/common`의 `BaseEntity`(감사·소프트삭제·`@Version`)·`BaseTimeEntity`(createdAt만).
- Produces (Task 6·7·8이 사용):
  - `BibleChallenge.create(String title, String description, int startBook, int endBook, LocalDate startDate, int targetDays)`, `applyPatch(String, String, Integer, Integer, LocalDate, Integer)`(null=미변경), 파생 `totalChapters()`, `endDate()`, `dailyGoal()`, `status(LocalDate today)`.
  - `ChallengeParticipation.create(Long challengeId, Long memberId)`, `advance(int chapters, int scopeChapters)`, `rollback(int chapters, int scopeChapters)`, `totalChaptersRead(int scopeChapters)`.
  - `ChallengeReadingLog.create(Long participationId, LocalDate readDate, int chapters)`, `addChapters(int)`.
  - 리포지토리 메서드 시그니처는 Step 4 코드 그대로.

- [ ] **Step 1: 실패하는 테스트 — 포인터 산술(회독 이월·역이월)**

```java
package com.elipair.church.domain.challenge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 포인터 산술 — 회독 이월(설계 §3 read 4단계)·취소 역이월(설계 §3 취소). */
class ChallengeParticipationTest {

    @Test
    void advance_moves_pointer() {
        ChallengeParticipation p = ChallengeParticipation.create(1L, 2L);
        p.advance(12, 1189);
        assertThat(p.getChaptersRead()).isEqualTo(12);
        assertThat(p.getRoundsCompleted()).isZero();
    }

    @Test
    void advance_past_scope_completes_round_and_carries_over() {
        ChallengeParticipation p = ChallengeParticipation.create(1L, 2L);
        p.advance(1184, 1189);
        p.advance(12, 1189); // 5장 남았는데 12장 → 회독 +1, 새 포인터 7 (설계 §1 회독 이월)
        assertThat(p.getRoundsCompleted()).isEqualTo(1);
        assertThat(p.getChaptersRead()).isEqualTo(7);
    }

    @Test
    void advance_exactly_scope_resets_pointer_to_zero() {
        ChallengeParticipation p = ChallengeParticipation.create(1L, 2L);
        p.advance(10, 10);
        assertThat(p.getRoundsCompleted()).isEqualTo(1);
        assertThat(p.getChaptersRead()).isZero();
    }

    @Test
    void advance_can_complete_multiple_rounds_in_one_call() {
        ChallengeParticipation p = ChallengeParticipation.create(1L, 2L);
        p.advance(30, 10);
        assertThat(p.getRoundsCompleted()).isEqualTo(3);
        assertThat(p.getChaptersRead()).isZero();
    }

    @Test
    void rollback_reverses_across_round_boundary() {
        ChallengeParticipation p = ChallengeParticipation.create(1L, 2L);
        p.advance(1184, 1189);
        p.advance(12, 1189); // rounds=1, pointer=7
        p.rollback(12, 1189); // 원상복구 (설계 §3 취소: 회독 경계 역이월)
        assertThat(p.getRoundsCompleted()).isZero();
        assertThat(p.getChaptersRead()).isEqualTo(1184);
    }

    @Test
    void total_chapters_read_includes_completed_rounds() {
        ChallengeParticipation p = ChallengeParticipation.create(1L, 2L);
        p.advance(1189 + 7, 1189); // rounds=1, pointer=7
        assertThat(p.totalChaptersRead(1189)).isEqualTo(1196);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.challenge.ChallengeParticipationTest'`
Expected: 컴파일 실패.

- [ ] **Step 3: 엔티티·enum 구현**

`ChallengeStatus.java`:

```java
package com.elipair.church.domain.challenge;

import java.time.LocalDate;

/** 챌린지 파생 상태 — 날짜에서 계산, 컬럼 아님(설계 §1: 종료 조작 없음). */
public enum ChallengeStatus {
    UPCOMING,
    ONGOING,
    ENDED;

    public static ChallengeStatus of(LocalDate startDate, LocalDate endDate, LocalDate today) {
        if (today.isBefore(startDate)) {
            return UPCOMING;
        }
        if (today.isAfter(endDate)) {
            return ENDED;
        }
        return ONGOING;
    }
}
```

`BibleChallenge.java`:

```java
package com.elipair.church.domain.challenge;

import com.elipair.church.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 통독 챌린지(설계 §2). 범위는 연속 권 구간 [startBook, endBook](1~66) — 신약=40~66 등.
 * 종료일·하루 목표·상태는 전부 파생값(저장 안 함). 수정가능 콘텐츠라 BaseEntity 상속.
 */
@Entity
@Table(name = "bible_challenges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BibleChallenge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_book", nullable = false)
    private int startBook;

    @Column(name = "end_book", nullable = false)
    private int endBook;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "target_days", nullable = false)
    private int targetDays;

    private BibleChallenge(
            String title, String description, int startBook, int endBook, LocalDate startDate, int targetDays) {
        this.title = title;
        this.description = description;
        this.startBook = startBook;
        this.endBook = endBook;
        this.startDate = startDate;
        this.targetDays = targetDays;
    }

    public static BibleChallenge create(
            String title, String description, int startBook, int endBook, LocalDate startDate, int targetDays) {
        return new BibleChallenge(title, description, startBook, endBook, startDate, targetDays);
    }

    /** PATCH 부분 수정 — null 인자는 미변경. 참여자 존재 시 구간·기간 가드는 서비스가 수행(설계 §3). */
    public void applyPatch(
            String title,
            String description,
            Integer startBook,
            Integer endBook,
            LocalDate startDate,
            Integer targetDays) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (startBook != null) {
            this.startBook = startBook;
        }
        if (endBook != null) {
            this.endBook = endBook;
        }
        if (startDate != null) {
            this.startDate = startDate;
        }
        if (targetDays != null) {
            this.targetDays = targetDays;
        }
    }

    public int totalChapters() {
        return BibleStructure.chapterCount(startBook, endBook);
    }

    /** 종료일(포함) = start_date + target_days - 1. */
    public LocalDate endDate() {
        return startDate.plusDays(targetDays - 1L);
    }

    /** 하루 목표 = ⌈구간 장 수 / target_days⌉. */
    public int dailyGoal() {
        return Math.ceilDiv(totalChapters(), targetDays);
    }

    public ChallengeStatus status(LocalDate today) {
        return ChallengeStatus.of(startDate, endDate(), today);
    }
}
```

`ChallengeParticipation.java`:

```java
package com.elipair.church.domain.challenge;

import com.elipair.church.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 챌린지 참여(설계 §2). chaptersRead = 현재 회독의 포인터(0~구간 장 수), 회독 완료 시 초과분 이월.
 * member는 BaseEntity 관례대로 Long id 참조(연관 아님). 동시 클릭 방어는 상속받은 @Version.
 * 참여 취소 = 소프트삭제, (challenge_id, member_id) 부분 유니크로 재참여 허용.
 */
@Entity
@Table(name = "challenge_participations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChallengeParticipation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_id", nullable = false)
    private Long challengeId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "chapters_read", nullable = false)
    private int chaptersRead;

    @Column(name = "rounds_completed", nullable = false)
    private int roundsCompleted;

    private ChallengeParticipation(Long challengeId, Long memberId) {
        this.challengeId = challengeId;
        this.memberId = memberId;
        this.chaptersRead = 0;
        this.roundsCompleted = 0;
    }

    public static ChallengeParticipation create(Long challengeId, Long memberId) {
        return new ChallengeParticipation(challengeId, memberId);
    }

    /** 포인터 전진 — 구간 끝 도달 시 회독 +1·초과분 이월(여러 회독 한 번에 가능, 설계 §3). */
    public void advance(int chapters, int scopeChapters) {
        chaptersRead += chapters;
        while (chaptersRead >= scopeChapters) {
            chaptersRead -= scopeChapters;
            roundsCompleted++;
        }
    }

    /** 기록 취소 롤백 — 회독 경계 역이월(설계 §3). 로그 합계 불변식상 음수 회독은 불가능. */
    public void rollback(int chapters, int scopeChapters) {
        chaptersRead -= chapters;
        while (chaptersRead < 0) {
            chaptersRead += scopeChapters;
            roundsCompleted--;
        }
        if (roundsCompleted < 0) {
            throw new IllegalStateException("롤백이 기록 합계를 초과했습니다: participation=" + id);
        }
    }

    /** 누적 총 읽은 장 수(회독 포함) — 페이스 계산용. */
    public int totalChaptersRead(int scopeChapters) {
        return roundsCompleted * scopeChapters + chaptersRead;
    }
}
```

`ChallengeReadingLog.java`:

```java
package com.elipair.church.domain.challenge;

import com.elipair.church.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 날짜별 읽음 로그(설계 §2) — 하루 1행, 같은 날 추가 읽기는 chapters 누적.
 * 스트릭·히트맵·페이스가 전부 여기서 파생. 취소는 물리 삭제(소프트삭제·낙관락 불필요 → BaseTimeEntity만).
 */
@Entity
@Table(name = "challenge_reading_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChallengeReadingLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "participation_id", nullable = false)
    private Long participationId;

    @Column(name = "read_date", nullable = false)
    private LocalDate readDate;

    @Column(nullable = false)
    private int chapters;

    private ChallengeReadingLog(Long participationId, LocalDate readDate, int chapters) {
        this.participationId = participationId;
        this.readDate = readDate;
        this.chapters = chapters;
    }

    public static ChallengeReadingLog create(Long participationId, LocalDate readDate, int chapters) {
        return new ChallengeReadingLog(participationId, readDate, chapters);
    }

    /** 같은 날 추가 읽기 누적(설계 §3 read 3단계). */
    public void addChapters(int chapters) {
        this.chapters += chapters;
    }
}
```

- [ ] **Step 4: 리포지토리 3종**

`BibleChallengeRepository.java`:

```java
package com.elipair.church.domain.challenge;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BibleChallengeRepository extends JpaRepository<BibleChallenge, Long> {

    Optional<BibleChallenge> findByIdAndDeletedAtIsNull(Long id);

    Page<BibleChallenge> findAllByDeletedAtIsNull(Pageable pageable);
}
```

`ChallengeParticipationRepository.java`:

```java
package com.elipair.church.domain.challenge;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeParticipationRepository extends JpaRepository<ChallengeParticipation, Long> {

    Optional<ChallengeParticipation> findByChallengeIdAndMemberIdAndDeletedAtIsNull(Long challengeId, Long memberId);

    boolean existsByChallengeIdAndMemberIdAndDeletedAtIsNull(Long challengeId, Long memberId);

    /** 관리자 구간·기간 수정 가드(설계 §3): 참여자가 하나라도 있으면 구조 필드 수정 거부. */
    boolean existsByChallengeIdAndDeletedAtIsNull(Long challengeId);

    Page<ChallengeParticipation> findByMemberIdAndDeletedAtIsNull(Long memberId, Pageable pageable);
}
```

`ChallengeReadingLogRepository.java`:

```java
package com.elipair.church.domain.challenge;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeReadingLogRepository extends JpaRepository<ChallengeReadingLog, Long> {

    Optional<ChallengeReadingLog> findByParticipationIdAndReadDate(Long participationId, LocalDate readDate);

    List<ChallengeReadingLog> findByParticipationIdAndReadDateBetweenOrderByReadDateAsc(
            Long participationId, LocalDate from, LocalDate to);

    /** 스트릭 계산용 날짜 목록 — 참여당 최대 챌린지 일수 행이라 전량 로드로 충분. */
    @Query("select l.readDate from ChallengeReadingLog l "
            + "where l.participationId = :participationId order by l.readDate desc")
    List<LocalDate> findReadDatesDesc(@Param("participationId") Long participationId);
}
```

- [ ] **Step 5: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.challenge.ChallengeParticipationTest'`
Expected: PASS (6 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/elipair/church/domain/challenge/ src/test/java/com/elipair/church/domain/challenge/ChallengeParticipationTest.java
git commit -m "feat : 통독 챌린지 엔티티 3종·상태 enum·리포지토리 추가"
```

---

### Task 4: ClockConfig + APP_TIMEZONE

**Files:**
- Create: `src/main/java/com/elipair/church/global/config/ClockConfig.java`
- Modify: `src/main/resources/application.yml` (`view:` 블록 아래에 `app:` 블록 추가)

**Interfaces:**
- Produces: `java.time.Clock` 빈. Task 6·7 서비스가 주입받아 `LocalDate.now(clock)`으로 "오늘" 판정(설계 §5). 테스트는 `Clock.fixed(...)` 대체.

- [ ] **Step 1: application.yml에 추가** (파일 끝 `view:` 블록 다음)

```yaml
app:
  timezone: ${APP_TIMEZONE:Asia/Seoul} # 통독 챌린지 "오늘" 판정 기준 시간대(설계 §5)
```

- [ ] **Step 2: ClockConfig 작성**

```java
package com.elipair.church.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "오늘" 판정용 Clock(통독 챌린지 설계 §5). 시간대는 APP_TIMEZONE env(기본 Asia/Seoul) —
 * 멀티처치 템플릿 규율상 env 주입. 테스트는 Clock.fixed로 대체해 날짜를 고정한다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.timezone}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
```

(전용 테스트 없음 — 상수 배선이라 Task 7 서비스 테스트의 fixed Clock과 Task 8 통합 테스트가 실사용을 검증한다.)

- [ ] **Step 3: 컨텍스트 부팅 확인**

Run: `./gradlew test --tests 'com.elipair.church.ChurchBackendApplicationTests'`
Expected: PASS (Clock 빈 추가로 부팅 실패 없음)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/elipair/church/global/config/ClockConfig.java src/main/resources/application.yml
git commit -m "feat : APP_TIMEZONE 기반 Clock 빈 추가"
```

---

### Task 5: SecurityConfig 경로 규칙 + 경로 인가 테스트

**Files:**
- Modify: `src/main/java/com/elipair/church/global/config/SecurityConfig.java` (requestMatchers 1건 추가)
- Modify: `src/test/java/com/elipair/church/global/security/SecuredTestController.java` (ping 1건 추가)
- Modify: `src/test/java/com/elipair/church/global/security/SecurityConfigPathRulesTest.java` (테스트 3건 추가)

**Interfaces:**
- Produces: `/api/bible-challenges/**` → `CHALLENGE_PARTICIPATE` 필요(갤러리 패턴, 설계 §4). Task 8 회원 컨트롤러는 메서드 어노테이션 없이 이 경로 규칙에 의존.

- [ ] **Step 1: 실패하는 테스트 — SecuredTestController에 ping 추가**

`SecuredTestController.java`의 `galleryPing` 아래에 추가:

```java
    @GetMapping("/api/bible-challenges/ping")
    public String challengePing() {
        return "challenge";
    }
```

`SecurityConfigPathRulesTest.java`의 gallery 테스트 3건 뒤에 추가 (기존 `bearer(List<String>)` 헬퍼 재사용):

```java
    @Test
    void challenge_path_without_challenge_participate_is_403() throws Exception {
        mockMvc.perform(get("/api/bible-challenges/ping").header("Authorization", bearer(List.of("SERMON_WRITE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void challenge_path_with_challenge_participate_is_200() throws Exception {
        mockMvc.perform(get("/api/bible-challenges/ping")
                        .header("Authorization", bearer(List.of("CHALLENGE_PARTICIPATE"))))
                .andExpect(status().isOk());
    }

    @Test
    void challenge_path_anonymous_is_401_invalid_token() throws Exception {
        mockMvc.perform(get("/api/bible-challenges/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.SecurityConfigPathRulesTest'`
Expected: FAIL — 경로 규칙 없어서 ping이 permitAll(anyRequest)로 통과 → 403/401 기대 테스트 실패.

- [ ] **Step 3: SecurityConfig에 규칙 추가**

`securityFilterChain`의 `/api/gallery/**` 매처와 `.anyRequest()` 사이에 삽입:

```java
                        .requestMatchers("/api/bible-challenges/**")
                        .hasAuthority("CHALLENGE_PARTICIPATE")
```

클래스 상단 주석(경로 3분법 설명)에 `/api/bible-challenges/** CHALLENGE_PARTICIPATE,` 문구를 추가해 문서화를 동기화한다.

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.SecurityConfigPathRulesTest'`
Expected: PASS (기존 + 신규 3건)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/elipair/church/global/config/SecurityConfig.java src/test/java/com/elipair/church/global/security/
git commit -m "feat : /api/bible-challenges 경로 CHALLENGE_PARTICIPATE 인가 규칙 추가"
```

---

### Task 6: 관리자 서비스(BibleChallengeService) + 관련 DTO

**Files:**
- Create: `src/main/java/com/elipair/church/domain/challenge/dto/ChallengeCreateRequest.java`
- Create: `src/main/java/com/elipair/church/domain/challenge/dto/ChallengePatchRequest.java`
- Create: `src/main/java/com/elipair/church/domain/challenge/dto/ChallengeCardResponse.java`
- Create: `src/main/java/com/elipair/church/domain/challenge/dto/ChallengeDetailResponse.java`
- Create: `src/main/java/com/elipair/church/domain/challenge/BibleChallengeService.java`
- Test: `src/test/java/com/elipair/church/domain/challenge/BibleChallengeServiceTest.java`

**Interfaces:**
- Consumes: Task 3 엔티티·리포지토리, Task 4 `Clock`, 기존 `BusinessException(ErrorCode[, detail])`.
- Produces (Task 8 컨트롤러가 사용):
  - `Page<ChallengeCardResponse> list(Pageable)`
  - `ChallengeDetailResponse get(Long id, Long memberId)`
  - `ChallengeDetailResponse create(ChallengeCreateRequest)` / `patch(Long id, ChallengePatchRequest)` / `void delete(Long id)`
  - DTO 시그니처는 Step 3 코드 그대로.

- [ ] **Step 1: DTO 4종 작성**

`ChallengeCreateRequest.java`:

```java
package com.elipair.church.domain.challenge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 챌린지 개설(POST) 요청(설계 §3). startBook<=endBook 교차 검증은 서비스가 수행. */
public record ChallengeCreateRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 50000) String description,
        @NotNull @Min(1) @Max(66) Integer startBook,
        @NotNull @Min(1) @Max(66) Integer endBook,
        @NotNull LocalDate startDate,
        @NotNull @Min(1) @Max(3650) Integer targetDays) {}
```

`ChallengePatchRequest.java`:

```java
package com.elipair.church.domain.challenge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 챌린지 부분 수정(PATCH) 요청 — null 필드는 미변경. 구간·기간 필드는 참여자 존재 시 거부(설계 §3). */
public record ChallengePatchRequest(
        @Size(max = 100) String title,
        @Size(max = 50000) String description,
        @Min(1) @Max(66) Integer startBook,
        @Min(1) @Max(66) Integer endBook,
        LocalDate startDate,
        @Min(1) @Max(3650) Integer targetDays,
        @NotNull Long version) {}
```

`ChallengeCardResponse.java`:

```java
package com.elipair.church.domain.challenge.dto;

import com.elipair.church.domain.challenge.BibleChallenge;
import com.elipair.church.domain.challenge.ChallengeStatus;
import java.time.LocalDate;

/** 목록 카드(설계 §3) — 본문(description) 제외(목록 카드 관례). endDate·totalChapters·dailyGoal·status는 파생. */
public record ChallengeCardResponse(
        Long id,
        String title,
        int startBook,
        int endBook,
        LocalDate startDate,
        LocalDate endDate,
        int targetDays,
        int totalChapters,
        int dailyGoal,
        ChallengeStatus status) {

    public static ChallengeCardResponse from(BibleChallenge c, LocalDate today) {
        return new ChallengeCardResponse(
                c.getId(),
                c.getTitle(),
                c.getStartBook(),
                c.getEndBook(),
                c.getStartDate(),
                c.endDate(),
                c.getTargetDays(),
                c.totalChapters(),
                c.dailyGoal(),
                c.status(today));
    }
}
```

`ChallengeDetailResponse.java`:

```java
package com.elipair.church.domain.challenge.dto;

import com.elipair.church.domain.challenge.BibleChallenge;
import com.elipair.church.domain.challenge.ChallengeStatus;
import java.time.LocalDate;

/** 챌린지 상세(설계 §3). joined = 요청 회원의 참여 여부(회원 상세 조회에서만 의미 — 관리자 응답은 false 고정). */
public record ChallengeDetailResponse(
        Long id,
        String title,
        String description,
        int startBook,
        int endBook,
        LocalDate startDate,
        LocalDate endDate,
        int targetDays,
        int totalChapters,
        int dailyGoal,
        ChallengeStatus status,
        boolean joined,
        Long version) {

    public static ChallengeDetailResponse from(BibleChallenge c, LocalDate today, boolean joined) {
        return new ChallengeDetailResponse(
                c.getId(),
                c.getTitle(),
                c.getDescription(),
                c.getStartBook(),
                c.getEndBook(),
                c.getStartDate(),
                c.endDate(),
                c.getTargetDays(),
                c.totalChapters(),
                c.dailyGoal(),
                c.status(today),
                joined,
                c.getVersion());
    }
}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

```java
package com.elipair.church.domain.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.challenge.dto.ChallengeCreateRequest;
import com.elipair.church.domain.challenge.dto.ChallengeDetailResponse;
import com.elipair.church.domain.challenge.dto.ChallengePatchRequest;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BibleChallengeServiceTest {

    /** 고정 "오늘" = 2026-07-06 (KST). */
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 6);

    private BibleChallengeRepository repository;
    private ChallengeParticipationRepository participationRepository;
    private BibleChallengeService service;

    @BeforeEach
    void init() {
        repository = mock(BibleChallengeRepository.class);
        participationRepository = mock(ChallengeParticipationRepository.class);
        service = new BibleChallengeService(repository, participationRepository, FIXED);
    }

    private BibleChallenge ntChallenge() {
        // 신약 60일: 6/27 시작(오늘 10일차), 260장, 하루 목표 ceil(260/60)=5
        return BibleChallenge.create("학생부 신약 60일", "설명", 40, 66, LocalDate.of(2026, 6, 27), 60);
    }

    @Test
    void create_returns_derived_fields() {
        BibleChallenge saved = ntChallenge();
        when(repository.save(any(BibleChallenge.class))).thenReturn(saved);

        ChallengeDetailResponse res = service.create(
                new ChallengeCreateRequest("학생부 신약 60일", "설명", 40, 66, LocalDate.of(2026, 6, 27), 60));

        assertThat(res.totalChapters()).isEqualTo(260);
        assertThat(res.dailyGoal()).isEqualTo(5);
        assertThat(res.endDate()).isEqualTo(LocalDate.of(2026, 8, 25)); // 6/27 + 60일 - 1
        assertThat(res.status()).isEqualTo(ChallengeStatus.ONGOING);
        assertThat(res.joined()).isFalse();
    }

    @Test
    void create_with_inverted_book_range_throws_400() {
        assertThatThrownBy(() -> service.create(
                        new ChallengeCreateRequest("역순", null, 66, 40, TODAY, 100)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(repository, never()).save(any());
    }

    @Test
    void patch_with_stale_version_throws_409() {
        BibleChallenge c = ntChallenge();
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.patch(1L, new ChallengePatchRequest("새제목", null, null, null, null, null, 99L)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }

    @Test
    void patch_structure_field_with_participants_throws_400() {
        BibleChallenge c = mock(BibleChallenge.class);
        when(c.getVersion()).thenReturn(0L);
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(c));
        when(participationRepository.existsByChallengeIdAndDeletedAtIsNull(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.patch(1L, new ChallengePatchRequest(null, null, null, null, null, 120, 0L)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(c, never()).applyPatch(any(), any(), any(), any(), any(), any());
    }

    @Test
    void patch_title_only_with_participants_is_allowed() {
        BibleChallenge c = ntChallenge();
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(c));
        when(participationRepository.existsByChallengeIdAndDeletedAtIsNull(1L)).thenReturn(true);

        ChallengeDetailResponse res = service.patch(1L, new ChallengePatchRequest("바뀐 제목", null, null, null, null, null, 0L));

        assertThat(res.title()).isEqualTo("바뀐 제목");
        verify(repository).flush();
    }

    @Test
    void patch_resulting_in_inverted_range_throws_400() {
        BibleChallenge c = ntChallenge(); // startBook=40
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(c));
        when(participationRepository.existsByChallengeIdAndDeletedAtIsNull(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.patch(1L, new ChallengePatchRequest(null, null, null, 39, null, null, 0L)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void get_unknown_throws_404() {
        when(repository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(9L, 2L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void get_marks_joined_for_participant() {
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ntChallenge()));
        when(participationRepository.existsByChallengeIdAndMemberIdAndDeletedAtIsNull(1L, 2L)).thenReturn(true);

        assertThat(service.get(1L, 2L).joined()).isTrue();
    }

    @Test
    void delete_soft_deletes() {
        BibleChallenge c = mock(BibleChallenge.class);
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(c));

        service.delete(1L);

        verify(c).softDelete();
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.challenge.BibleChallengeServiceTest'`
Expected: 컴파일 실패 — `BibleChallengeService` 미존재.

- [ ] **Step 4: 서비스 구현**

```java
package com.elipair.church.domain.challenge;

import com.elipair.church.domain.challenge.dto.ChallengeCardResponse;
import com.elipair.church.domain.challenge.dto.ChallengeCreateRequest;
import com.elipair.church.domain.challenge.dto.ChallengeDetailResponse;
import com.elipair.church.domain.challenge.dto.ChallengePatchRequest;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 챌린지 관리 CRUD + 목록/상세(설계 §3). 진행 기록은 ChallengeProgressService 담당.
 * 참여자가 있는 챌린지의 구간·기간 수정은 진행률 의미를 깨므로 거부(400). 낙관락은 명시 version 비교 + flush(Notice 선례).
 */
@Service
@Transactional(readOnly = true)
public class BibleChallengeService {

    private final BibleChallengeRepository repository;
    private final ChallengeParticipationRepository participationRepository;
    private final Clock clock;

    public BibleChallengeService(
            BibleChallengeRepository repository,
            ChallengeParticipationRepository participationRepository,
            Clock clock) {
        this.repository = repository;
        this.participationRepository = participationRepository;
        this.clock = clock;
    }

    public Page<ChallengeCardResponse> list(Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        return repository.findAllByDeletedAtIsNull(pageable).map(c -> ChallengeCardResponse.from(c, today));
    }

    public ChallengeDetailResponse get(Long id, Long memberId) {
        BibleChallenge challenge = load(id);
        boolean joined = participationRepository.existsByChallengeIdAndMemberIdAndDeletedAtIsNull(id, memberId);
        return ChallengeDetailResponse.from(challenge, LocalDate.now(clock), joined);
    }

    @Transactional
    public ChallengeDetailResponse create(ChallengeCreateRequest req) {
        validateBookRange(req.startBook(), req.endBook());
        BibleChallenge challenge = repository.save(BibleChallenge.create(
                req.title(), req.description(), req.startBook(), req.endBook(), req.startDate(), req.targetDays()));
        return ChallengeDetailResponse.from(challenge, LocalDate.now(clock), false);
    }

    @Transactional
    public ChallengeDetailResponse patch(Long id, ChallengePatchRequest req) {
        BibleChallenge challenge = load(id);
        checkVersion(challenge, req.version());
        boolean structureChange = req.startBook() != null
                || req.endBook() != null
                || req.startDate() != null
                || req.targetDays() != null;
        if (structureChange && participationRepository.existsByChallengeIdAndDeletedAtIsNull(id)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "참여자가 있는 챌린지의 구간·기간은 수정할 수 없습니다");
        }
        if (structureChange) {
            int newStart = req.startBook() != null ? req.startBook() : challenge.getStartBook();
            int newEnd = req.endBook() != null ? req.endBook() : challenge.getEndBook();
            validateBookRange(newStart, newEnd);
        }
        challenge.applyPatch(
                req.title(), req.description(), req.startBook(), req.endBook(), req.startDate(), req.targetDays());
        repository.flush(); // 응답 version post-increment (Notice/Sermon 선례)
        return ChallengeDetailResponse.from(challenge, LocalDate.now(clock), false);
    }

    @Transactional
    public void delete(Long id) {
        load(id).softDelete();
        // 참여·로그는 이력 보존을 위해 그대로 둔다 — 마이페이지 이력에는 남고 신규 기록은 404(설계 §3 delete).
    }

    private BibleChallenge load(Long id) {
        return repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** null-safe — 미영속 엔티티(version null)는 0으로 비교(단위 테스트가 새 엔티티를 그대로 쓴다). */
    private void checkVersion(BibleChallenge challenge, Long expected) {
        Long current = challenge.getVersion() == null ? 0L : challenge.getVersion();
        if (!current.equals(expected)) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private void validateBookRange(int startBook, int endBook) {
        if (startBook > endBook) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "startBook은 endBook보다 클 수 없습니다");
        }
    }
}
```

- [ ] **Step 5: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.challenge.BibleChallengeServiceTest'`
Expected: PASS (9 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/elipair/church/domain/challenge/ src/test/java/com/elipair/church/domain/challenge/BibleChallengeServiceTest.java
git commit -m "feat : 챌린지 관리 서비스(개설·수정 가드·소프트삭제) 추가"
```

---

### Task 7: 진행 서비스(ChallengeProgressService) + 관련 DTO

**Files:**
- Create: `src/main/java/com/elipair/church/domain/challenge/dto/ChallengeSummaryResponse.java`
- Create: `src/main/java/com/elipair/church/domain/challenge/dto/ChallengeReadRequest.java`
- Create: `src/main/java/com/elipair/church/domain/challenge/dto/BiblePositionResponse.java`
- Create: `src/main/java/com/elipair/church/domain/challenge/dto/MyProgressResponse.java`
- Create: `src/main/java/com/elipair/church/domain/challenge/dto/ReadingLogResponse.java`
- Create: `src/main/java/com/elipair/church/domain/challenge/dto/MyParticipationResponse.java`
- Create: `src/main/java/com/elipair/church/domain/challenge/ChallengeProgressService.java`
- Test: `src/test/java/com/elipair/church/domain/challenge/ChallengeProgressServiceTest.java`

**Interfaces:**
- Consumes: Task 1 `BibleStructure.locate`, Task 3 엔티티·리포지토리 전부, Task 4 `Clock`.
- Produces (Task 8 컨트롤러가 사용):
  - `MyProgressResponse join(Long challengeId, Long memberId)`
  - `MyProgressResponse read(Long challengeId, Long memberId, ChallengeReadRequest req)`
  - `MyProgressResponse cancelRead(Long challengeId, Long memberId, LocalDate date)` (date null=오늘)
  - `MyProgressResponse myProgress(Long challengeId, Long memberId)`
  - `List<ReadingLogResponse> myLogs(Long challengeId, Long memberId, LocalDate from, LocalDate to)` (null=챌린지 기간)
  - `Page<MyParticipationResponse> myParticipations(Long memberId, Pageable pageable)`

- [ ] **Step 1: DTO 6종 작성**

`ChallengeSummaryResponse.java`:

```java
package com.elipair.church.domain.challenge.dto;

import com.elipair.church.domain.challenge.BibleChallenge;
import com.elipair.church.domain.challenge.ChallengeStatus;
import java.time.LocalDate;

/** 진행 응답에 내장되는 챌린지 요약(설계 §3 my-progress / my-participations). */
public record ChallengeSummaryResponse(
        Long id, String title, LocalDate startDate, LocalDate endDate, ChallengeStatus status, int totalChapters) {

    public static ChallengeSummaryResponse from(BibleChallenge c, LocalDate today) {
        return new ChallengeSummaryResponse(
                c.getId(), c.getTitle(), c.getStartDate(), c.endDate(), c.status(today), c.totalChapters());
    }
}
```

`ChallengeReadRequest.java`:

```java
package com.elipair.church.domain.challenge.dto;

import jakarta.validation.constraints.Min;
import java.time.LocalDate;

/**
 * 읽음 기록 요청(설계 §3 read) — 둘 다 생략 가능.
 * chapters 기본 = 해당 날짜의 남은 목표치, date 기본 = 오늘(소급 = 챌린지 시작일~오늘).
 */
public record ChallengeReadRequest(@Min(1) Integer chapters, LocalDate date) {}
```

`BiblePositionResponse.java`:

```java
package com.elipair.church.domain.challenge.dto;

/** 현재 읽은 위치 — 한글 권 이름 + 장(설계 §3 currentPosition). */
public record BiblePositionResponse(String book, int chapter) {}
```

`MyProgressResponse.java`:

```java
package com.elipair.church.domain.challenge.dto;

/**
 * 대시보드 원샷 응답(설계 §3 my-progress) — UI가 한 번의 호출로 전부 그린다.
 * currentPosition null = 현재 회독 시작 전(포인터 0). paceDays null = 기간 종료(ENDED).
 */
public record MyProgressResponse(
        double progressRate,
        BiblePositionResponse currentPosition,
        int chaptersRead,
        int totalChapters,
        int todayChapters,
        int dailyGoal,
        boolean todayDone,
        int streakDays,
        int roundsCompleted,
        Integer paceDays,
        ChallengeSummaryResponse challenge) {}
```

`ReadingLogResponse.java`:

```java
package com.elipair.church.domain.challenge.dto;

import java.time.LocalDate;

/** 달력 히트맵용 날짜별 로그(설계 §3 my-logs). */
public record ReadingLogResponse(LocalDate readDate, int chapters) {}
```

`MyParticipationResponse.java`:

```java
package com.elipair.church.domain.challenge.dto;

import java.time.LocalDate;

/** 마이페이지 참여 이력 1건(설계 §3 my-participations). completed = 회독 1회 이상. */
public record MyParticipationResponse(
        ChallengeSummaryResponse challenge,
        LocalDate joinedAt,
        double progressRate,
        int chaptersRead,
        int roundsCompleted,
        boolean completed,
        int streakDays) {}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

```java
package com.elipair.church.domain.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.challenge.dto.ChallengeReadRequest;
import com.elipair.church.domain.challenge.dto.MyProgressResponse;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChallengeProgressServiceTest {

    /** 고정 "오늘" = 2026-07-06 (KST). */
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 6);

    private BibleChallengeRepository challengeRepository;
    private ChallengeParticipationRepository participationRepository;
    private ChallengeReadingLogRepository logRepository;
    private ChallengeProgressService service;

    @BeforeEach
    void init() {
        challengeRepository = mock(BibleChallengeRepository.class);
        participationRepository = mock(ChallengeParticipationRepository.class);
        logRepository = mock(ChallengeReadingLogRepository.class);
        service = new ChallengeProgressService(challengeRepository, participationRepository, logRepository, FIXED);
        when(logRepository.findReadDatesDesc(any())).thenReturn(List.of());
        when(logRepository.findByParticipationIdAndReadDate(any(), any())).thenReturn(Optional.empty());
    }

    /** 신약 60일: 6/27 시작(오늘 10일차), 260장, 하루 목표 5. */
    private BibleChallenge ntChallenge() {
        return BibleChallenge.create("신약 60일", null, 40, 66, LocalDate.of(2026, 6, 27), 60);
    }

    private ChallengeParticipation stubJoined(BibleChallenge challenge) {
        ChallengeParticipation p = ChallengeParticipation.create(1L, 2L);
        when(challengeRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(challenge));
        when(participationRepository.findByChallengeIdAndMemberIdAndDeletedAtIsNull(1L, 2L))
                .thenReturn(Optional.of(p));
        return p;
    }

    // ---- join ----

    @Test
    void join_duplicate_throws_409() {
        when(challengeRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ntChallenge()));
        when(participationRepository.existsByChallengeIdAndMemberIdAndDeletedAtIsNull(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.join(1L, 2L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
        verify(participationRepository, never()).save(any());
    }

    @Test
    void join_unknown_challenge_throws_404() {
        when(challengeRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(9L, 2L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // ---- read: 기본값·누적·검증 ----

    @Test
    void read_without_chapters_fills_remaining_daily_goal() {
        ChallengeParticipation p = stubJoined(ntChallenge());

        MyProgressResponse res = service.read(1L, 2L, new ChallengeReadRequest(null, null));

        assertThat(res.chaptersRead()).isEqualTo(5); // dailyGoal 5
        assertThat(res.todayChapters()).isEqualTo(5);
        assertThat(res.todayDone()).isTrue();
        verify(logRepository).save(any(ChallengeReadingLog.class));
        assertThat(p.getChaptersRead()).isEqualTo(5);
    }

    @Test
    void read_accumulates_on_same_day_log() {
        stubJoined(ntChallenge());
        ChallengeReadingLog existing = ChallengeReadingLog.create(null, TODAY, 5);
        when(logRepository.findByParticipationIdAndReadDate(any(), eq(TODAY))).thenReturn(Optional.of(existing));

        service.read(1L, 2L, new ChallengeReadRequest(3, null));

        assertThat(existing.getChapters()).isEqualTo(8);
        verify(logRepository, never()).save(any());
    }

    @Test
    void read_default_after_goal_met_throws_400() {
        stubJoined(ntChallenge());
        when(logRepository.findByParticipationIdAndReadDate(any(), eq(TODAY)))
                .thenReturn(Optional.of(ChallengeReadingLog.create(null, TODAY, 5)));

        assertThatThrownBy(() -> service.read(1L, 2L, new ChallengeReadRequest(null, null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void read_backfill_past_date_within_challenge_is_ok() {
        ChallengeParticipation p = stubJoined(ntChallenge());
        LocalDate backfill = LocalDate.of(2026, 7, 4);

        service.read(1L, 2L, new ChallengeReadRequest(5, backfill));

        assertThat(p.getChaptersRead()).isEqualTo(5);
        verify(logRepository).save(any(ChallengeReadingLog.class));
    }

    @Test
    void read_before_start_or_future_throws_400() {
        stubJoined(ntChallenge());

        assertThatThrownBy(() -> service.read(1L, 2L, new ChallengeReadRequest(5, LocalDate.of(2026, 6, 26))))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> service.read(1L, 2L, new ChallengeReadRequest(5, TODAY.plusDays(1))))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void read_not_joined_throws_404() {
        when(challengeRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ntChallenge()));
        when(participationRepository.findByChallengeIdAndMemberIdAndDeletedAtIsNull(1L, 2L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.read(1L, 2L, new ChallengeReadRequest(5, null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // ---- read: 회독 이월 / 위치 ----

    @Test
    void read_rollover_completes_round_and_resets_position() {
        // 오바댜(31권, 1장)만 — 구간 1장이라 3장 읽으면 3회독
        BibleChallenge tiny = BibleChallenge.create("오바댜 반복", null, 31, 31, LocalDate.of(2026, 7, 1), 30);
        stubJoined(tiny);

        MyProgressResponse res = service.read(1L, 2L, new ChallengeReadRequest(3, null));

        assertThat(res.roundsCompleted()).isEqualTo(3);
        assertThat(res.chaptersRead()).isZero();
        assertThat(res.currentPosition()).isNull(); // 새 회독 시작 전
        assertThat(res.progressRate()).isZero();
    }

    @Test
    void progress_current_position_crosses_book_boundary() {
        BibleChallenge full = BibleChallenge.create("전체 100일", null, 1, 66, LocalDate.of(2026, 6, 27), 100);
        ChallengeParticipation p = stubJoined(full);
        p.advance(57, 1189); // 창세기 50 + 출애굽기 7

        MyProgressResponse res = service.myProgress(1L, 2L);

        assertThat(res.currentPosition().book()).isEqualTo("출애굽기");
        assertThat(res.currentPosition().chapter()).isEqualTo(7);
    }

    // ---- cancel ----

    @Test
    void cancel_rolls_back_pointer_and_deletes_log() {
        ChallengeParticipation p = stubJoined(ntChallenge());
        p.advance(8, 260);
        ChallengeReadingLog log = ChallengeReadingLog.create(null, TODAY, 8);
        when(logRepository.findByParticipationIdAndReadDate(any(), eq(TODAY))).thenReturn(Optional.of(log));

        MyProgressResponse res = service.cancelRead(1L, 2L, null);

        assertThat(res.chaptersRead()).isZero();
        verify(logRepository).delete(log);
    }

    @Test
    void cancel_without_log_throws_404() {
        stubJoined(ntChallenge());

        assertThatThrownBy(() -> service.cancelRead(1L, 2L, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // ---- streak / pace ----

    @Test
    void streak_counts_from_yesterday_when_today_not_logged() {
        stubJoined(ntChallenge());
        when(logRepository.findReadDatesDesc(any()))
                .thenReturn(List.of(TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(3)));

        assertThat(service.myProgress(1L, 2L).streakDays()).isEqualTo(3);
    }

    @Test
    void streak_includes_today_when_logged() {
        stubJoined(ntChallenge());
        when(logRepository.findReadDatesDesc(any())).thenReturn(List.of(TODAY, TODAY.minusDays(1)));

        assertThat(service.myProgress(1L, 2L).streakDays()).isEqualTo(2);
    }

    @Test
    void streak_zero_when_gap_before_yesterday() {
        stubJoined(ntChallenge());
        when(logRepository.findReadDatesDesc(any())).thenReturn(List.of(TODAY.minusDays(3)));

        assertThat(service.myProgress(1L, 2L).streakDays()).isZero();
    }

    @Test
    void pace_negative_when_behind_schedule() {
        // 10일차, 하루 5장 → 예정 50장. 실제 30장 → -20/5 = -4일
        ChallengeParticipation p = stubJoined(ntChallenge());
        p.advance(30, 260);

        assertThat(service.myProgress(1L, 2L).paceDays()).isEqualTo(-4);
    }

    @Test
    void pace_null_when_ended() {
        BibleChallenge ended = BibleChallenge.create("끝난 챌린지", null, 40, 66, LocalDate.of(2026, 1, 1), 60);
        stubJoined(ended);

        assertThat(service.myProgress(1L, 2L).paceDays()).isNull();
    }

    // ---- myLogs ----

    @Test
    void my_logs_defaults_to_challenge_period() {
        stubJoined(ntChallenge());
        when(logRepository.findByParticipationIdAndReadDateBetweenOrderByReadDateAsc(
                        any(), eq(LocalDate.of(2026, 6, 27)), eq(LocalDate.of(2026, 8, 25))))
                .thenReturn(List.of(ChallengeReadingLog.create(null, TODAY, 5)));

        assertThat(service.myLogs(1L, 2L, null, null)).hasSize(1);
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.challenge.ChallengeProgressServiceTest'`
Expected: 컴파일 실패.

- [ ] **Step 4: 서비스 구현**

```java
package com.elipair.church.domain.challenge;

import com.elipair.church.domain.challenge.dto.BiblePositionResponse;
import com.elipair.church.domain.challenge.dto.ChallengeReadRequest;
import com.elipair.church.domain.challenge.dto.ChallengeSummaryResponse;
import com.elipair.church.domain.challenge.dto.MyParticipationResponse;
import com.elipair.church.domain.challenge.dto.MyProgressResponse;
import com.elipair.church.domain.challenge.dto.ReadingLogResponse;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통독 진행 기록(설계 §3): 참여·읽음(소급 포함)·취소·대시보드·히트맵 로그·마이페이지 이력.
 * "오늘" = Clock(APP_TIMEZONE) 기준. 동시 클릭은 participation @Version → 409(전역 핸들러).
 * ENDED 챌린지도 기록 허용(늦은 완주 응원) — 단 소프트삭제된 챌린지는 404.
 */
@Service
@Transactional(readOnly = true)
public class ChallengeProgressService {

    private final BibleChallengeRepository challengeRepository;
    private final ChallengeParticipationRepository participationRepository;
    private final ChallengeReadingLogRepository logRepository;
    private final Clock clock;

    public ChallengeProgressService(
            BibleChallengeRepository challengeRepository,
            ChallengeParticipationRepository participationRepository,
            ChallengeReadingLogRepository logRepository,
            Clock clock) {
        this.challengeRepository = challengeRepository;
        this.participationRepository = participationRepository;
        this.logRepository = logRepository;
        this.clock = clock;
    }

    @Transactional
    public MyProgressResponse join(Long challengeId, Long memberId) {
        BibleChallenge challenge = loadChallenge(challengeId);
        if (participationRepository.existsByChallengeIdAndMemberIdAndDeletedAtIsNull(challengeId, memberId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "이미 참여 중인 챌린지입니다");
        }
        ChallengeParticipation participation =
                participationRepository.save(ChallengeParticipation.create(challengeId, memberId));
        return progressOf(challenge, participation);
    }

    @Transactional
    public MyProgressResponse read(Long challengeId, Long memberId, ChallengeReadRequest req) {
        BibleChallenge challenge = loadChallenge(challengeId);
        ChallengeParticipation participation = loadParticipation(challengeId, memberId);
        LocalDate today = LocalDate.now(clock);
        LocalDate date = req.date() != null ? req.date() : today;
        if (date.isBefore(challenge.getStartDate()) || date.isAfter(today)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "기록 날짜는 챌린지 시작일부터 오늘까지만 가능합니다");
        }
        ChallengeReadingLog existing =
                logRepository.findByParticipationIdAndReadDate(participation.getId(), date).orElse(null);
        int logged = existing != null ? existing.getChapters() : 0;
        int chapters = req.chapters() != null ? req.chapters() : Math.max(challenge.dailyGoal() - logged, 0);
        if (chapters <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "해당 날짜의 목표를 이미 달성했습니다. 장 수를 직접 지정하세요");
        }
        if (existing != null) {
            existing.addChapters(chapters);
        } else {
            logRepository.save(ChallengeReadingLog.create(participation.getId(), date, chapters));
        }
        participation.advance(chapters, challenge.totalChapters());
        // 오늘 기록이면 방금 계산한 값을 그대로 전달 — 재조회는 미플러시 상태에 의존하고 단위 테스트도 어렵다.
        Integer todayOverride = date.equals(today) ? logged + chapters : null;
        return progressOf(challenge, participation, todayOverride);
    }

    @Transactional
    public MyProgressResponse cancelRead(Long challengeId, Long memberId, LocalDate date) {
        BibleChallenge challenge = loadChallenge(challengeId);
        ChallengeParticipation participation = loadParticipation(challengeId, memberId);
        LocalDate target = date != null ? date : LocalDate.now(clock);
        ChallengeReadingLog log = logRepository
                .findByParticipationIdAndReadDate(participation.getId(), target)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        participation.rollback(log.getChapters(), challenge.totalChapters());
        logRepository.delete(log);
        // 오늘 기록을 지웠으면 todayChapters=0 확정 — 삭제 플러시 전 재조회에 의존하지 않는다.
        Integer todayOverride = target.equals(LocalDate.now(clock)) ? 0 : null;
        return progressOf(challenge, participation, todayOverride);
    }

    public MyProgressResponse myProgress(Long challengeId, Long memberId) {
        BibleChallenge challenge = loadChallenge(challengeId);
        return progressOf(challenge, loadParticipation(challengeId, memberId));
    }

    public List<ReadingLogResponse> myLogs(Long challengeId, Long memberId, LocalDate from, LocalDate to) {
        BibleChallenge challenge = loadChallenge(challengeId);
        ChallengeParticipation participation = loadParticipation(challengeId, memberId);
        LocalDate start = from != null ? from : challenge.getStartDate();
        LocalDate end = to != null ? to : challenge.endDate();
        return logRepository
                .findByParticipationIdAndReadDateBetweenOrderByReadDateAsc(participation.getId(), start, end)
                .stream()
                .map(l -> new ReadingLogResponse(l.getReadDate(), l.getChapters()))
                .toList();
    }

    public Page<MyParticipationResponse> myParticipations(Long memberId, Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        Page<ChallengeParticipation> page =
                participationRepository.findByMemberIdAndDeletedAtIsNull(memberId, pageable);
        // 소프트삭제된 챌린지도 이력에 표시(참여 기록 보존) — findAllById는 deleted_at을 거르지 않는다.
        Map<Long, BibleChallenge> challenges = challengeRepository
                .findAllById(page.map(ChallengeParticipation::getChallengeId).getContent())
                .stream()
                .collect(Collectors.toMap(BibleChallenge::getId, Function.identity()));
        // ponytail: 페이지당 참여별 스트릭 조회 N회(기본 10) — 인덱스 조회라 충분, 병목 시 일괄 조회로 교체.
        return page.map(p -> {
            BibleChallenge c = challenges.get(p.getChallengeId());
            return new MyParticipationResponse(
                    ChallengeSummaryResponse.from(c, today),
                    p.getCreatedAt().toLocalDate(),
                    progressRate(p, c),
                    p.getChaptersRead(),
                    p.getRoundsCompleted(),
                    p.getRoundsCompleted() >= 1,
                    streak(p.getId(), today));
        });
    }

    // ---- 조립 ----

    private MyProgressResponse progressOf(BibleChallenge challenge, ChallengeParticipation p) {
        return progressOf(challenge, p, null);
    }

    /** todayChaptersOverride: read/cancel이 방금 계산한 오늘 장 수 — null이면 로그에서 조회(join/myProgress 경로). */
    private MyProgressResponse progressOf(
            BibleChallenge challenge, ChallengeParticipation p, Integer todayChaptersOverride) {
        LocalDate today = LocalDate.now(clock);
        int dailyGoal = challenge.dailyGoal();
        int todayChapters = todayChaptersOverride != null
                ? todayChaptersOverride
                : logRepository
                        .findByParticipationIdAndReadDate(p.getId(), today)
                        .map(ChallengeReadingLog::getChapters)
                        .orElse(0);
        BiblePositionResponse position = null;
        if (p.getChaptersRead() > 0) {
            BibleStructure.BiblePosition located = BibleStructure.locate(challenge.getStartBook(), p.getChaptersRead());
            position = new BiblePositionResponse(located.book(), located.chapter());
        }
        return new MyProgressResponse(
                progressRate(p, challenge),
                position,
                p.getChaptersRead(),
                challenge.totalChapters(),
                todayChapters,
                dailyGoal,
                todayChapters >= dailyGoal,
                streak(p.getId(), today),
                p.getRoundsCompleted(),
                paceDays(challenge, p, today),
                ChallengeSummaryResponse.from(challenge, today));
    }

    /** 현재 회독 기준 %, 소수 1자리 반올림(설계 §3). */
    private double progressRate(ChallengeParticipation p, BibleChallenge challenge) {
        return Math.round(p.getChaptersRead() * 1000.0 / challenge.totalChapters()) / 10.0;
    }

    /** 연속 기록 일수 — 오늘 기록이 없으면 어제부터 역산(오늘은 아직 기회가 있다). 소급 백필로 치유된다(설계 §3). */
    private int streak(Long participationId, LocalDate today) {
        Set<LocalDate> dates = Set.copyOf(logRepository.findReadDatesDesc(participationId));
        LocalDate cursor = dates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** 예정 대비 앞섬(+)/뒤처짐(-) 일수. ENDED면 null. 늦은 참여자도 챌린지 시작일 기준(공동 챌린지, 설계 §3). */
    private Integer paceDays(BibleChallenge challenge, ChallengeParticipation p, LocalDate today) {
        if (challenge.status(today) == ChallengeStatus.ENDED) {
            return null;
        }
        long elapsedDays = Math.min(
                Math.max(ChronoUnit.DAYS.between(challenge.getStartDate(), today) + 1, 0),
                challenge.getTargetDays());
        long expected = Math.min(elapsedDays * challenge.dailyGoal(), challenge.totalChapters());
        long actual = p.totalChaptersRead(challenge.totalChapters());
        return Math.toIntExact(Math.round((actual - expected) / (double) challenge.dailyGoal()));
    }

    private BibleChallenge loadChallenge(Long id) {
        return challengeRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private ChallengeParticipation loadParticipation(Long challengeId, Long memberId) {
        return participationRepository
                .findByChallengeIdAndMemberIdAndDeletedAtIsNull(challengeId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
```

- [ ] **Step 5: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.challenge.ChallengeProgressServiceTest'`
Expected: PASS (18 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/elipair/church/domain/challenge/ src/test/java/com/elipair/church/domain/challenge/ChallengeProgressServiceTest.java
git commit -m "feat : 통독 진행 서비스(읽음 기록·소급·취소·스트릭·페이스) 추가"
```

---

### Task 8: 컨트롤러 2종 + API 통합 테스트

**Files:**
- Create: `src/main/java/com/elipair/church/domain/challenge/AdminBibleChallengeController.java`
- Create: `src/main/java/com/elipair/church/domain/challenge/BibleChallengeController.java`
- Test: `src/test/java/com/elipair/church/domain/challenge/BibleChallengeApiTest.java`

**Interfaces:**
- Consumes: Task 6 `BibleChallengeService`, Task 7 `ChallengeProgressService`, 기존 `MemberPrincipal`(`@AuthenticationPrincipal`, `principal.id()`), Task 5 경로 규칙.
- Produces: 스펙 §3의 URL 11개. 회원 컨트롤러는 메서드 인가 없음(경로 규칙이 담당), 관리자 컨트롤러는 클래스 `@PreAuthorize("hasAuthority('CHALLENGE_MANAGE')")`.

- [ ] **Step 1: 실패하는 API 테스트 작성** (GalleryApiTest 패턴 — @SpringBootTest + MockMvc + Testcontainers)

```java
package com.elipair.church.domain.challenge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
class BibleChallengeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private BibleChallengeRepository challengeRepository;

    @Autowired
    private ChallengeParticipationRepository participationRepository;

    @Autowired
    private ChallengeReadingLogRepository logRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;

    @BeforeEach
    void seed() {
        Member member =
                memberRepository.saveAndFlush(Member.create("01000000000", "김통독", "{enc}", null, null, true, true));
        memberId = member.getId();
    }

    @AfterEach
    void cleanup() {
        logRepository.deleteAll();
        participationRepository.deleteAll();
        challengeRepository.deleteAll(challengeRepository.findAll());
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private String adminToken() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(memberId, "uuid-admin", "관리자", 900),
                        null,
                        List.of("CHALLENGE_MANAGE", "CHALLENGE_PARTICIPATE"));
    }

    private String memberToken() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(memberId, "uuid-member", "교인", 100),
                        null,
                        List.of("CHALLENGE_PARTICIPATE"));
    }

    private String userToken() {
        return "Bearer "
                + provider.issueAccess(new MemberPrincipal(memberId, "uuid-user", "미승인", 0), null, List.of());
    }

    /** 신약 60일 챌린지(오늘 10일차) 생성 → id. dailyGoal = ceil(260/60) = 5. */
    private long createNtChallenge() throws Exception {
        String json = mockMvc.perform(post("/api/admin/bible-challenges")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"학생부 신약 60일","description":"방학 통독","startBook":40,"endBook":66,
                                 "startDate":"%s","targetDays":60}
                                """.formatted(LocalDate.now().minusDays(9))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    // ---- 인가 3단계(설계 §4) ----

    @Test
    void list_anonymous_is_401() throws Exception {
        mockMvc.perform(get("/api/bible-challenges"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void list_user_without_participate_is_403() throws Exception {
        mockMvc.perform(get("/api/bible-challenges").header("Authorization", userToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void admin_create_without_manage_is_403() throws Exception {
        mockMvc.perform(post("/api/admin/bible-challenges")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"x","startBook":1,"endBook":66,"startDate":"2026-01-01","targetDays":100}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ---- 관리자 CRUD ----

    @Test
    void create_returns_201_with_derived_fields() throws Exception {
        mockMvc.perform(post("/api/admin/bible-challenges")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"신약 60일","startBook":40,"endBook":66,"startDate":"2026-07-01","targetDays":60}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalChapters").value(260))
                .andExpect(jsonPath("$.dailyGoal").value(5))
                .andExpect(jsonPath("$.endDate").value("2026-08-29"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void create_inverted_range_is_400() throws Exception {
        mockMvc.perform(post("/api/admin/bible-challenges")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"역순","startBook":66,"endBook":40,"startDate":"2026-07-01","targetDays":60}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void patch_bumps_version_then_stale_is_409() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(patch("/api/admin/bible-challenges/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"수정","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(patch("/api/admin/bible-challenges/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"또수정","version":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void patch_structure_with_participant_is_400() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/admin/bible-challenges/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetDays":90,"version":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void delete_soft_deletes_then_detail_404() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(delete("/api/admin/bible-challenges/" + id).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/bible-challenges/" + id).header("Authorization", memberToken()))
                .andExpect(status().isNotFound());
    }

    // ---- 회원: 목록/상세/참여 ----

    @Test
    void list_returns_page_envelope_with_status() throws Exception {
        createNtChallenge();
        mockMvc.perform(get("/api/bible-challenges").header("Authorization", memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("ONGOING"))
                .andExpect(jsonPath("$.content[0].description").doesNotExist());
    }

    @Test
    void detail_shows_joined_flag() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(get("/api/bible-challenges/" + id).header("Authorization", memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joined").value(false));
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/bible-challenges/" + id).header("Authorization", memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joined").value(true));
    }

    @Test
    void join_twice_is_409() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    // ---- 회원: 읽음 기록 흐름 ----

    @Test
    void read_default_then_progress_reflects_daily_goal() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/bible-challenges/" + id + "/read")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaptersRead").value(5))
                .andExpect(jsonPath("$.todayDone").value(true))
                .andExpect(jsonPath("$.currentPosition.book").value("마태복음"))
                .andExpect(jsonPath("$.currentPosition.chapter").value(5))
                .andExpect(jsonPath("$.streakDays").value(1));
    }

    @Test
    void read_backfill_yesterday_heals_streak() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());

        // 오늘 기록 → 어제 소급 → 스트릭 2 (설계 §3 백필 치유)
        mockMvc.perform(post("/api/bible-challenges/" + id + "/read")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapters\":5}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bible-challenges/" + id + "/read")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapters\":5,\"date\":\"%s\"}".formatted(LocalDate.now().minusDays(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streakDays").value(2))
                .andExpect(jsonPath("$.chaptersRead").value(10));
    }

    @Test
    void read_future_date_is_400() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/bible-challenges/" + id + "/read")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapters\":5,\"date\":\"%s\"}".formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void read_without_join_is_404() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/read")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void cancel_today_rolls_back() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/bible-challenges/" + id + "/read")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapters\":8}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/bible-challenges/" + id + "/read").header("Authorization", memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaptersRead").value(0))
                .andExpect(jsonPath("$.todayChapters").value(0));
    }

    @Test
    void cancel_without_log_is_404() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/bible-challenges/" + id + "/read").header("Authorization", memberToken()))
                .andExpect(status().isNotFound());
    }

    // ---- 회원: 로그/마이페이지 ----

    @Test
    void my_logs_returns_dated_entries() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/bible-challenges/" + id + "/read")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapters\":5}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bible-challenges/" + id + "/my-logs").header("Authorization", memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].readDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$[0].chapters").value(5));
    }

    @Test
    void my_participations_lists_history_with_completion() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());
        // 260장 전부 → 1회독 완료
        mockMvc.perform(post("/api/bible-challenges/" + id + "/read")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapters\":260}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundsCompleted").value(1));

        mockMvc.perform(get("/api/bible-challenges/my-participations").header("Authorization", memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].completed").value(true))
                .andExpect(jsonPath("$.content[0].roundsCompleted").value(1))
                .andExpect(jsonPath("$.content[0].challenge.title").value("학생부 신약 60일"));
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.challenge.BibleChallengeApiTest'`
Expected: 컴파일 성공하되 전부 404류 실패(컨트롤러 미존재) — 혹은 컴파일 실패 없음 확인만.

- [ ] **Step 3: 관리자 컨트롤러 구현**

```java
package com.elipair.church.domain.challenge;

import com.elipair.church.domain.challenge.dto.ChallengeCreateRequest;
import com.elipair.church.domain.challenge.dto.ChallengeDetailResponse;
import com.elipair.church.domain.challenge.dto.ChallengePatchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 통독 챌린지 관리 API(설계 §3). 전 메서드 CHALLENGE_MANAGE. */
@Tag(name = "통독 챌린지", description = "성경 통독 챌린지 API(설계 2026-07-06)")
@RestController
@PreAuthorize("hasAuthority('CHALLENGE_MANAGE')")
public class AdminBibleChallengeController {

    private final BibleChallengeService service;

    public AdminBibleChallengeController(BibleChallengeService service) {
        this.service = service;
    }

    @Operation(summary = "챌린지 개설", description = """
                    새 통독 챌린지를 개설한다(201 Created).

                    - 인증(JWT): 필요 — `CHALLENGE_MANAGE`
                    - 요청 본문: `ChallengeCreateRequest` — 제목(필수)·설명·권 구간(`startBook`~`endBook`, 1~66)·시작일·목표 일수
                    - 반환값: `ChallengeDetailResponse` — 파생값(종료일·총 장수·하루 목표·상태) 포함
                    - 부수효과: 없음 (성경 구조는 코드 상수 — 별도 데이터 준비 불필요)
                    """)
    @PostMapping("/api/admin/bible-challenges")
    public ResponseEntity<ChallengeDetailResponse> create(@Valid @RequestBody ChallengeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "챌린지 수정", description = """
                    챌린지를 부분 수정한다(PATCH). null 필드는 미변경.

                    - 인증(JWT): 필요 — `CHALLENGE_MANAGE`
                    - 경로 변수: `id` — 수정할 챌린지 ID
                    - 요청 본문: `ChallengePatchRequest` — 변경 필드 + `version`(낙관락, 필수)
                    - 반환값: `ChallengeDetailResponse`(`version`은 증가 후 값)
                    - 부수효과: `version` 불일치 시 409 · 참여자 존재 시 구간·기간(startBook/endBook/startDate/targetDays) 수정은 400
                    """)
    @PatchMapping("/api/admin/bible-challenges/{id}")
    public ChallengeDetailResponse patch(@PathVariable Long id, @Valid @RequestBody ChallengePatchRequest request) {
        return service.patch(id, request);
    }

    @Operation(summary = "챌린지 삭제", description = """
                    챌린지를 삭제한다(204 No Content).

                    - 인증(JWT): 필요 — `CHALLENGE_MANAGE`
                    - 경로 변수: `id` — 삭제할 챌린지 ID
                    - 반환값: 없음(204)
                    - 부수효과: soft delete — 참여·로그는 이력 보존(마이페이지에 계속 표시), 신규 기록만 차단
                    """)
    @DeleteMapping("/api/admin/bible-challenges/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
```

- [ ] **Step 4: 회원 컨트롤러 구현**

```java
package com.elipair.church.domain.challenge;

import com.elipair.church.domain.challenge.dto.ChallengeCardResponse;
import com.elipair.church.domain.challenge.dto.ChallengeDetailResponse;
import com.elipair.church.domain.challenge.dto.ChallengeReadRequest;
import com.elipair.church.domain.challenge.dto.MyParticipationResponse;
import com.elipair.church.domain.challenge.dto.MyProgressResponse;
import com.elipair.church.domain.challenge.dto.ReadingLogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.elipair.church.global.security.MemberPrincipal;

/**
 * 통독 챌린지 회원 API(설계 §3). 인가는 SecurityConfig 경로 규칙(/api/bible-challenges/** → CHALLENGE_PARTICIPATE) —
 * 갤러리 패턴이라 메서드 어노테이션 없음. read 본문은 선택(빈 {} 허용 — 기본값: 오늘·남은 목표치).
 */
@Tag(name = "통독 챌린지")
@RestController
public class BibleChallengeController {

    private final BibleChallengeService challengeService;
    private final ChallengeProgressService progressService;

    public BibleChallengeController(BibleChallengeService challengeService, ChallengeProgressService progressService) {
        this.challengeService = challengeService;
        this.progressService = progressService;
    }

    @Operation(summary = "챌린지 목록", description = """
                    챌린지 카드 목록(파생 status 포함, 본문 description 제외).

                    - 인증(JWT): 필요 — `CHALLENGE_PARTICIPATE`(MEMBER)
                    - 요청 파라미터: `page`·`size`·`sort`(기본 startDate,desc)
                    - 반환값: `Page<ChallengeCardResponse>`
                    """)
    @GetMapping("/api/bible-challenges")
    public Page<ChallengeCardResponse> list(
            @PageableDefault(size = 10, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return challengeService.list(pageable);
    }

    @Operation(summary = "챌린지 상세", description = """
                    챌린지 상세(본문·내 참여 여부 `joined` 포함).

                    - 인증(JWT): 필요 — `CHALLENGE_PARTICIPATE`
                    - 경로 변수: `id`
                    - 반환값: `ChallengeDetailResponse`
                    """)
    @GetMapping("/api/bible-challenges/{id}")
    public ChallengeDetailResponse get(@PathVariable Long id, @AuthenticationPrincipal MemberPrincipal principal) {
        return challengeService.get(id, principal.id());
    }

    @Operation(summary = "챌린지 참여", description = """
                    챌린지에 참여한다(201 Created).

                    - 인증(JWT): 필요 — `CHALLENGE_PARTICIPATE`
                    - 경로 변수: `id`
                    - 반환값: `MyProgressResponse` — 초기 대시보드(진행 0)
                    - 부수효과: 중복 참여 시 409 DUPLICATE_RESOURCE
                    """)
    @PostMapping("/api/bible-challenges/{id}/join")
    public ResponseEntity<MyProgressResponse> join(
            @PathVariable Long id, @AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(progressService.join(id, principal.id()));
    }

    @Operation(summary = "읽음 기록", description = """
                    "오늘 N장 읽음"을 기록한다. 본문 생략/빈 객체 = 해당 날짜의 남은 목표치.

                    - 인증(JWT): 필요 — `CHALLENGE_PARTICIPATE`
                    - 요청 본문: `ChallengeReadRequest` — `chapters`(기본 남은 목표치)·`date`(기본 오늘, 소급 = 챌린지 시작일~오늘)
                    - 반환값: `MyProgressResponse` — 갱신된 대시보드
                    - 부수효과: 같은 날 재기록은 누적 · 구간 끝 도달 시 회독 +1·초과분 이월 · 동시 클릭 시 409
                    """)
    @PostMapping("/api/bible-challenges/{id}/read")
    public MyProgressResponse read(
            @PathVariable Long id,
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody(required = false) ChallengeReadRequest request) {
        ChallengeReadRequest req = request != null ? request : new ChallengeReadRequest(null, null);
        return progressService.read(id, principal.id(), req);
    }

    @Operation(summary = "읽음 기록 취소", description = """
                    해당 날짜의 기록을 취소한다(실수 클릭 복구).

                    - 인증(JWT): 필요 — `CHALLENGE_PARTICIPATE`
                    - 요청 파라미터: `date`(기본 오늘)
                    - 반환값: `MyProgressResponse` — 롤백된 대시보드
                    - 부수효과: 해당 날짜 로그 물리 삭제 + 포인터 롤백(회독 경계 역이월) · 로그 없으면 404
                    """)
    @DeleteMapping("/api/bible-challenges/{id}/read")
    public MyProgressResponse cancelRead(
            @PathVariable Long id,
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return progressService.cancelRead(id, principal.id(), date);
    }

    @Operation(summary = "내 진행 대시보드", description = """
                    진행률·현재 위치·오늘 현황·스트릭·회독·페이스를 한 번에 반환한다(UI 원샷).

                    - 인증(JWT): 필요 — `CHALLENGE_PARTICIPATE`
                    - 반환값: `MyProgressResponse` — `currentPosition` null=회독 시작 전, `paceDays` null=기간 종료
                    """)
    @GetMapping("/api/bible-challenges/{id}/my-progress")
    public MyProgressResponse myProgress(@PathVariable Long id, @AuthenticationPrincipal MemberPrincipal principal) {
        return progressService.myProgress(id, principal.id());
    }

    @Operation(summary = "내 읽기 로그", description = """
                    달력 히트맵용 날짜별 로그(배열 — 페이지 아님).

                    - 인증(JWT): 필요 — `CHALLENGE_PARTICIPATE`
                    - 요청 파라미터: `from`·`to`(생략 시 챌린지 기간 전체)
                    - 반환값: `List<ReadingLogResponse>` — 날짜 오름차순
                    """)
    @GetMapping("/api/bible-challenges/{id}/my-logs")
    public List<ReadingLogResponse> myLogs(
            @PathVariable Long id,
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return progressService.myLogs(id, principal.id(), from, to);
    }

    @Operation(summary = "내 참여 이력", description = """
                    마이페이지용 — 전 챌린지 참여 이력(과거 포함, 삭제된 챌린지 이력도 보존 표시).

                    - 인증(JWT): 필요 — `CHALLENGE_PARTICIPATE`
                    - 요청 파라미터: `page`·`size`·`sort`(기본 createdAt,desc = 최근 참여순)
                    - 반환값: `Page<MyParticipationResponse>` — 진행률·회독·완주 여부·스트릭 포함
                    """)
    @GetMapping("/api/bible-challenges/my-participations")
    public Page<MyParticipationResponse> myParticipations(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return progressService.myParticipations(principal.id(), pageable);
    }
}
```

참고: `/my-participations`는 리터럴 경로라 `/{id}`보다 우선 매칭된다(Spring MVC 패턴 우선순위) — 충돌 없음.

- [ ] **Step 5: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.challenge.BibleChallengeApiTest'`
Expected: PASS (17 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/elipair/church/domain/challenge/ src/test/java/com/elipair/church/domain/challenge/BibleChallengeApiTest.java
git commit -m "feat : 통독 챌린지 API 컨트롤러(관리·회원) 및 통합 테스트 추가"
```

---

### Task 9: dev 시드 + 전체 빌드 검증

**Files:**
- Modify: `src/main/resources/db/dev/afterMigrate__seed.sql` (챌린지 시드 + 시퀀스 동기화 3줄)

**Interfaces:**
- Consumes: V13 스키마(Task 2), dev 시드의 기존 회원(9001=김은혜 목사).

- [ ] **Step 1: 시드 추가** — content_tags INSERT 블록 뒤·"시퀀스 동기화" 섹션 앞에 삽입:

```sql
-- ── 통독 챌린지 (V13) ────────────────────────────────────────────────────────
-- 진행 중 2건: 전교인 전체(30일차) + 학생부 신약(10일차). 참여·로그는 앱에서 만들며 시드하지 않는다.
INSERT INTO bible_challenges (id, title, description, start_book, end_book, start_date, target_days,
                              created_at, created_by, version) VALUES
    (9000, '2026 전교인 100일 통독', '창세기부터 요한계시록까지 함께 읽는 100일 여정입니다.',
     1, 66, CURRENT_DATE - 30, 100, now(), 9001, 0),
    (9001, '학생부 신약 60일 챌린지', '여름방학 동안 신약 27권을 함께 읽어요.',
     40, 66, CURRENT_DATE - 10, 60, now(), 9001, 0)
ON CONFLICT (id) DO NOTHING;
```

같은 파일의 "시퀀스 동기화" 섹션 끝에 3줄 추가:

```sql
SELECT setval(pg_get_serial_sequence('bible_challenges',        'id'), GREATEST((SELECT MAX(id) FROM bible_challenges),        9999));
SELECT setval(pg_get_serial_sequence('challenge_participations','id'), GREATEST((SELECT MAX(id) FROM challenge_participations), 9999));
SELECT setval(pg_get_serial_sequence('challenge_reading_logs',  'id'), GREATEST((SELECT MAX(id) FROM challenge_reading_logs),   9999));
```

- [ ] **Step 2: 전체 빌드 + 전체 테스트**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — 기존 416개 + 신규(약 60개) 전부 green. 실패 시 원인 수정 후 재실행(버전 파일은 절대 건드리지 않는다).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/dev/afterMigrate__seed.sql
git commit -m "chore : dev 목데이터에 통독 챌린지 시드 추가"
```

---

## Self-Review 결과 (작성 시 반영 완료)

- **스펙 커버리지**: 설계 §1~§7 전 항목이 Task 1~9에 매핑됨 — 상수(T1), 스키마·권한(T2), 엔티티·포인터 산술(T3), 시간대(T4), 경로 인가(T5), 관리 CRUD·수정 가드(T6), read 5단계·취소·스트릭·페이스·마이페이지(T7), URL 11개·인가 3단계(T8), YAGNI 제외 목록은 구현 없음(§7 준수).
- **타입 일관성**: 서비스가 쓰는 리포지토리 메서드명·DTO 시그니처는 Task 3/6/7 정의와 대조 완료. `checkVersion` null-safe 버전으로 통일.
- **알려진 트레이드오프**: my-participations의 참여별 스트릭 N회 조회(페이지당 ≤10회, `ponytail:` 주석), `locate`의 구간 초과 미검증(호출자 불변식) — 코드 주석에 명시.
