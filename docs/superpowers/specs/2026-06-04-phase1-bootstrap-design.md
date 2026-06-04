# Phase 1 · 빌드·환경·인프라 부트스트랩 설계 (이슈 #2)

> 작성일: 2026-06-04
> 대상 이슈: [#2](https://github.com/church-template/church-backend/issues/2) (Phase 1, 전체 blocker)
> 출처 스펙: [`docs/church-backend-spec.md`](../../church-backend-spec.md) §1·§2·§10·§11·§12
> 상위 로드맵: [`2026-06-04-church-backend-workflow-design.md`](./2026-06-04-church-backend-workflow-design.md)

## 목표 / 성공 기준

스캐폴드에 **실행·테스트 가능한 기반**을 깐다. 도메인 로직(엔티티·컨트롤러·서비스)은 일절 포함하지 않는다.

성공 기준:
1. `docker compose up --build` 한 번으로 postgres·redis·backend가 모두 기동된다.
2. 환경 차이(개발 ↔ 운영)는 오직 `.env` 값 변경만으로 전환된다. 코드·이미지·compose 파일은 동일.
3. Swagger UI가 `SWAGGER_ENABLED` 값에 따라 노출/차단된다.
4. Testcontainers 통합테스트(pg+redis)가 green — 컨텍스트 로드 + Flyway migrate + JPA validate 통과.

## 핵심 결정

| # | 결정 | 근거 |
|---|---|---|
| D1 | **풀스택 단일 docker-compose** (pg+redis+backend) | 개발=운영 환경 동일(parity). `docker compose up`으로 전부 기동(§11·§12) |
| D2 | **환경 차이는 `.env`로만** (12-factor) | 코드·이미지 불변, 새 교회=`.env` 교체(§2·§10·§12). compose `${VAR:-기본값}`로 dev 즉시 실행, 운영은 `.env` override |
| D3 | **Flyway가 스키마 소유 + JPA `ddl-auto=validate`** | 운영 안전·재현성. 모든 도메인은 `Vn__*.sql` 추가, 엔티티 변경은 마이그레이션 동반 |
| D4 | **env 중심 단일 `application.yml`** (프로파일 최소화) | `${ENV:기본값}` 바인딩. Swagger는 `SWAGGER_ENABLED` 프로퍼티 토글 |

## 산출물

| 파일 | 내용 |
|---|---|
| `build.gradle` | 의존성 추가: springdoc-openapi(webmvc-ui), jjwt(api/impl/jackson), flyway-core + flyway-database-postgresql, Testcontainers(postgresql, junit-jupiter) + spring-boot-testcontainers |
| `src/main/resources/application.yml` | `application.properties` 대체. `${ENV:기본값}` 바인딩, `ddl-auto=validate`, `open-in-view=false`, Flyway 활성, `springdoc.*.enabled=${SWAGGER_ENABLED:true}` |
| `src/main/resources/db/migration/.gitkeep` | Flyway 마이그레이션 디렉터리(배선만, 테이블 0개. 실제 스키마는 도메인별 Vn으로 추가) |
| `src/main/java/com/elipair/church/global/config/OpenApiConfig.java` | OpenAPI 빈: 서비스 info + Bearer 보안 스킴 (공통 에러 문서화는 #3에서 보강) |
| `.env.example` | 스펙 §10 키 전부(값 비움) + `SWAGGER_ENABLED`. `.env`는 gitignore(완료) |
| `docker-compose.yml` | postgres:16-alpine + redis:7-alpine + backend(Dockerfile 빌드). 단일 `.env` 소스, depends_on+healthcheck, named volume |
| `Dockerfile` | 배포용 멀티스테이지(gradle 빌드 → eclipse-temurin:21-jre 런타임, bootJar 실행) |
| `src/test/java/.../TestcontainersConfiguration.java` | pg + redis 컨테이너 `@ServiceConnection` 제공 |
| `src/test/java/.../ChurchBackendApplicationTests.java` | Testcontainers config import 하도록 갱신(contextLoads green) |

## application.yml ↔ 환경변수 매핑

| 설정 | 환경변수(기본값) |
|---|---|
| spring.datasource.url | `${DB_URL:jdbc:postgresql://postgres:5432/church_db}` |
| spring.datasource.username | `${DB_USERNAME:church_user}` |
| spring.datasource.password | `${DB_PASSWORD}` (기본값 없음 — fail-fast, compose가 주입) |
| spring.data.redis.host / port | `${REDIS_HOST:redis}` / `${REDIS_PORT:6379}` |
| spring.data.redis.password | `${REDIS_PASSWORD}` (fail-fast) |
| spring.jpa.hibernate.ddl-auto | `validate` (고정) |
| spring.flyway.enabled / locations | `true` / `classpath:db/migration` |
| springdoc.api-docs.enabled / swagger-ui.enabled | `${SWAGGER_ENABLED:true}` |
| (커스텀) jwt.secret / access-expiry / refresh-expiry | `${JWT_SECRET}` / `${JWT_ACCESS_EXPIRY:3600}` / `${JWT_REFRESH_EXPIRY:1209600}` — 바인딩만, 사용은 #4 |
| (커스텀) cors.allowed-origin | `${CORS_ALLOWED_ORIGIN:http://localhost:3000}` — 바인딩만, 사용은 #3/#4 |
| (커스텀) file.upload-dir / base-url / max-size | `${FILE_UPLOAD_DIR:/app/uploads}` / `${FILE_BASE_URL:http://localhost:8080/api/media}` / `${FILE_MAX_SIZE:10485760}` — 바인딩만, 사용은 #5/#6 |

- 비밀값(DB/Redis 패스워드, JWT_SECRET)은 **기본값 없이 fail-fast**. dev에서는 compose 인라인 기본값이 컨테이너에 주입되므로 정상 동작.
- JWT/CORS/File 키는 이번 이슈에서 **바인딩(@ConfigurationProperties 또는 yml)만** 하고 실제 사용은 해당 도메인 이슈에서.

## docker-compose 구조

- 서비스 3개: `postgres`(16-alpine), `redis`(7-alpine), `backend`(`build: .`).
- 값은 `${VAR:-기본값}` 치환으로 단일 `.env` 소스에서 주입 → pg(`POSTGRES_PASSWORD` 등)·redis·backend(`environment:`)에 동시 적용.
- backend는 pg·redis가 healthy해진 뒤 시작(`depends_on: condition: service_healthy`). backend healthcheck는 `/actuator/health`.
- **DB·Redis 포트는 호스트로 노출하지 않는다**(§11, 운영과 동일). 디버깅용 포트 노출이 필요하면 opt-in `docker-compose.override.yml`로 분리(parity 유지).
- named volume: postgres 데이터, redis 데이터, 업로드 파일(`FILE_UPLOAD_DIR`).

## Dockerfile 구조

- 멀티스테이지: ① `gradle:jdk21`(또는 wrapper)로 `bootJar` 빌드 → ② `eclipse-temurin:21-jre` 런타임에 jar 복사, 8080 노출, `ENTRYPOINT ["java","-jar","app.jar"]`.
- healthcheck용 경량 수단(curl 또는 wget) 1줄 추가.

## 검증 전략

- **통합테스트(Testcontainers)**: pg+redis 컨테이너 기동 → 컨텍스트 로드 + Flyway migrate(0건) + JPA validate 통과 확인. 이것이 "기반이 살아있다"의 success criteria.
- 단위테스트는 이 페이즈에선 설정 위주라 거의 없음. 80% 커버리지 목표는 도메인 페이즈부터 적용.

## 리스크 / 구현 시 확인

- ⚠️ **springdoc-openapi의 Spring Boot 4 호환 좌표**가 최대 불확실성(SB4가 매우 신규). 착수 시 **Context7로 정확한 버전 확인 후 고정**. flyway-database-postgresql·Testcontainers 좌표의 SB4 BOM 관리 여부도 확인.
- 현재 `ChurchBackendApplicationTests`는 datasource 없이 돌던 상태 → Testcontainers 도입으로 정상화.
- backend 코드 변경 시 `docker compose up --build` 재빌드가 개발 루프(운영과 동일 경로). 핫리로드는 YAGNI로 제외.

## 스코프 경계 (YAGNI)

- 도메인 로직(엔티티/리포지토리/컨트롤러), 실제 보안 필터·JWT 발급(#4), 전역 예외 핸들러(#3), 파일 저장 구현(#5)은 **제외**. 이번엔 의존성·설정·인프라 배선과 키 바인딩까지.
- OCI 배포·Block Volume·스냅샷 백업·시드 SUPER_ADMIN은 운영/후속 단계.

## 후속 연결

- #3(공통/예외)·#4(보안)이 이 위에 쌓인다. JWT/CORS/File 키는 여기서 바인딩만 하고 해당 이슈에서 사용.

## 구현 결과 반영 (#2 완료)

Spring Boot 4 신규성으로 인해 설계 대비 다음이 실제 적용됨:

- **springdoc-openapi 3.0.0** 채택(2.8.x는 SB3 전용, SB4에서 실패). 설계의 "버전 확인 필요" 리스크 해소.
- **테스트는 `RestClient` + `@LocalServerPort`** — `TestRestTemplate`가 SB4 `spring-boot-test`에서 제거됨.
- **최소 부트스트랩 `SecurityConfig`가 #2 범위로 편입** — spring-security가 클래스패스에 있어 default-deny가 Swagger·actuator/health를 막기 때문(앱 기동·헬스체크·Swagger 노출의 전제). 셸만 둠: CSRF off·STATELESS·permit(swagger·`/error`·`/actuator/health`)·그 외 authenticated. **JWT 필터·RBAC·경로 3분법은 #4(보안 기반)에서 이 셸을 확장**한다(재작성 아님). 따라서 "보안 필터·JWT는 #4" 스코프 경계는 유지된다.
- 검증: Testcontainers 통합테스트 4종 green + `docker compose up`으로 3개 컨테이너 기동·`/actuator/health` UP·`/v3/api-docs` 노출 스모크 PASS.
