# Persistence Conventions

JPA/PostgreSQL rules shared by every domain. See `docs/church-backend-spec.md` §3, §5, §6.

## BaseEntity (`global/common`)

Editable content entities extend a `@MappedSuperclass` `BaseEntity` carrying:

- `created_at`, `updated_at` — JPA Auditing (`@EnableJpaAuditing`).
- `created_by`, `updated_by` — FK → members (audit).
- `deleted_at` — soft delete.
- `version` — optimistic lock.

Don't redeclare these per entity; inherit them so audit/soft-delete/locking stay consistent.

## Soft delete everywhere

- Delete = set `deleted_at`, never physical delete (except media files, see [[media-library]]).
- Every read filters `deleted_at IS NULL`.
- **All list/lookup indexes are partial**: `... WHERE deleted_at IS NULL`. This keeps deleted rows out of the index.
- `members.phone` uniqueness is a **partial unique index** (`WHERE deleted_at IS NULL`) so a recycled carrier number can re-register after withdrawal.

## Identity is immutable

- `members.id` (BIGINT, internal FK/join) and `members.uuid` (external id, JWT sub) never change.
- `phone` / `name` / `email` / `password` are mutable; all FKs reference `id`, so changing them preserves every link.
- Phone is stored **digits-only normalized** (e.g. `01012345678`); normalize before lookup and uniqueness checks.

## Optimistic locking

Editable content (sermon, notice, event, department, gallery album, bulletin) uses `@Version`. On conflict, surface `409 OPTIMISTIC_LOCK_CONFLICT` (see [[api-conventions]]) — never let a late save silently overwrite.

## Author display policy

- Content has both `created_by` (original) and `updated_by` (last editor); set `updated_by` to the current requester on every edit.
- **Displayed author = `updated_by`**, not `created_by`. (A withdrawn author's posts self-heal when someone edits them.)
- If that member is soft-deleted, render `"(탈퇴한 사용자)"` — keep the FK intact, just mask the name.

## Tags are polymorphic — integrity is the app's job

`content_tags(tag_id, resource_type, resource_id)` is a single global tag pool shared across domains. The DB cannot enforce FK integrity on `resource_id`, so:

- Validate the target resource exists when creating a link.
- On content soft-delete, clean up its `content_tags` rows.
