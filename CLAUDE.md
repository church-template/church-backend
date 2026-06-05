# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A **reusable template backend** for church homepage sites. The code stays single-church and clean; every church-specific value (name, domain, JWT secret, DB credentials, CORS origin) is injected via `.env`. Deploying a new church = copy code → swap `.env` → deploy. There is intentionally **no multi-tenancy** — no `church_id`/`tenant_id` columns; each church gets a separate DB and a separate deployment instance, so isolation is an infrastructure concern, not a code concern.

The repo is currently an **early scaffold**: only `ChurchBackendApplication` and a context-load test exist. The full intended design lives in **`docs/church-backend-spec.md`** (Korean) — treat it as the authoritative blueprint when implementing any domain. Read the relevant spec section before building a feature; don't infer the data model from the (mostly empty) source tree.

Working language is **Korean**: commits, the spec, issue templates, and CodeRabbit reviews (`ko-KR`) are all in Korean.

## Project rules (`.claude/rules/`)

The sections below are the map; these files are the enforceable detail. **Consult the relevant one before implementing in that area:**

- `rbac-authorization.md` — per-permission `@PreAuthorize`, priority hierarchy guards, self-role / last-SUPER_ADMIN protection, JWT shape, 3-way path authz.
- `persistence-conventions.md` — `BaseEntity`, soft delete + partial indexes, `@Version`, `updated_by` author display, uuid/id immutability.
- `api-conventions.md` — RFC 7807 error envelope + error codes, pagination envelope, raw-markdown body storage.
- `media-library.md` — central media table, `media:{id}` refs, LIKE+FK reference tracking, blocking delete.
- `multi-church-template.md` — no tenancy columns, everything via env, deliberate non-features (no SMTP), `FileStorage` abstraction.
- `spring-boot-4.md` — SB4 starter coordinates and spec'd-but-unwired dependencies.
- `versioning-ci.md` — automation-owned version/changelog files; don't hand-edit.

## Build & test

Gradle wrapper, Java 21 toolchain.

```bash
./gradlew build            # compile + test + assemble jar
./gradlew bootRun          # run the app locally
./gradlew test             # run all tests
./gradlew clean            # remove build/

# single test class / method
./gradlew test --tests 'com.elipair.church.ChurchBackendApplicationTests'
./gradlew test --tests 'com.elipair.church.SomeTest.someMethod'
```

### Spring Boot 4 notes (important)

The stack is **Spring Boot 4.0.6** on Java 21. SB4 differs from the more common SB3 in ways that bite when adding dependencies:

- The web starter is **`spring-boot-starter-webmvc`** (renamed from `spring-boot-starter-web` in SB4).
- Test dependencies use module-specific `*-test` starters (e.g. `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`), not a single `spring-boot-starter-test`.
- Several things the spec calls for are **not yet wired up**: springdoc-openapi (Swagger), the Docker/`docker-compose` setup, an `application.yml` reading `${ENV}` vars, and any `.env`/`.env.example`. Add them with SB4-compatible coordinates and confirm against current docs (use Context7 for Spring Boot docs).

## Architecture (the parts that span files)

Package-by-feature under `com.elipair.church` (per spec §7):

- `global/` — cross-cutting: `config/` (Security, Redis, Swagger, Jpa), `security/` (JWT issue/verify filter, authorization, **priority-based hierarchy checks**), `exception/` (RFC 7807 global handler), `common/` (`BaseEntity`, page wrapper), `storage/` (`FileStorage` interface + `LocalFileStorage`).
- `domain/` — `auth`, `member`, `role`, `position`, `sermon`, `notice`, `event`, `department`, `tag`, `media`, `gallery`, `bulletin`. Each splits into `controller/service/repository/entity/dto`; keep simple domains flat rather than over-nesting. Dependency direction is **domain → global, one-way**.

Cross-cutting conventions that every domain must follow consistently — get these right once in `global` and inherit:

- **RBAC is Discord-style and per-permission.** Position (직분: 목사/장로…) and Role/Permission are *independent* axes — a high position grants no authority. Authorization checks a flattened **permission** (`@PreAuthorize("hasAuthority('SERMON_WRITE')")`); roles are just bundles of permissions. Roles carry a numeric `priority`; you may only grant/modify/delete a role whose priority is **at or below** your own `maxPriority` (same level allowed; escalation guard blocks strictly-higher only), `is_system` roles are immutable, you cannot change your own roles, and the last `SUPER_ADMIN` is protected.
- **JWT** `sub` = member `uuid` (never the BIGINT id); payload carries flattened `permissions` + `maxPriority`, not roles. Refresh tokens and the logout blacklist live in Redis. Identity (uuid/id) is immutable while phone/name/email/password are mutable — so JWT-embedded values lag until the next refresh; for live values read `GET /api/members/me`.
- **Soft delete everywhere** (`deleted_at`). All list-query indexes are **partial** (`WHERE deleted_at IS NULL`). Phone uniqueness is also a partial unique index (lets a recycled carrier number re-register).
- **Optimistic locking** (`@Version`) on editable content; version conflicts return `409 OPTIMISTIC_LOCK_CONFLICT`.
- **Errors** use a single RFC 7807 envelope (`errorCode`/`title`/`status`/`detail`/`instance`) from a `@RestControllerAdvice`. **Lists** use the `{content, page:{size,number,totalElements,totalPages}}` envelope; list cards omit body `content`.
- **Author display = `updated_by`** (last editor), not the original author — so a withdrawn author's posts self-heal when edited; show "(탈퇴한 사용자)" when that member is soft-deleted.
- **Central media library**: images/PDFs all live in one `media` table. Bodies reference them as the literal string `media:{id}` inside markdown (not URLs), so bodies stay domain-independent across churches. Reference tracking is body `LIKE '%media:{id}%'` UNION gallery/bulletin FK; deletion is **blocking** (409 + reference list if in use). Markdown is stored raw (TEXT); rendering/sanitizing is the frontend's job.
- **Identifier naming**: code-facing keys are **English** (permission names like `SERMON_WRITE`, role names like `ADMIN`); user-facing data is **Korean** (position/tag names, titles, `roles.description`).
- **Path authorization is three-way**, not "all reads public": `/api/admin/**` needs the write/manage permission; `/api/gallery/**` needs login + `GALLERY_VIEW` (members-only); other `/api/**` reads are public.

## Versioning & CI — do not hand-edit (SUH-DEVOPS-TEMPLATE)

This repo is wired to the SUH-DevOps template. **`version.yml` is the version source of truth** and `.github/scripts/version_manager.sh` syncs it with `build.gradle`'s `version`.

- **Pushing to `main` auto-bumps the patch version** (`PROJECT-COMMON-VERSION-CONTROL`) and commits it with `[skip ci]`. **Do not manually bump** `version.yml` or the `build.gradle` version, and don't hand-edit `CHANGELOG.*` — the workflows own those files.
- Treat `.github/workflows/PROJECT-COMMON-*` and `.github/scripts/*` as template-managed; don't modify them as part of feature work.
- Publishing to GitHub Packages happens on push to the **`deploy`** branch or a `v*.*.*` tag; changelog automation runs on PRs targeting `deploy`.

## Commit convention

`<type> : <description>` — 콜론 앞 공백은 저장소 히스토리 관행이다 (types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `ci`). Commit in Korean to match history. Commit/push only when asked.
