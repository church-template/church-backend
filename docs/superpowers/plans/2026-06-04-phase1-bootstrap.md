# Phase 1 인프라 부트스트랩 (#2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 스캐폴드에 실행·테스트 가능한 기반(빌드 의존성·환경설정·Flyway·Docker)을 깔아 `docker compose up`으로 pg·redis·backend가 모두 뜨고 Testcontainers 통합테스트가 green이 되게 한다.

**Architecture:** 풀스택 단일 docker-compose로 개발=운영 환경을 동일하게(parity) 만들고, 환경 차이는 오직 `.env` 값으로만 전환한다(12-factor). 스키마는 Flyway가 소유하고 JPA는 `ddl-auto=validate`로 검증만 한다. 설정은 env 중심 단일 `application.yml`(`${ENV:기본값}`), Swagger 노출은 `SWAGGER_ENABLED` 프로퍼티로 토글한다.

**Tech Stack:** Java 21, Spring Boot 4.0.6, Gradle 9.5.1, PostgreSQL 16, Redis 7, Flyway, Testcontainers, springdoc-openapi, jjwt.

설계 출처: `docs/superpowers/specs/2026-06-04-phase1-bootstrap-design.md`

---

## 사전 준비 (Setup)

- [ ] **브랜치 생성**

Run:
```bash
git checkout -b feature/#2-bootstrap
```

이후 모든 커밋은 이 브랜치에 한다. 커밋 메시지는 한글 `type : 설명 #2` 규칙(레포 컨벤션)을 따른다. **push 하지 않는다**(요청 시에만).

## 파일 구조 (이 계획이 생성/수정하는 파일)

| 파일 | 책임 |
|---|---|
| `build.gradle` (수정) | 의존성 추가 (Flyway·Testcontainers·jjwt·springdoc) |
| `src/main/resources/application.yml` (생성, `.properties` 대체) | env 바인딩·JPA·Flyway·Redis·springdoc·커스텀 설정 |
| `src/main/resources/application.properties` (삭제) | yml로 대체 |
| `src/main/resources/db/migration/.gitkeep` (생성) | Flyway 마이그레이션 디렉터리(테이블 0개) |
| `src/main/java/com/elipair/church/global/config/OpenApiConfig.java` (생성) | OpenAPI 빈 + Bearer 보안 스킴 |
| `src/test/java/com/elipair/church/TestcontainersConfiguration.java` (생성) | pg·redis 컨테이너 `@ServiceConnection` |
| `src/test/java/com/elipair/church/ChurchBackendApplicationTests.java` (수정) | Testcontainers 적용 contextLoads |
| `src/test/java/com/elipair/church/global/config/OpenApiConfigTest.java` (생성) | `/v3/api-docs` 노출 검증 |
| `src/test/java/com/elipair/church/global/config/SwaggerToggleTest.java` (생성) | `SWAGGER_ENABLED=false` → 404 검증 |
| `.env.example` (생성) | 스펙 §10 키 전부(값 비움) |
| `.dockerignore` (생성) | 이미지 빌드 컨텍스트에서 `.env`·build 등 제외 |
| `Dockerfile` (생성) | 배포용 멀티스테이지 이미지 |
| `docker-compose.yml` (생성) | pg + redis + backend 풀스택 |

---

## Task 1: 코어 의존성 추가 (Flyway · Testcontainers · jjwt)

**Files:**
- Modify: `build.gradle` (dependencies 블록)

- [ ] **Step 1: 의존성 추가**

`build.gradle`의 `dependencies { ... }` 블록 안, 기존 `runtimeOnly 'org.postgresql:postgresql'` 아래에 다음을 추가한다. (springdoc은 Task 4에서 별도 추가)

```gradle
    // DB 마이그레이션 (Flyway) — Spring Boot BOM이 버전 관리
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'

    // JWT (jjwt) — 이번 이슈는 바인딩만, 발급/검증은 #4
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
```

그리고 기존 `testImplementation` 묶음 아래에 다음을 추가한다.

```gradle
    // 통합테스트 (Testcontainers 2.x — Spring Boot 4 BOM이 버전 관리, 해석값 2.0.5)
    // 주의: TC 2.x에서 아티팩트명 변경(junit-jupiter→testcontainers-junit-jupiter, postgresql→testcontainers-postgresql)
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:testcontainers-junit-jupiter'
    testImplementation 'org.testcontainers:testcontainers-postgresql'
```

- [ ] **Step 2: 의존성 해석 확인**

Run: `./gradlew dependencies --configuration runtimeClasspath -q | grep -E 'flyway|jjwt'`
Expected: `org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql`, `io.jsonwebtoken:jjwt-api:0.12.6`가 트리에 보임 (버전 충돌·미해석 없음).

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 커밋**

```bash
git add build.gradle
git commit -m "chore : Flyway·Testcontainers·jjwt 의존성 추가 #2"
```

---

## Task 2: application.yml 작성 (`.properties` 대체) + Flyway 디렉터리

**Files:**
- Create: `src/main/resources/application.yml`
- Delete: `src/main/resources/application.properties`
- Create: `src/main/resources/db/migration/.gitkeep`

- [ ] **Step 1: application.yml 생성**

`src/main/resources/application.yml`에 아래 내용을 작성한다. 비밀값(`DB_PASSWORD`·`REDIS_PASSWORD`·`JWT_SECRET`)은 **기본값 없이 fail-fast** — 운영은 `.env`, dev는 compose가 주입, 테스트는 Task 3에서 프로퍼티로 주입한다.

```yaml
spring:
  application:
    name: church-backend
  datasource:
    url: ${DB_URL:jdbc:postgresql://postgres:5432/church_db}
    username: ${DB_USERNAME:church_user}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
  data:
    redis:
      host: ${REDIS_HOST:redis}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}

springdoc:
  api-docs:
    enabled: ${SWAGGER_ENABLED:true}
  swagger-ui:
    enabled: ${SWAGGER_ENABLED:true}

# 커스텀 설정 — 이번 이슈는 바인딩만, 실제 사용은 이후 이슈(#3·#4·#5·#6)
jwt:
  secret: ${JWT_SECRET}
  access-expiry: ${JWT_ACCESS_EXPIRY:3600}
  refresh-expiry: ${JWT_REFRESH_EXPIRY:1209600}
cors:
  allowed-origin: ${CORS_ALLOWED_ORIGIN:http://localhost:3000}
file:
  upload-dir: ${FILE_UPLOAD_DIR:/app/uploads}
  base-url: ${FILE_BASE_URL:http://localhost:8080/api/media}
  max-size: ${FILE_MAX_SIZE:10485760}
```

- [ ] **Step 2: 기존 properties 삭제**

Run: `git rm src/main/resources/application.properties`
Expected: 파일 삭제됨.

- [ ] **Step 3: Flyway 마이그레이션 디렉터리 생성**

Run:
```bash
mkdir -p src/main/resources/db/migration && touch src/main/resources/db/migration/.gitkeep
```
Expected: 디렉터리와 `.gitkeep` 생성. (실제 테이블 마이그레이션은 도메인 이슈에서 `V1__*.sql`로 추가)

- [ ] **Step 4: YAML 파싱·기동 설정 확인 (DB 없이)**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. (앱 부팅은 DB가 필요하므로 Task 3 통합테스트에서 검증)

- [ ] **Step 5: 커밋**

```bash
git add src/main/resources/application.yml src/main/resources/db/migration/.gitkeep
git rm --cached src/main/resources/application.properties 2>/dev/null || true
git commit -m "feat : env 중심 application.yml·Flyway 디렉터리 구성 #2"
```

---

## Task 3: Testcontainers 통합테스트 (코어 스택 green)

이 태스크가 "기반이 살아있다"의 핵심 성공 기준이다. pg+redis 컨테이너로 컨텍스트 로드 + Flyway migrate + JPA validate 통과를 검증한다.

**Files:**
- Create: `src/test/java/com/elipair/church/TestcontainersConfiguration.java`
- Modify: `src/test/java/com/elipair/church/ChurchBackendApplicationTests.java`

- [ ] **Step 1: 실패하는 테스트 상태 확인 (현재 깨짐)**

Run: `./gradlew test --tests 'com.elipair.church.ChurchBackendApplicationTests'`
Expected: FAIL — datasource 연결 불가(`UnknownHostException: postgres` 또는 connection refused) 또는 `DB_PASSWORD` 플레이스홀더 미해석. 이게 출발점이다.

- [ ] **Step 2: Testcontainers 설정 작성**

> TC 2.0.5 확인됨: `org.testcontainers.containers.PostgreSQLContainer`·`GenericContainer`·`org.testcontainers.utility.DockerImageName` 모두 유효(하위호환). Redis는 `@ServiceConnection(name = "redis")` + `GenericContainer`로 연결 — SB4/TC2.x에서 실제 와이어링은 Step 4 contextLoads 테스트로 검증한다. 만약 Redis 연결이 안 잡히면 컨테이너에 `.withExposedPorts(6379)`가 있는지, 이미지명이 `redis`로 인식되는지 확인.

`src/test/java/com/elipair/church/TestcontainersConfiguration.java`:

```java
package com.elipair.church;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
    }
}
```

- [ ] **Step 3: 컨텍스트 로드 테스트 갱신**

`src/test/java/com/elipair/church/ChurchBackendApplicationTests.java`를 아래로 교체한다. `@ServiceConnection`이 실제 DB·Redis 연결을 컨테이너로 덮으므로, `properties`의 비밀값은 `${...}` 플레이스홀더 해석만을 위한 더미다.

```java
package com.elipair.church;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "DB_PASSWORD=test",
        "REDIS_PASSWORD=test",
        "JWT_SECRET=test-secret-0123456789-0123456789-0123"
})
class ChurchBackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.ChurchBackendApplicationTests'`
Expected: PASS. 로그에 Testcontainers가 postgres:16-alpine·redis:7-alpine를 띄우고 Flyway가 `Successfully applied 0 migrations` 또는 baseline 메시지를 출력, JPA validate 통과.

> Docker 데몬이 실행 중이어야 한다. 미실행 시 `Could not find a valid Docker environment` → Docker Desktop 기동 후 재실행.

- [ ] **Step 5: 커밋**

```bash
git add src/test/java/com/elipair/church/TestcontainersConfiguration.java src/test/java/com/elipair/church/ChurchBackendApplicationTests.java
git commit -m "test : Testcontainers 통합테스트로 컨텍스트 로드 검증 #2"
```

---

## Task 4: springdoc-openapi + OpenApiConfig (Swagger)

⚠️ **SB4 호환 주의:** springdoc 최신 안정판 2.8.17은 Spring Boot 3.x 대상이다. Spring Boot 4에서 컨텍스트 로드가 깨지면(예: `NoSuchMethodError`/`ClassNotFoundException` from springdoc) **Context7로 `/websites/springdoc`에서 Spring Boot 4 호환 버전을 조회**해 좌표를 교체한다. 그래도 호환 릴리스가 없으면 이 태스크를 **보류**하고(의존성·OpenApiConfig·테스트를 커밋하지 말 것) 코어 스택(Task 1~3, 5~7)만으로 #2를 마감한 뒤 별도 후속 이슈로 분리한다. 이 분리는 Task 1~3을 무효화하지 않는다.

**Files:**
- Modify: `build.gradle`
- Create: `src/main/java/com/elipair/church/global/config/OpenApiConfig.java`
- Create: `src/test/java/com/elipair/church/global/config/OpenApiConfigTest.java`
- Create: `src/test/java/com/elipair/church/global/config/SwaggerToggleTest.java`

- [ ] **Step 1: 실패하는 테스트 작성 (api-docs 노출)**

> 실제 구현 정정: SB4에서 `TestRestTemplate`가 제거되어, 아래 두 테스트(OpenApiConfigTest·SwaggerToggleTest)는 `RestClient` + `@LocalServerPort`로 구현됐다. 아래 코드블록은 최초 계획 원안이며, 실제 코드는 RestClient 기반이다.

`src/test/java/com/elipair/church/global/config/OpenApiConfigTest.java`:

```java
package com.elipair.church.global.config;

import com.elipair.church.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DB_PASSWORD=test",
        "REDIS_PASSWORD=test",
        "JWT_SECRET=test-secret-0123456789-0123456789-0123"
})
class OpenApiConfigTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void apiDocsExposedWithBearerScheme() {
        ResponseEntity<String> resp = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("bearerAuth");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.config.OpenApiConfigTest'`
Expected: FAIL — `/v3/api-docs`가 404 (springdoc 의존성·설정 없음).

- [ ] **Step 3: springdoc 의존성 추가**

`build.gradle`의 `dependencies` 블록에 추가:

```gradle
    // OpenAPI / Swagger UI — springdoc 3.0.0 (Spring Boot 4 네이티브 라인. 2.8.x는 SB3 전용)
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.0'
```
> 실제 적용: 2.8.17은 SB4에서 `ClassNotFoundException: WebMvcProperties`로 실패 → 폴백 절차대로 **3.0.0** 채택(POM parent = spring-boot-starter-parent 4.0.0).

- [ ] **Step 4: OpenApiConfig 작성**

`src/main/java/com/elipair/church/global/config/OpenApiConfig.java`:

```java
package com.elipair.church.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI churchOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Church Backend API")
                        .description("교회 홈페이지 백엔드 REST API")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.config.OpenApiConfigTest'`
Expected: PASS. (실패 시 상단 ⚠️ SB4 폴백 절차 수행)

- [ ] **Step 6: 토글 테스트 작성**

`src/test/java/com/elipair/church/global/config/SwaggerToggleTest.java`:

```java
package com.elipair.church.global.config;

import com.elipair.church.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "SWAGGER_ENABLED=false",
        "DB_PASSWORD=test",
        "REDIS_PASSWORD=test",
        "JWT_SECRET=test-secret-0123456789-0123456789-0123"
})
class SwaggerToggleTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void apiDocsDisabledReturns404() {
        assertThat(restTemplate.getForEntity("/v3/api-docs", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
```

- [ ] **Step 7: 토글 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.config.SwaggerToggleTest'`
Expected: PASS — `SWAGGER_ENABLED=false`이면 `/v3/api-docs`가 404.

- [ ] **Step 8: 커밋**

```bash
git add build.gradle src/main/java/com/elipair/church/global/config/OpenApiConfig.java src/test/java/com/elipair/church/global/config/OpenApiConfigTest.java src/test/java/com/elipair/church/global/config/SwaggerToggleTest.java
git commit -m "feat : springdoc-openapi·Bearer 보안 스킴·노출 토글 #2"
```

---

## Task 5: .env.example + .dockerignore

**Files:**
- Create: `.env.example`
- Create: `.dockerignore`

- [ ] **Step 1: .env.example 작성**

`.env.example` (스펙 §10 키 전부, 비밀값은 비움). `DB_NAME`은 compose의 postgres 컨테이너 초기화용이며 `DB_URL`의 DB명과 일치해야 한다.

```dotenv
# 교회 식별
CHURCH_KEY=

# PostgreSQL
DB_NAME=church_db
DB_URL=jdbc:postgresql://postgres:5432/church_db
DB_USERNAME=church_user
DB_PASSWORD=

# Redis
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT — 교회마다 반드시 다른 값
JWT_SECRET=
JWT_ACCESS_EXPIRY=3600
JWT_REFRESH_EXPIRY=1209600

# CORS — 해당 교회 프론트 도메인
CORS_ALLOWED_ORIGIN=https://example.kr

# 파일 저장
FILE_UPLOAD_DIR=/app/uploads
FILE_BASE_URL=https://example.kr/api/media
FILE_MAX_SIZE=10485760

# Swagger 노출 (운영은 false 권장)
SWAGGER_ENABLED=false
```

- [ ] **Step 2: .dockerignore 작성**

`.dockerignore` (이미지 빌드 컨텍스트에서 비밀·불필요 파일 제외 — `.env` 누출 방지가 핵심):

```gitignore
.git
.gradle
build
.issues
.env
.idea
.vscode
docs
*.md
```

- [ ] **Step 3: `.env`가 추적되지 않는지 확인**

Run: `git check-ignore -v .env`
Expected: `.gitignore` 규칙에 매칭됨(이미 `/.issues/`와 함께 `.env`는 무시 대상). 매칭이 없으면 `.gitignore`에 `.env` 한 줄 추가.

- [ ] **Step 4: 커밋**

```bash
git add .env.example .dockerignore
git commit -m "chore : .env.example·.dockerignore 추가 #2"
```

---

## Task 6: Dockerfile (배포용 멀티스테이지 이미지)

**Files:**
- Create: `Dockerfile`

- [ ] **Step 1: Dockerfile 작성**

`Dockerfile` (프로젝트 wrapper로 빌드해 Gradle 9.5.1 일치, 런타임은 JRE 21 + healthcheck용 curl):

```dockerfile
# syntax=docker/dockerfile:1

# --- build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew bootJar --no-daemon

# --- runtime stage ---
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: 이미지 빌드 확인**

Run: `docker build -t church-backend:dev .`
Expected: 빌드 성공, 최종 이미지 생성. (빌드 스테이지에서 `bootJar`가 SUCCESSFUL, `.dockerignore` 덕에 `.env`·build 미포함)

- [ ] **Step 3: 커밋**

```bash
git add Dockerfile
git commit -m "chore : 배포용 멀티스테이지 Dockerfile 추가 #2"
```

---

## Task 7: docker-compose.yml (풀스택 parity)

**Files:**
- Create: `docker-compose.yml`

- [ ] **Step 1: docker-compose.yml 작성**

`docker-compose.yml`. 값은 `${VAR:-기본값}`로 단일 `.env` 소스에서 주입되며, `.env`가 없어도 dev 기본값으로 `up` 된다. DB·Redis 포트는 호스트로 노출하지 않는다(§11).

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: ${DB_NAME:-church_db}
      POSTGRES_USER: ${DB_USERNAME:-church_user}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-church_dev_pw}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME:-church_user} -d ${DB_NAME:-church_db}"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    command: ["redis-server", "--requirepass", "${REDIS_PASSWORD:-church_dev_redis}"]
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD:-church_dev_redis}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build: .
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      DB_URL: ${DB_URL:-jdbc:postgresql://postgres:5432/church_db}
      DB_USERNAME: ${DB_USERNAME:-church_user}
      DB_PASSWORD: ${DB_PASSWORD:-church_dev_pw}
      REDIS_HOST: ${REDIS_HOST:-redis}
      REDIS_PORT: ${REDIS_PORT:-6379}
      REDIS_PASSWORD: ${REDIS_PASSWORD:-church_dev_redis}
      JWT_SECRET: ${JWT_SECRET:-dev-only-secret-change-in-production-0123456789}
      JWT_ACCESS_EXPIRY: ${JWT_ACCESS_EXPIRY:-3600}
      JWT_REFRESH_EXPIRY: ${JWT_REFRESH_EXPIRY:-1209600}
      CORS_ALLOWED_ORIGIN: ${CORS_ALLOWED_ORIGIN:-http://localhost:3000}
      FILE_UPLOAD_DIR: ${FILE_UPLOAD_DIR:-/app/uploads}
      FILE_BASE_URL: ${FILE_BASE_URL:-http://localhost:8080/api/media}
      FILE_MAX_SIZE: ${FILE_MAX_SIZE:-10485760}
      SWAGGER_ENABLED: ${SWAGGER_ENABLED:-true}
    ports:
      - "8080:8080"
    volumes:
      - uploads-data:${FILE_UPLOAD_DIR:-/app/uploads}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 60s

volumes:
  postgres-data:
  redis-data:
  uploads-data:
```

- [ ] **Step 2: compose 문법 검증**

Run: `docker compose config -q`
Expected: 출력 없음(문법 정상). 변수 미해석/들여쓰기 오류 없음.

- [ ] **Step 3: 풀스택 기동 스모크 테스트**

Run:
```bash
docker compose up -d --build
```
Expected: postgres·redis·backend 3개 컨테이너 기동. 잠시 후 backend가 healthy.

Run (기동 대기 후):
```bash
sleep 45 && curl -fsS http://localhost:8080/actuator/health
```
Expected: `{"status":"UP"}` (또는 `"status":"UP"` 포함). Swagger 확인: `curl -fsS http://localhost:8080/v3/api-docs | head -c 200` → OpenAPI JSON.

- [ ] **Step 4: 정리**

Run: `docker compose down`
Expected: 컨테이너 종료. (named volume은 유지)

- [ ] **Step 5: 커밋**

```bash
git add docker-compose.yml
git commit -m "feat : 풀스택 docker-compose(pg·redis·backend) 구성 #2"
```

---

## 최종 검증

- [ ] **전체 테스트 green**

Run: `./gradlew clean test`
Expected: 모든 테스트 PASS (ChurchBackendApplicationTests, OpenApiConfigTest, SwaggerToggleTest).

- [ ] **이슈 수용 기준 대조**
  - `docker compose up --build`로 3개 컨테이너 기동 ✅
  - 환경 차이는 `.env`로만 전환(코드·이미지 불변) ✅
  - `SWAGGER_ENABLED` 토글 동작 ✅
  - Testcontainers 통합테스트 green ✅

---

## 참고 (구현 시 확인 문서)

- springdoc 좌표/사용법: Context7 `/websites/springdoc` (SB4 호환 버전 확인 필수)
- Spring Boot 4 설정·Testcontainers `@ServiceConnection`: Context7 `/spring-projects/spring-boot`
- Flyway + Spring Boot: Context7 `/flyway/...` 또는 Spring Boot 레퍼런스

---

## 구현 결과 노트 (#2 완료 — 실제 적용된 SB4 편차)

계획 대비 Spring Boot 4 호환을 위해 다음이 실제로 적용됨(모두 독립 리뷰로 정당성 검증, 전체 테스트 green, compose 스모크 PASS):

1. **Testcontainers 2.0.5 아티팩트명** — `testcontainers-junit-jupiter`/`testcontainers-postgresql` (SB4 BOM 관리). [Task 1]
2. **springdoc 3.0.0** — 2.8.17은 SB4에서 `WebMvcProperties` 제거로 실패. 3.0.0이 SB4 네이티브. [Task 4]
3. **TestRestTemplate 제거** — SB4의 `spring-boot-test`에서 삭제됨 → 테스트는 `RestClient` + `@LocalServerPort` 사용. [Task 4]
4. **최소 부트스트랩 `SecurityConfig` 추가(계획 4파일 외)** — spring-security가 스캐폴드 클래스패스에 있어 default-deny가 모든 엔드포인트를 막음. `global/config/SecurityConfig`에 CSRF off·STATELESS·permit(swagger·`/error`·`/actuator/health`)·그 외 authenticated 만 둠. **JWT 필터·RBAC·3분법은 #4에서 확장**(여기서 만든 셸을 재작성이 아니라 확장). [Task 4 + fix]
   - `/actuator/health` permit는 compose 헬스체크 동작에 필수.

최종 산출물 파일에 추가됨: `global/config/SecurityConfig.java`, `global/config/ActuatorHealthTest.java`.
