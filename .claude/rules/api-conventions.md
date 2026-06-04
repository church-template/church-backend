# API Conventions

REST contract shared by all controllers. See `docs/church-backend-spec.md` §5.

## Error responses — RFC 7807, one shape

All failures map through a single `@RestControllerAdvice` (`global/exception`) to:

```json
{ "errorCode": "INVALID_INPUT_VALUE", "title": "유효하지 않은 입력값",
  "status": 400, "detail": "...", "instance": "/api/auth/login" }
```

- `errorCode`: UPPER_SNAKE, for client branching. `title`: Korean, user-facing. Add field errors under `errors` when useful.

Canonical mappings (reuse these codes — don't invent new ones ad hoc):

| status | errorCode | when |
|---|---|---|
| 400 | `INVALID_INPUT_VALUE` | validation failure |
| 401 | `AUTHENTICATION_FAILED` | login mismatch (do **not** reveal whether phone exists) |
| 401 | `INVALID_TOKEN` | expired/invalid token |
| 403 | `ACCESS_DENIED` | missing permission / hierarchy violation |
| 404 | `RESOURCE_NOT_FOUND` | |
| 409 | `MEDIA_IN_USE` | media delete blocked (include `references`) |
| 409 | `OPTIMISTIC_LOCK_CONFLICT` | concurrent edit |
| 409 | `DUPLICATE_RESOURCE` | duplicate phone, etc. |

## List responses — always paginated

Every list returns this envelope and accepts `?page=0&size=10&sort=createdAt,desc`:

```json
{ "content": [ ... ], "page": { "size": 10, "number": 0, "totalElements": 42, "totalPages": 5 } }
```

- **List cards omit body `content`** — return only card metadata (title, tags, view count, date, author). Body is returned in detail only. No separate `summary` field.
- Home preview vs. full page differ only by `size` (e.g. `size=3` vs `size=10`), or use `GET /api/main` for the combined latest-N feed.

## Body content — markdown stored raw

- Markdown body fields (`sermon.content`, `notice.content`, `department.description`, …) are stored **raw as `TEXT`**. The server does **not** convert to HTML — render & sanitize are the frontend's job (raw HTML disabled by default).
- In-body images reference media by id, never a URL: `![alt](media:{id})`, PDFs as `[제목](media:{id})`. See [[media-library]]. Server-side validation is minimal (length etc.); no HTML sanitize on the server.

## Auth token responses

`login` / `refresh` wrap tokens in a `tokens` object (`accessToken`, `refreshToken`). Login also returns `member` and `requiresAgreement` (true → send client to re-consent flow). Login failure: same `401` whether phone is missing or password wrong.

## Constant church-intro content is NOT in the backend

교회 소개·연혁·비전·오시는 길 etc. live in the frontend, not the API (rarely changes, frontend is per-church anyway).
