# AI 온보딩 문서 체계 구현 플랜

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** clone 직후 AI 에이전트(Claude Code·Codex)와 사람이 문서만 읽고 로컬 개발 시작 또는 새 교회 배포에 도달하게 하는 문서 체계(신규 4파일 + 기존 2파일 동기화 + override 추적 해제)를 만든다.

**Architecture:** 진입점(README/AGENTS/CLAUDE)은 요약+포인터만, 절차는 `docs/setup-*.md` 정본 2개에만 둔다. 스테일해진 기존 문서(경로 인가 3-way 표기)를 먼저 현행화한 뒤 신규 문서가 그 사실을 참조한다. 스펙: `docs/superpowers/specs/2026-07-20-ai-onboarding-docs-design.md` (§6은 (a) 추적 해제로 확정).

**Tech Stack:** Markdown 문서만. 코드 변경 없음(Task 1의 git 추적 해제 제외).

## Global Constraints

- 커밋 메시지 `<type> : <설명>` 한국어(콜론 앞 공백), Co-Authored-By 태그 금지, push 금지(로컬 커밋만).
- `version.yml`·`build.gradle`의 `version`·`CHANGELOG.*` 절대 수정 금지(자동화 소유).
- 문서에 시크릿·교회 실값(도메인·IP·비번) 금지 — 플레이스홀더만. 예외: dev 전용 공개 기본값(`church_dev_pw`·`church_dev_redis`·시드 비번 `church1234!`)은 compose에 이미 커밋된 값이라 수록 가능.
- 문서 속 명령·URL·경로는 전부 이 플랜에서 검증된 사실만: Swagger UI `/docs/swagger-ui.html`, api-docs `/v3/api-docs`, health `/actuator/health`, 로그인 `POST /api/auth/login` `{phone, password}`, backend 포트 `8080:8080`, dev 시드는 9000번대·비번 `church1234!`.
- 절차 중복 금지: 서버 프로비저닝은 `docs/deploy-server-setup.md`, DB 원격 접속은 `docs/db-remote-access.md`로 링크 위임.

---

### Task 1: docker-compose.override.yml 추적 해제 — **[집행 시 정정: no-op]** 실행 시 재검증 결과 처음부터 미추적(스펙 §6 정정 참조). Step 1 검증만 수행하고(Expected를 "무출력"으로 정정) Step 2~4는 건너뛴다.

**Files:**
- Modify: (git index만) `docker-compose.override.yml` — 파일 내용·로컬 사본은 그대로 둠

**Interfaces:**
- Produces: fresh clone에 override가 없는 상태. Task 3의 setup-dev.md가 "override 생성" 스텝을 포함하는 전제.

- [ ] **Step 1: 현재 추적 상태 확인**

Run: `git ls-files docker-compose.override.yml`
Expected: 무출력(원래부터 미추적 — 상단 [집행 시 정정] 참조. 초안은 "출력(추적 중)"을 기대했으나 오독이었음)

- [ ] **Step 2: 추적 해제 (로컬 파일 유지)**

```bash
git rm --cached docker-compose.override.yml
```

- [ ] **Step 3: 검증 — untracked도 아니고 status에 안 떠야 함**

Run: `git status --short docker-compose.override.yml && git check-ignore docker-compose.override.yml && ls docker-compose.override.yml`
Expected: status에 `D  docker-compose.override.yml`(스테이징된 삭제)만 표시, check-ignore가 경로 출력(ignore 매칭), ls로 로컬 파일 존재 확인. 커밋 후 status에서 사라짐(.gitignore:53이 이후 재추적 방지).

- [ ] **Step 4: Commit**

```bash
git commit -m "chore : docker-compose.override.yml 추적 해제 — gitignore 선언과 실제 일치(로컬 전용 오버레이)"
```

---

### Task 2: 기존 문서 동기화 (rbac 규칙 + CLAUDE.md 현행화)

**Files:**
- Modify: `.claude/rules/rbac-authorization.md` — "Path authorization is three-way" 섹션 전체 교체
- Modify: `CLAUDE.md` — 규칙 맵 한 줄, global/domain 목록, 경로 인가 불릿

**Interfaces:**
- Consumes: `SecurityConfig.securityFilterChain`의 현행 매처 체인(검증 완료 사실, Global Constraints 참조)
- Produces: Task 3·5가 참조하는 현행 인가 사실. 이후 문서는 이 두 파일과 어긋나면 안 됨.

- [ ] **Step 1: rbac-authorization.md의 3-way 섹션을 아래로 교체**

기존 섹션(`## Path authorization is three-way (not "reads are public")`부터 `Granting `MEMBER` **is** the 교인 approval step (replaces email verification).`까지)을 다음으로 교체:

```markdown
## Path authorization — SecurityConfig 매처 체인이 정본

매처는 **선언 순서대로 선순위 매칭**된다 (`global/config/SecurityConfig.securityFilterChain`):

| 순서 | Path | Rule |
|---|---|---|
| 1 | swagger(`/v3/api-docs`, `/v3/api-docs/**`, `/docs/swagger-ui/**`, `/docs/swagger-ui.html`) · `/error` · `/actuator/health` | permitAll |
| 2 | `/api/admin/**` | 인증 필수 + 세부 권한은 메서드 `@PreAuthorize` (예: `SERMON_WRITE`) |
| 3 | `/api/gallery/**` | `GALLERY_VIEW` (승인 교인 전용) |
| 4 | `/api/bible-challenges/**` | `CHALLENGE_PARTICIPATE` (승인 교인 전용) |
| 5 | `/api/sermons/**` | `SERMON_VIEW` (승인 교인 전용 — 2026-07 #53 전환) |
| 6 | 나머지 `/api/**` (공지·행사·주보·부서·문의·메인 등) | public |

- `/api/main`(통합 조회)은 **의도적으로 public 유지** — 설교 카드가 홈에 노출돼도 상세(`/api/sermons/{id}`) 클릭은 차단된다(#53 설계 결정).
- `MEMBER` role = 승인 교인 — `GALLERY_VIEW`·`SERMON_VIEW`·`CHALLENGE_PARTICIPATE` 보유(V2·V13·V15 시드). 가입 직후 기본 `USER`와 익명은 회원전용 경로에서 차단. `MEMBER` 부여가 곧 교인 승인 절차다(이메일 인증 대체).
- 새 회원전용 경로를 추가할 땐 `anyRequest().permitAll()` **앞에** 매처를 넣고, 이 표와 CLAUDE.md 요약도 함께 갱신할 것.
```

- [ ] **Step 2: CLAUDE.md 규칙 맵 한 줄 수정**

old_string (일부):
```
JWT shape, 3-way path authz.
```
new_string:
```
JWT shape, path authz(SecurityConfig 매처 체인 정본).
```

- [ ] **Step 3: CLAUDE.md global 목록에 viewcount 추가**

old_string (일부):
```
`storage/` (`FileStorage` interface + `LocalFileStorage`).
```
new_string:
```
`storage/` (`FileStorage` interface + `LocalFileStorage`), `viewcount/` (조회수 버퍼 → 주기 flush).
```

- [ ] **Step 4: CLAUDE.md domain 목록 현행화**

old_string (일부):
```
`domain/` — `auth`, `member`, `role`, `position`, `sermon`, `notice`, `event`, `department`, `tag`, `media`, `gallery`, `bulletin`.
```
new_string:
```
`domain/` — `auth`, `member`, `role`, `position`, `sermon`, `notice`, `event`, `department`, `tag`, `media`, `gallery`, `bulletin`, `challenge`(통독), `inquiry`(문의), `main`(통합 조회).
```

- [ ] **Step 5: CLAUDE.md 경로 인가 불릿 교체**

old_string:
```
- **Path authorization is three-way**, not "all reads public": `/api/admin/**` needs the write/manage permission; `/api/gallery/**` needs login + `GALLERY_VIEW` (members-only); other `/api/**` reads are public.
```
new_string:
```
- **Path authorization**: `/api/admin/**` 인증+메서드 권한, `/api/gallery/**` `GALLERY_VIEW`, `/api/bible-challenges/**` `CHALLENGE_PARTICIPATE`, `/api/sermons/**` `SERMON_VIEW`(회원전용), 나머지 `/api/**` public(`/api/main` 포함 — 의도적). 정본은 `SecurityConfig` 매처 체인과 `.claude/rules/rbac-authorization.md`의 표.
```

- [ ] **Step 6: 검증 — 스테일 표기 잔존 0건**

Run: `grep -rn "three-way\|3-way" CLAUDE.md .claude/rules/rbac-authorization.md`
Expected: 매칭 0건 (exit 1)

- [ ] **Step 7: Commit**

```bash
git add CLAUDE.md .claude/rules/rbac-authorization.md
git commit -m "docs : 경로 인가 5단·신규 도메인 현행화 (CLAUDE.md·rbac 규칙 동기화)"
```

---

### Task 3: docs/setup-dev.md 작성 + 명령 실검증

**Files:**
- Create: `docs/setup-dev.md`

**Interfaces:**
- Consumes: Task 1(override 미추적 전제 — 생성 스텝 포함), Task 2(현행 인가 사실)
- Produces: 로컬 개발 정본 문서. Task 5 AGENTS.md·Task 6 README가 이 경로로 링크.

- [ ] **Step 1: 아래 내용으로 `docs/setup-dev.md` 생성**

````markdown
# 로컬 개발 시작 (setup-dev)

clone 직후 로컬에서 앱을 띄우고 테스트까지 도달하는 정본 절차. 대상: 사람·AI 에이전트 공통.

## 0. 전제

- JDK 21, Docker(Compose v2). 그 외 전부 컨테이너/Gradle wrapper가 해결.
- `.env` 없이도 로컬 개발이 된다 — `docker-compose.yml`이 모든 값에 dev 전용 기본값
  (`church_dev_pw`·`church_dev_redis` 등)을 갖고 있다. `.env`는 운영 배포에서만 필수.

## 1. dev 오버라이드 생성 (1회)

`docker-compose.override.yml`은 로컬 전용이라 git에 없다(.gitignore). 아래로 생성:

```yaml
# docker-compose.override.yml — 로컬 전용(.gitignore). compose가 base와 자동 병합.
services:
  postgres:
    ports:
      - "127.0.0.1:5432:5432"   # 호스트 도구(DBeaver·IntelliJ)·host bootRun용
  redis:
    ports:
      - "127.0.0.1:6379:6379"   # host bootRun 시 필수(미노출이면 인증 조회 500 — §6 함정)
  backend:
    environment:
      SPRING_PROFILES_ACTIVE: dev                    # db/dev 목데이터 시드 로드
      LOGGING_LEVEL_ORG_HIBERNATE_SQL: DEBUG         # 실행 SQL 로그(선택)
```

- `dev` 프로필이 하는 일: Flyway locations에 `classpath:db/dev` 추가 →
  `db/dev/afterMigrate__seed.sql`이 매 부팅 멱등 실행되어 목데이터 시드(§5).
  운영은 이 프로필을 안 켜므로 시드가 절대 섞이지 않는다.

## 2. 전체 기동 (컨테이너 경로 — 기본)

```bash
docker compose up -d --build
```

postgres(16)·redis(7)·backend가 뜨고, backend는 `8080:8080` 노출. 헬스 대기 후 §4 검증으로.

## 3. host bootRun 경로 (빠른 반복 개발용, 선택)

인프라만 컨테이너로 띄우고 앱은 Gradle로 직접 실행:

```bash
docker compose up -d postgres redis
SPRING_PROFILES_ACTIVE=dev \
DB_URL=jdbc:postgresql://localhost:5432/church_db \
DB_PASSWORD=church_dev_pw \
REDIS_HOST=localhost \
REDIS_PASSWORD=church_dev_redis \
JWT_SECRET=dev-only-secret-change-in-production-0123456789 \
./gradlew bootRun
```

- compose는 `.env`를 자동 로드하지만 **Gradle은 아니다** — 위처럼 인라인 env 필수.
- DB 볼륨을 과거에 다른 비번으로 초기화했다면 그 값을 사용(비번은 볼륨 생성 시점에 고정됨).
- §1 오버라이드로 5432·6379가 열려 있어야 한다.

## 4. 검증

```bash
curl -s localhost:8080/actuator/health
# → {"status":"UP"}

# Swagger UI(브라우저): http://localhost:8080/docs/swagger-ui.html  (spec: /v3/api-docs)

# 시드 관리자 로그인
curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"phone":"01090000001","password":"church1234!"}'
# → {"tokens":{"accessToken":"...","refreshToken":"..."},"member":{...},"requiresAgreement":false}
```

테스트·빌드:

```bash
./gradlew build   # 컴파일+전체 테스트(Testcontainers 포함— Docker 필요)+jar
./gradlew test --tests 'com.elipair.church.SomeTest.someMethod'   # 단일
```

## 5. 목데이터 (dev 프로필 전용)

- 모든 시드 행은 **id 9000번대 예약 블록** — 앱 실데이터(10000+)와 절대 충돌 없음. 정리는 id≥9000 삭제.
- 공용 비밀번호 `church1234!`. 대표 계정:

| phone | 이름 | 역할 | 용도 |
|---|---|---|---|
| 01090000001 | 김은혜 | ADMIN(+USER), 목사 | 관리자 API 테스트 |
| 01090000003 | 박소망 | MEMBER(+USER), 장로 | 회원전용(갤러리·설교·챌린지) 테스트 |
| 01090000007 | 윤방문 | USER만 | 미승인 → 회원전용 403 테스트 |
| 01090000008 | 한탈퇴 | (소프트삭제) | 탈퇴 상태·"(탈퇴한 사용자)" 표시 테스트 |

- 전체 계정·태그·부서·콘텐츠 목록과 id 규약 상세: `src/main/resources/db/dev/afterMigrate__seed.sql` 헤더 주석이 정본.

## 6. 함정 (실전 이슈 누적)

- **host bootRun에서 redis 미연결이면 인증 필요 조회가 500** — JWT 필터가 블랙리스트를 Redis에서
  확인한다. redis 컨테이너 기동 + §1의 6379 노출 + `REDIS_PASSWORD` 지정까지 세 가지 다 필요.
- **회원전용 경로**: 갤러리·설교·통독 챌린지는 `MEMBER` 승인 계정으로만 200. `USER`(01090000007)는 403이 정상.
- **`version.yml`·`build.gradle` version·`CHANGELOG.*` 수동 편집 금지** — CI 자동화 소유(`.claude/rules/versioning-ci.md`).
- **SB4 스타터 명명**: web은 `spring-boot-starter-webmvc`, 테스트는 모듈별 `*-test`(`.claude/rules/spring-boot-4.md`).
- 운영 DB에 원격 접속하려면: `docs/db-remote-access.md`.
````

- [ ] **Step 2: 문서 속 명령 실검증 — 스택 기동**

Run: `docker compose up -d && sleep 25 && curl -s localhost:8080/actuator/health`
Expected: `{"status":"UP"}` (로컬에 기존 `.env`·볼륨이 있으면 그 값으로 뜸 — 문서 검증 목적엔 무방)

- [ ] **Step 3: 문서 속 명령 실검증 — 시드 로그인**

Run: `curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"phone":"01090000001","password":"church1234!"}' | head -c 120`
Expected: `{"tokens":{"accessToken":"` 로 시작하는 JSON. (dev 프로필로 떠 있어야 시드 존재 — 아니면 §1 오버라이드 적용 후 재기동하고 재시도)

- [ ] **Step 4: Commit**

```bash
git add docs/setup-dev.md
git commit -m "docs : 로컬 개발 정본 문서 setup-dev.md 신설 (오버라이드·시드·검증·함정)"
```

---

### Task 4: docs/setup-new-church.md 작성

**Files:**
- Create: `docs/setup-new-church.md`

**Interfaces:**
- Consumes: `docs/deploy-server-setup.md`(§1~7 헤딩 존재 확인됨), `.github/workflows/deploy.yml`(deploy push 트리거·`/srv/church-backend`), `.env.example`
- Produces: 새 교회 배포 정본. Task 5·6이 링크.

- [ ] **Step 1: 아래 내용으로 `docs/setup-new-church.md` 생성**

````markdown
# 새 교회 인스턴스 배포 (setup-new-church)

**원칙: 코드 수정 0.** 새 교회 = 레포 복사 → `.env` 작성 → 배포. 코드를 고쳐야 한다면
멀티-교회 템플릿 원칙 위반이다(`.claude/rules/multi-church-template.md`) — 변화를 config로 밀어낼 것.

## 1. 레포 준비

템플릿 레포를 새 교회 레포로 복사(fork/템플릿 생성). 이후 절차에서 코드는 건드리지 않는다.

## 2. `.env` 작성 (전체 키 목록 정본: `.env.example`)

**교회마다 반드시 달라야 하는 값:**

| 키 | 설명 |
|---|---|
| `JWT_SECRET` | 교회별 필수 상이 — 유출 시 토큰 위조 가능 |
| `DB_PASSWORD` / `REDIS_PASSWORD` | 강한 랜덤 값 |
| `CORS_ALLOWED_ORIGIN` | 해당 교회 프론트 도메인 (예: `https://www.<church-domain>`) |
| `FILE_BASE_URL` | `https://api.<church-domain>/api/media` 형태 |

**최초 관리자 부트스트랩(3종 모두 채워야 동작):** `ADMIN_PHONE`·`ADMIN_NAME`·`ADMIN_PASSWORD` —
기동 시 활성 SUPER_ADMIN이 없을 때만 1회 멱등 생성. **최초 로그인 후 비밀번호 즉시 변경.**
(SUPER_ADMIN은 API로 부여 불가 — 이 부트스트랩이 유일한 생성 경로다.)

**선택 조정값:** `SWAGGER_ENABLED`(공개 서버는 `false` 권장), `FILE_MAX_SIZE`, `CACHE_TTL`,
`VIEW_FLUSH_INTERVAL`, `APP_TIMEZONE`(기본 Asia/Seoul), `JWT_*_EXPIRY`.

`.env`는 서버(`/srv/church-backend/.env`)에만 존재 — git에 절대 커밋 금지.

## 3. 서버 프로비저닝 (교회별 1회)

정본: **`docs/deploy-server-setup.md`** — Docker 설치(§1), 배포 디렉터리+`.env`(§2),
GHCR 인증(§3), 방화벽 개방(§4), 배포용 SSH 키+GitHub Secrets(§5).

GitHub Secrets는 3개만: `DEPLOY_HOST` / `DEPLOY_USER` / `DEPLOY_SSH_KEY`. 앱 시크릿은 서버 `.env`에만.

## 4. 배포 트리거

- **`deploy` 브랜치 push** → GHCR 멀티아치 이미지 빌드 + SSH 배포(`.github/workflows/deploy.yml`),
  `up -d` 후 `/actuator/health` 180초 헬스 게이트.
- 버전봇 커밋(`[skip ci]`) 때문에 워크플로우가 안 돌면 수동 트리거:
  `gh workflow run deploy.yml --ref deploy`
- `DEPLOY_HOST` 시크릿이 비어 있으면 이미지 push까지만 수행(서버 배포 skip).

## 5. HTTPS

정본: `docs/deploy-server-setup.md` §7 — Caddy 리버스 프록시(host network, Let's Encrypt 자동),
DNS `api.<church-domain>` → 서버 IP, `.env` 도메인 반영 후 백엔드 재기동.
앱은 `forward-headers-strategy: framework`로 프록시 뒤 https를 인식한다(8080 직접 노출 금지 전제).

## 6. 배포 검증 체크리스트

```bash
curl -s https://api.<church-domain>/actuator/health   # {"status":"UP"}
# 최초 관리자 로그인(§2의 ADMIN_PHONE/ADMIN_PASSWORD):
curl -s -X POST https://api.<church-domain>/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"phone":"<ADMIN_PHONE>","password":"<ADMIN_PASSWORD>"}'
```

- [ ] health UP · [ ] 관리자 로그인 → 즉시 비번 변경 · [ ] 프론트에서 CORS 통과
- [ ] 목데이터 없음 확인(운영은 dev 프로필 미사용 — 회원 목록에 9000번대 id가 없어야 정상)
- [ ] `SWAGGER_ENABLED=false`라면 `/docs/swagger-ui.html` 404

운영 DB 원격 점검: `docs/db-remote-access.md`.
````

- [ ] **Step 2: 링크 검증**

Run: `ls docs/deploy-server-setup.md docs/db-remote-access.md .env.example .github/workflows/deploy.yml`
Expected: 4개 경로 모두 존재

- [ ] **Step 3: Commit**

```bash
git add docs/setup-new-church.md
git commit -m "docs : 새 교회 배포 정본 문서 setup-new-church.md 신설 (코드 수정 0 원칙)"
```

---

### Task 5: AGENTS.md 작성

**Files:**
- Create: `AGENTS.md`

**Interfaces:**
- Consumes: Task 2 현행화된 인가 사실, Task 3·4 문서 경로
- Produces: AI 공용 진입점. Task 7 검증이 이 파일만 주고 시작.

- [ ] **Step 1: 아래 내용으로 `AGENTS.md` 생성**

````markdown
# AGENTS.md — AI 에이전트 진입점

교회 홈페이지용 **재사용 템플릿 백엔드**(Spring Boot 4.0.x · Java 21 · PostgreSQL 16 · Redis 7 · Flyway).
코드는 single-church로 유지하고 교회별 차이는 전부 `.env`로 주입한다 — **멀티테넌시 없음**
(`church_id`/`tenant_id` 금지; 교회당 별도 DB·별도 배포). 전체 설계 정본: `docs/church-backend-spec.md`(한국어).

작업 언어는 **한국어**: 커밋 `<type> : <설명>`(콜론 앞 공백, feat/fix/refactor/docs/test/chore/perf/ci),
커밋·푸시는 요청받았을 때만.

## 시작하기

| 하려는 일 | 읽을 문서 |
|---|---|
| 로컬에서 앱 실행·개발 | `docs/setup-dev.md` |
| 새 교회 인스턴스 배포 | `docs/setup-new-church.md` |
| 도메인 기능 구현 | `docs/church-backend-spec.md` 해당 § + 아래 규칙 파일 |
| 운영 DB 점검 | `docs/db-remote-access.md` |

## 핵심 불변식 (요약 — 상세는 `.claude/rules/`의 각 파일이 정본)

- **RBAC**: 직분(position)과 권한은 독립 축. 인가는 항상 권한 단위
  `@PreAuthorize("hasAuthority('SERMON_WRITE')")` — role·직분으로 검사 금지. 역할 priority 계층 가드
  (부여는 strictly-below, 역할 수정은 at-or-below). → `rbac-authorization.md`
- **경로 인가**: `/api/admin/**` 인증+메서드 권한 · `/api/gallery/**`·`/api/bible-challenges/**`·`/api/sermons/**`
  회원전용(각 `GALLERY_VIEW`/`CHALLENGE_PARTICIPATE`/`SERMON_VIEW`) · 나머지 public(`/api/main` 포함, 의도적).
  정본: `SecurityConfig` 매처 체인. → `rbac-authorization.md`
- **JWT**: `sub`=member uuid(BIGINT id 금지), payload는 평탄화된 permissions+maxPriority. → `rbac-authorization.md`
- **영속성**: soft delete(`deleted_at`) 전면 + 모든 목록 인덱스는 partial(`WHERE deleted_at IS NULL`),
  낙관락 `@Version`(충돌 409), 작성자 표시는 `updated_by`. → `persistence-conventions.md`
- **API**: RFC 7807 단일 에러 envelope + 정해진 errorCode 표, 목록은 `{content, page{...}}` 페이지 envelope,
  본문 markdown은 raw 저장. → `api-conventions.md`
- **미디어**: 이미지·PDF는 중앙 `media` 테이블 하나, 본문 참조는 `media:{id}` 문자열(URL 금지),
  삭제는 참조 있으면 409 차단. → `media-library.md`
- **SB4**: web 스타터는 `spring-boot-starter-webmvc`, 테스트는 모듈별 `*-test`. → `spring-boot-4.md`

## 명령어

```bash
./gradlew build     # 컴파일+전체 테스트(Testcontainers — Docker 필요)+jar
./gradlew test --tests 'com.elipair.church.SomeTest.someMethod'
docker compose up -d --build    # 로컬 전체 기동(상세·시드·검증: docs/setup-dev.md)
```

## 하지 말 것

- `version.yml`·`build.gradle`의 `version`·`CHANGELOG.*` 수동 편집(자동화 소유 — `versioning-ci.md`)
- `.github/workflows/PROJECT-COMMON-*`·`.github/scripts/*` 수정(템플릿 관리)
- 테넌시 컬럼·SMTP·복잡한 비밀번호 정책 추가(의도된 비기능 — `multi-church-template.md`)
- 교회별 실값(도메인·시크릿) 하드코딩 — 전부 `.env`
````

- [ ] **Step 2: 링크·사실 검증**

Run: `ls docs/setup-dev.md docs/setup-new-church.md docs/church-backend-spec.md docs/db-remote-access.md .claude/rules/rbac-authorization.md && grep -c "SERMON_VIEW" AGENTS.md`
Expected: 5개 경로 존재, grep 1 이상

- [ ] **Step 3: Commit**

```bash
git add AGENTS.md
git commit -m "docs : AI 공용 진입점 AGENTS.md 신설 (불변식 요약·읽기 순서·명령어)"
```

---

### Task 6: README.md 작성 + CLAUDE.md 문서 지도 링크

**Files:**
- Create: `README.md`
- Modify: `CLAUDE.md` — "Build & test" 섹션 끝에 링크 2줄 추가

**Interfaces:**
- Consumes: Task 3·4·5 파일 존재
- Produces: 사람용 진입점 완성. 문서 체계 완결.

- [ ] **Step 1: 아래 내용으로 `README.md` 생성**

````markdown
# church-backend

교회 홈페이지용 **재사용 템플릿 백엔드**. 코드는 한 교회 기준으로 깨끗하게 유지하고,
교회별 차이(이름·도메인·시크릿)는 전부 `.env`로 주입한다. 새 교회 추가 = 레포 복사 + `.env` 작성 + 배포 —
**코드 수정 0**. 멀티테넌시는 의도적으로 없다(교회당 별도 DB·별도 인스턴스).

**스택**: Spring Boot 4.0.x · Java 21 · PostgreSQL 16 · Redis 7 · Flyway · Docker Compose · GitHub Actions(GHCR)

## 문서 지도

| 목적 | 문서 |
|---|---|
| 로컬 개발 시작 (clone → 실행 → 테스트) | [docs/setup-dev.md](docs/setup-dev.md) |
| 새 교회 인스턴스 배포 | [docs/setup-new-church.md](docs/setup-new-church.md) |
| 서버 프로비저닝 (OCI VM, 1회) | [docs/deploy-server-setup.md](docs/deploy-server-setup.md) |
| 전체 설계 스펙 (정본, 한국어) | [docs/church-backend-spec.md](docs/church-backend-spec.md) |
| AI 에이전트 진입점 | [AGENTS.md](AGENTS.md) (Claude Code는 `CLAUDE.md`) |

## 빠른 시작

```bash
docker compose up -d --build
curl -s localhost:8080/actuator/health   # {"status":"UP"}
```

목데이터·시드 계정·검증 절차는 [docs/setup-dev.md](docs/setup-dev.md) 참조.

## License

[LICENSE](LICENSE)
````

- [ ] **Step 2: CLAUDE.md에 링크 추가**

"## Build & test" 섹션의 코드 블록 종료 뒤에 추가:

```markdown
로컬 셋업 절차·목데이터·함정 목록은 `docs/setup-dev.md`, 새 교회 배포는 `docs/setup-new-church.md`가 정본.
비-Claude 에이전트용 진입점으로 `AGENTS.md`가 있다(이 파일과 사실이 어긋나면 안 됨).
```

- [ ] **Step 3: 검증 — README 링크 전부 유효**

Run: `for f in docs/setup-dev.md docs/setup-new-church.md docs/deploy-server-setup.md docs/church-backend-spec.md AGENTS.md LICENSE; do [ -f "$f" ] || echo "MISSING: $f"; done`
Expected: 출력 없음

- [ ] **Step 4: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "docs : README 신설(문서 지도) + CLAUDE.md에 setup 문서 링크 추가"
```

---

### Task 7: AI 실전 검증 (스펙 §8.2)

**Files:**
- Modify: (검증에서 발견된 문서 결함 수정 — 대상 파일은 결과에 따름)

**Interfaces:**
- Consumes: Task 1~6 전부 완료 상태
- Produces: "AGENTS.md만 읽고 도달 가능" 검증 통과. 플랜 완료 조건.

- [ ] **Step 1: fresh-context 서브에이전트 검증 디스패치**

레포 사전 지식이 없는 서브에이전트(general-purpose)에게 **딱 이 프롬프트만** 준다:

```
당신은 /Users/luca/workspace/Java_Spring/church-backend 레포를 처음 보는 에이전트다.
AGENTS.md부터 읽고 시작하라. 다음 질문에 답하라(문서 근거 경로 포함):
1. 로컬에서 앱을 띄우고 시드 계정으로 로그인 검증까지 하는 정확한 명령 순서는?
2. 새 교회 배포 시 교회마다 반드시 달라야 하는 .env 키 4개는?
3. 설교 목록 API는 익명이 조회 가능한가? 근거는?
4. 버전을 올리고 싶으면 version.yml을 직접 수정하면 되는가?
실행은 하지 말고 문서만으로 답하라.
```

- [ ] **Step 2: 성공 기준 채점**

- 1번: override 생성 → `docker compose up -d --build` → health → 로그인 curl(01090000001/church1234!) 순서가 나옴
- 2번: `JWT_SECRET`·`DB_PASSWORD`·`REDIS_PASSWORD`·`CORS_ALLOWED_ORIGIN`
- 3번: 불가(`SERMON_VIEW` 회원전용), 근거로 AGENTS.md 또는 rbac 규칙 인용
- 4번: 안 됨(자동화 소유)

- [ ] **Step 3: 실패 항목이 있으면 해당 문서 수정 후 Step 1 재실행**

틀린 답의 원인이 된 문서(경로 누락·모호 문구)를 고친다. 통과까지 반복(2회 초과 실패 시 유저에게 보고).

- [ ] **Step 4: 수정이 있었다면 Commit**

```bash
git add -A '*.md'
git commit -m "docs : AI 실전 검증 피드백 반영"
```

---

## Self-Review 결과

- 스펙 커버리지: §3 구조(4신규+2동기화)=Task 2~6, §6(a)=Task 1, §8.2 검증=Task 7, §8.3(명령 실검증)=Task 3 Step 2·3. 누락 없음.
- 플레이스홀더: 문서 본문 전문 수록, TBD 없음. `<church-domain>`류는 멀티-교회 원칙상 의도된 플레이스홀더.
- 일관성: 시드 계정 표(Task 3)와 검증 프롬프트(Task 7)의 계정·비번 일치, 경로 인가 서술이 Task 2 표와 일치.
