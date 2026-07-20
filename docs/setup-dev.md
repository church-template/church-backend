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
