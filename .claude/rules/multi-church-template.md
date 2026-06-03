# Multi-Church Template Discipline

This backend is a template reused across churches. Code stays single-church; per-church difference lives **only** in `.env`. See `docs/church-backend-spec.md` §2, §8, §10–§12.

## No multi-tenancy in code

- **Never** add `church_id` / `tenant_id` columns or tenant-scoping logic. Each church = separate DB + separate deployment; isolation is infrastructure's job.
- Write all domain code as pure single-church.

## Nothing church-specific is hardcoded

Church name, domain, JWT secret, DB/Redis credentials, CORS origin, file paths — all injected via env. `application.yml` reads `${ENV}` (e.g. `${DB_URL}`); never bake a church value into code.

- Commit `.env.example` (empty values) only; `.env` stays git-ignored.
- `JWT_SECRET` **must differ per church**. Token expiry comes from `JWT_ACCESS_EXPIRY` (3600s) / `JWT_REFRESH_EXPIRY` (1209600s).
- Bodies store `media:{id}`, not URLs, so they're identical across churches — see [[media-library]].

## Deliberate non-features (don't add them)

- **No SMTP / email verification.** Identity is confirmed by admin granting the `MEMBER` role — see [[rbac-authorization]].
- **No complex password policy** (elderly users): BCrypt + minimum length (~8) only; no forced special-char/case rules.
- Required consent at signup: `termsAgreed` **and** `privacyAgreed` must both be true, else reject; record `agreed_at`. Re-consent is handled by flag reset (no separate terms-version table).

## File storage is abstracted

Upload writes to local disk via a `FileStorage` interface (`global/storage`), first impl `LocalFileStorage`, dir = `FILE_UPLOAD_DIR` mounted on a Docker named volume. Keep call sites behind the interface so a future move to S3/OCI Object Storage is an impl swap, not a domain change.

## New-church deploy = copy + swap `.env`

Adding a church must require **zero code edits**: copy code → set the 3 secrets (`DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`) + `CORS_ALLOWED_ORIGIN` → build → `docker compose up`. If a change would force per-church code edits, it violates this rule — push the variation into config instead.
