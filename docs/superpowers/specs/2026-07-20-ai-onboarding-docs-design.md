# AI 온보딩 문서 체계 설계

- 날짜: 2026-07-20
- 유형: 문서 신설 + 기존 문서 동기화 (코드 변경 없음 — 부수 이슈 §6 제외)
- 배경: 이 레포는 멀티-교회 템플릿 백엔드다. git clone 직후 Claude Code·Codex 같은 AI 에이전트(및 사람)가
  문서만 읽고 ① 로컬 개발을 시작하거나 ② 새 교회 인스턴스를 배포할 수 있어야 한다.
  현재 진입점은 CLAUDE.md뿐이라 비-Claude 에이전트는 자동으로 읽지 못하고,
  "clone → 실행 → 검증"을 순서대로 안내하는 실행 문서 자체가 없다.

## 1. 목표

- **AGENTS.md**(사실상 표준 AI 진입점)와 **README.md**(사람 겸용)를 신설해, 어떤 에이전트든
  clone 직후 "무엇을 어떤 순서로 읽고 무엇을 실행하면 되는지" 도달하게 한다.
- 셋업 절차를 목적별 실행 문서 2개(`docs/setup-dev.md`, `docs/setup-new-church.md`)로 정본화한다.
- 이미 스테일해진 기존 진입점 문서(CLAUDE.md, `.claude/rules/rbac-authorization.md`)를
  코드 현행에 동기화한다 — 틀린 정보가 새 문서로 전파되는 것을 막는 선행 작업.

## 2. 현재 상태 (코드 검증 완료)

- `README.md`, `AGENTS.md` 없음. `HELP.md`는 Gradle 자동 생성물.
- `CLAUDE.md` 있음 — 그러나 다음이 스테일:
  - 경로 인가를 "3-way"로 설명 ("`/api/admin/**` / `/api/gallery/**` / 나머지 public").
    실제 `SecurityConfig.securityFilterChain`은 **5단**: swagger·`/error`·`/actuator/health` permitAll →
    `/api/admin/**` authenticated(세부는 메서드 `@PreAuthorize`) → `/api/gallery/**` `GALLERY_VIEW` →
    `/api/bible-challenges/**` `CHALLENGE_PARTICIPATE` → `/api/sermons/**` `SERMON_VIEW`(#53) →
    `anyRequest().permitAll()` (`/api/main`은 공개 유지 — #53 스펙에 결정 명시).
  - 도메인 목록에 `challenge`(#48)·`inquiry`(#50)·`main`·`global/viewcount` 누락.
- `.claude/rules/rbac-authorization.md`의 경로표도 동일하게 3-way로 스테일.
- `.env.example` 최신 상태 (SWAGGER_ENABLED, ADMIN_* 부트스트랩, APP_TIMEZONE까지 포함) — 정본으로 활용.
- 셋업 지식이 흩어져 있는 위치: CLAUDE.md(빌드 명령), `.claude/rules/multi-church-template.md`(배포 원칙),
  `docs/deploy-server-setup.md`(서버 프로비저닝), `docs/db-remote-access.md`(DB 원격 접속),
  dev 시드 규약(`db/dev/afterMigrate__seed.sql` 헤더 주석).

## 3. 문서 구조 (신규 4 + 동기화 2)

```
README.md                    ← 사람용 진입점: 소개·스택·문서 지도
AGENTS.md                    ← AI 공용 진입점: 정체성·불변식 요약·읽기 순서·명령어
CLAUDE.md                    ← [동기화] 인가 요약·도메인 목록 현행화 + setup 문서 링크
.claude/rules/rbac-authorization.md  ← [동기화] 경로표 5단 현행화
docs/setup-dev.md            ← 정본: 로컬 개발 시작 절차
docs/setup-new-church.md     ← 정본: 새 교회 인스턴스 배포 절차
```

의존 방향: 진입점(README/AGENTS/CLAUDE)은 **요약 + 포인터**만 담고, 절차는 setup 문서에만 쓴다.
setup 문서도 이미 정본이 있는 내용(서버 프로비저닝, DB 원격 접속)은 링크로 위임한다.

## 4. 문서별 내용 개요

### 4.1 README.md (사람 겸용, 짧게)

- 한 단락 소개: 교회 홈페이지용 재사용 템플릿 백엔드, 멀티테넌시 없음(교회당 별도 DB·배포).
- 스택 한 줄: Spring Boot 4.0.x · Java 21 · PostgreSQL 16 · Redis 7 · Flyway · Docker.
- 문서 지도 표: "개발 시작 → `docs/setup-dev.md`", "새 교회 배포 → `docs/setup-new-church.md`",
  "전체 설계 → `docs/church-backend-spec.md`", "AI 에이전트 → `AGENTS.md`".
- 라이선스 언급(LICENSE 존재).

### 4.2 AGENTS.md (AI 공용 진입점)

CLAUDE.md의 Claude 전용 요소(스킬·MCP 언급 등)를 뺀, 에이전트 중립 버전. 섹션:

1. **프로젝트 정체** — 멀티-교회 템플릿(코드는 single-church, 차이는 `.env`만), 작업 언어 한국어
   (커밋 `<type> : <설명>` 콜론 앞 공백 관행 포함), 정본 스펙은 `docs/church-backend-spec.md`.
2. **핵심 불변식 요약** (각 1-2줄 + rules 파일 포인터) — RBAC 두 축 분리·권한 단위 인가,
   경로 인가 5단(현행), soft delete + 부분 인덱스, 낙관락 409, RFC 7807 envelope,
   media:{id} 참조 모델, 버전 파일은 자동화 소유(수동 편집 금지).
3. **읽기 순서** — "작업 전: 해당 도메인 spec § + `.claude/rules/` 해당 파일 → 구현.
   셋업이면 setup-dev.md / setup-new-church.md."
4. **명령어** — 빌드·전체 테스트·단일 테스트·로컬 기동(docker compose / bootRun) 요약.
5. **하지 말 것** — 버전 파일 수동 편집, 테넌시 컬럼 추가, SMTP 추가, `PROJECT-COMMON-*` 워크플로우 수정.

### 4.3 docs/setup-dev.md (로컬 개발 정본)

전제(도구 버전: JDK 21, Docker) → 절차(번호 목록, 각 단계에 **검증 명령과 기대 출력** 포함):

1. clone 후 `.env` 작성 — `.env.example` 복사, 로컬 dev용 최소값 안내.
2. 인프라 기동 — `docker compose up -d postgres redis` (또는 전체 up: override가 backend를
   dev 프로필로 띄움 — §6 결정에 따라 서술 확정).
3. 앱 실행 두 가지 경로 — ① 컨테이너로 전부(`docker compose up`), ② host에서
   `SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun`(빠른 반복). dev 프로필이 하는 일:
   Flyway `db/dev` 추가 스캔 → 목데이터 시드(9000번대 예약 블록, 공용 비번, 계정 표).
4. 검증 — `./gradlew build`(Testcontainers 포함), `/actuator/health`, Swagger URL,
   시드 계정으로 로그인 curl 예시.
5. **함정 목록** (누적된 실전 이슈): host bootRun 시 redis 미기동이면 상세 조회 500,
   시드 id는 9000번대라 실데이터와 구분, 버전 파일 수동 편집 금지, SB4 스타터 명명
   (`spring-boot-starter-webmvc`, 모듈별 `*-test`).

### 4.4 docs/setup-new-church.md (새 교회 배포 정본)

"**코드 수정 0**" 원칙(위반 시 설계 위반)을 첫 줄에 명시. 절차:

1. 레포 복사(템플릿 clone) → 교회별 `.env` 작성 — 반드시 달라야 하는 값
   (`JWT_SECRET`·`DB_PASSWORD`·`REDIS_PASSWORD`·`CORS_ALLOWED_ORIGIN`)과 선택 조정값 구분 표.
2. 서버 프로비저닝 — `docs/deploy-server-setup.md`로 위임(링크), 이 문서엔 결과 요약만.
3. 배포 트리거 — `deploy` 브랜치 push(GHCR 이미지 + SSH 배포), 버전봇 `[skip ci]`로 안 돌 때
   `gh workflow run deploy.yml --ref deploy` 수동 트리거.
4. HTTPS — Caddy 리버스 프록시(기존 배포 가이드 섹션 링크), `forward-headers-strategy` 전제.
5. 최초 관리자 — `ADMIN_*` env 부트스트랩(활성 SUPER_ADMIN 없을 때 1회 멱등 생성, 이후 비번 변경).
6. 검증 체크리스트 — health·HTTPS·로그인·CORS. 운영 DB 접속은 `docs/db-remote-access.md` 링크.

### 4.5 동기화: `.claude/rules/rbac-authorization.md`

경로표를 현행 5단으로 교체(§2의 체인 그대로). "매처 순서가 의미를 가진다"(선순위 매칭)와
`/api/main` 공개 유지 결정도 한 줄씩 추가.

### 4.6 동기화: CLAUDE.md

- "Path authorization is three-way" 요약을 5단 현행으로 수정.
- 도메인 목록에 `challenge`·`inquiry`·`main`, global에 `viewcount` 추가.
- Build & test 섹션 아래에 setup 문서 2개 + AGENTS.md 존재 언급(1-2줄).

## 5. 스테일 방지 원칙 (모든 신규 문서 공통)

- 절차는 setup 문서 한 곳에만. 진입점은 요약+포인터.
- 변하기 쉬운 사실(인가 경로, env 키 목록)은 값 나열 대신 **정본 위치를 명시**:
  "인가 체인은 `SecurityConfig` 참조", "env 전체 목록은 `.env.example` 참조".
- 문서에 시크릿·교회별 실값 금지(멀티-교회 템플릿 원칙) — 플레이스홀더만.

## 6. 부수 발견: docker-compose.override.yml 추적 모순 (결정 필요)

파일 주석과 `.gitignore:53`은 "git에 없다"고 선언하지만 **실제로는 추적 중**
(gitignore는 이미 추적된 파일을 제외하지 못함). 즉 fresh clone에 딸려온다 —
운영 서버가 git pull 방식이었다면 DB 포트 노출·dev 시드가 운영에 적용될 뻔한 모순
(현 배포는 scp 방식이라 실해는 없음).

- **권장: (a) `git rm --cached docker-compose.override.yml`로 추적 해제** — 파일의 선언된 의도 준수.
  setup-dev.md 2단계에 "override 생성" 스텝(printf 원라이너 또는 예시 블록) 추가.
- 대안 (b): 추적 유지 + 주석·gitignore 정리 — clone 즉시 `docker compose up`만으로 dev 완성이라
  온보딩은 가장 쉬우나, "운영 클론에 없다"는 안전 전제를 포기.

→ 구현 플랜에서 (a)로 진행하되, 유저 리뷰에서 뒤집을 수 있음.

## 7. 비범위

- 커맨드/스킬 신설 없음 — 문서 정착 후 반복 검증 절차가 보이면 그때(YAGNI).
- `docs/church-backend-spec.md`·`deploy-server-setup.md`·`db-remote-access.md` 본문 개편 없음(링크만).
- CLAUDE.md 전면 개편 없음 — §4.6의 최소 동기화만.
- CI로 문서-코드 동기화 검증 자동화 없음.

## 8. 완료 기준

1. 신규 4파일 존재, 동기화 2파일 현행 일치(경로 인가 5단·도메인 목록).
2. **AI 실전 검증**: fresh clone 상태를 가정한 에이전트에게 AGENTS.md만 제시했을 때
   ① setup-dev.md를 찾아 로컬 기동·테스트까지 도달, ② setup-new-church.md의 필수 env 4종을
   정확히 답할 수 있어야 한다 (플랜에서 검증 태스크로 구체화).
3. 문서 내 명령은 전부 실제 실행으로 확인된 것만 수록(추정 금지).
4. 시크릿·교회 실값 0건.
