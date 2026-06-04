# Spring Boot 4 Dependencies

The stack is **Spring Boot 4.0.6** on Java 21 (`build.gradle`). SB4 differs from the more common SB3 — get coordinates right or the build breaks.

## Starter naming (SB4)

- Web starter is **`spring-boot-starter-webmvc`** (renamed from `spring-boot-starter-web`).
- Tests use **module-specific `*-test` starters** — e.g. `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test` — **not** a single `spring-boot-starter-test`.
- When adding any new dependency, verify the SB4-compatible coordinate against current docs (use the Context7 MCP for Spring Boot) rather than assuming SB3 names.

## Already on the classpath

actuator, data-jpa, data-redis, security, validation, webmvc, lombok (compileOnly + annotationProcessor), postgresql (runtimeOnly).

## Spec'd but NOT yet wired up — add when implementing

- **springdoc-openapi** (Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs`) — and gate its exposure behind a profile/env flag for production.
- **`application.yml`** reading `${ENV}` vars (only `application.properties` with `spring.application.name` exists today).
- **`.env` / `.env.example`** — see [[multi-church-template]].
- **Docker / `docker-compose`** (postgres:16-alpine, redis:7-alpine, backend) — see `docs/church-backend-spec.md` §11.

## Don't change the version manually

`build.gradle`'s `version` is template-managed — see [[versioning-ci]].
