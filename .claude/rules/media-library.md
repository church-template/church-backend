# Central Media Library

Upload-then-reference model (like GitHub attachments / Google asset library). See `docs/church-backend-spec.md` §5.10, §5.12, §5.13.

## One table, all binaries

- Images **and** PDFs live in a single `media` table, distinguished by `mime_type`. Images → in-body insert + gallery; PDFs → bulletins. Video is **never uploaded** — external link (`video_url`) only.
- Gallery photos and bulletins **reuse `media`** via FK (`gallery_photos.media_id`, `bulletins.media_id`). Uploading through gallery/bulletin must first create a `media` row, then reference it — never store binaries elsewhere.

## Reference is text + FK, no junction table

Bodies embed the literal string `media:{id}` (see [[api-conventions]]); there is **no** content↔media join table. The truth about "what uses media N" is:

- body fields (sermon/notice/event/department/gallery-album description) matched by the PostgreSQL regex `~ 'media:{id}($|[^0-9])'` (digit-boundary safe: `media:42` must not match `media:420`; a plain `LIKE '%media:{id}%'` would, and since references now decide public access that would leak private media), **plus**
- `gallery_photos.media_id` and `bulletins.media_id` matched by `=`.

Union of both = the reference list. This is intentional (free image placement anywhere in markdown); the cost is text-search (regex) based tracking.

## Serving: public only if used by content anyone can see right now, else signed URL

`GET /api/media/{id}` needs no JWT, but anonymous access depends on what uses the media **and** on the church's menu visibility (#95, `content_visibility`) and the per-item `members_only` flag. Decided per request via `MediaReferenceProvider.hasPublicReference(mediaId)`; `MediaService.isPublic` = any provider returns true.

- **Two questions, two methods — never merge them.** `findReferences` (delete blocking + references listing) returns **every** reference regardless of visibility or `members_only`. `hasPublicReference` (public serving) is the only one that reads settings. Narrowing `findReferences` to "public only" would let members-only content's files be deleted; widening `hasPublicReference` would leak members-only photos. New providers default to `false`.
- **Public** (`200` + `Cache-Control: public, max-age=86400`) when at least one of:
  - notice/event body or bulletin PDF, and that menu is `PUBLIC`;
  - sermon body, sermon menu `PUBLIC`, and that sermon is not `members_only`;
  - gallery photo or album description, gallery menu `PUBLIC`, and that album is not `members_only`;
  - department body, department hero image (`departments.hero_media_id`), or a photo of an album linked to a department (`departments.album_id`) — **always** (an album linked to a department can't be `members_only`, and vice versa).
- A file used by both public and members-only content is public (one public reason is enough).
- **Not public** otherwise. Without a valid signature → `404 RESOURCE_NOT_FOUND` (never 403) when `media.access-enforced` (`MEDIA_ACCESS_ENFORCED`) is true; while it is false (default, cut-over period) → `200` with `private, max-age=86400`. Closing a menu blocks new requests at once; browsers that already loaded a file may keep it up to 1 day.
- New `hasPublicReference` native queries carry `church_id = :churchId` and reuse the same `media:{id}($|[^0-9])` regex.
- **Signed URL**: `/api/media/{id}?exp={epochSeconds}&sig={base64url}`, no church prefix, valid 3600s, HMAC-SHA256 over `churchId:mediaId:exp` with a key derived from `JWT_SECRET` (`MediaUrlSigner`). Valid → `200` + `private, max-age=<remaining>`; invalid/expired/only one of `exp`/`sig` → 404. Handed out by gallery album responses (`thumbnailUrl`, photo `url`, `mediaUrls`), sermon/notice/event detail `mediaUrls`, bulletin card/detail `url`, and admin media responses (`url`); the frontend builds `${NEXT_PUBLIC_API_BASE}${url}`.
- **`mediaUrls` contract**: every `media:{id}` in the body (`MediaRefs.ids` — digit-boundary, de-duplicated, in order) that exists **in the same church** has a key; other churches' ids and missing ids have none; no media → empty map, never null. The frontend drops images missing from the map.
- Linking an album to a department makes all its photos public.
- Taking a photo down: unlink it (clear the hero image / remove it from the album / edit the body) → blocking delete in the library → address is 404 (browsers that loaded it may keep it up to 1 day).

## Deletion is blocking

- `DELETE /api/admin/media/{id}`: if **any** reference exists (body, hero image, or FK), reject with `409 MEDIA_IN_USE` and return the `references` list. Never delete a file that's in use.
- Only when references hit zero does delete remove **both** the file and the record.
- Removing a photo from an album or deleting an album is **un-linking only** — the `media` original stays in the library. Real deletion happens **only** through the blocking media delete above.

## Orphans are fine

Unused media accumulating in the library is normal (like Google Drive). No auto-cleanup; admins prune manually via the blocking delete.

## Why bodies store ids, not URLs

`media:{id}` keeps bodies domain-independent — the same body works for every church regardless of `FILE_BASE_URL`. The frontend substitutes `media:{id}` → real URL at render time. See [[multi-church-template]].
