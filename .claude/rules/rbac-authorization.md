# RBAC & Authorization

Discord-style dynamic RBAC. See `docs/church-backend-spec.md` §3–§4 for the full model. These are the invariants that must hold in every authorization path.

## Two independent axes — never couple them

- **Position (직분)** and **Role/Permission** are separate. A high position grants **zero** authority. Never derive permissions from `position_id`.
- Authorization decisions are made **per Permission**, never per Role. Roles are just bundles.

```java
@PreAuthorize("hasAuthority('SERMON_WRITE')")   // correct — check the permission
// NOT hasRole('ADMIN'), NOT "if position == 목사"
```

- DB stores permission/role names **without** any `ROLE_`/authority prefix. Apply the prefix only at the Spring Security mapping layer.

## Priority hierarchy guard (escalation prevention)

Two priority rules, **split by operation**:

- **Grant/revoke a role to a member** — target `priority` must be **strictly below** the requester's `maxPriority` (same level **blocked**). Only a strictly-higher role delegates/strips a lower one; a peer cannot. The top role (`SUPER_ADMIN`) has nothing above it → **never grantable via API; seed/DB only**. Equal-or-higher → `403`. (Validator: `validateGrantable`, used by `MemberRoleService`.)
- **Modify the role itself** — `PATCH /roles/{id}`, `DELETE /roles/{id}`, `PUT /roles/{id}/permissions` — target `priority` must be **at or below** the requester's `maxPriority` (same level allowed). Strictly higher → `403`. (Validator: `validateAssignable` via `validateMutable`.)

Additional hard stops:

- `is_system = true` roles: reject modify/delete outright, regardless of priority.
- **Self-protection**: a requester cannot grant/revoke their **own** roles.
- **Last SUPER_ADMIN**: if only one SUPER_ADMIN exists, reject its revoke/demote/delete.

## JWT contents

- `sub` = member **`uuid`** (never the BIGINT `id`).
- Carry **flattened `permissions`** + `maxPriority` — not roles.
- Token reflects issue-time values; role/permission changes apply on next refresh. For live authority decisions on sensitive screens, read DB (`GET /api/members/me`), not the token.

## Path authorization is three-way (not "reads are public")

| Path | Rule |
|---|---|
| `/api/admin/**` | requires the matching write/manage permission (e.g. `SERMON_WRITE`) |
| `/api/gallery/**` | login **+ `GALLERY_VIEW`** (members-only; not public) |
| other `/api/**` reads | public |

`MEMBER` role = approved 교인 (holds `GALLERY_VIEW`); plain `USER` (auto-granted at signup) and anonymous visitors are blocked from gallery. Granting `MEMBER` **is** the 교인 approval step (replaces email verification).

## Identifier language

Code-facing keys are **English** (`SERMON_WRITE`, `ADMIN`, `MEMBER`); user-facing labels are **Korean** (`roles.description`, position/tag names). Check English `name` in code, show Korean `description` to humans.
