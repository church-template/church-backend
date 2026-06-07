# 갤러리(Gallery) 도메인 설계 — #16

> 스펙 출처: `docs/church-backend-spec.md` §5.12(갤러리), §5.10(미디어 참조추적), §5(작성자 표시·낙관락·페이지네이션), §6(인덱스).
> 작성일: 2026-06-07 · 선행 의존: D2(MEMBER·GALLERY_VIEW)·미디어 라이브러리·글로벌 태그 — **모두 완료·배선됨**.

---

## 1. 목표 & 범위

교회 홈페이지의 **앨범 단위 사진 갤러리**. 다른 콘텐츠 도메인과 달리 **회원 전용 열람**이다 — 승인된 교인(`MEMBER` 역할 = `GALLERY_VIEW` 보유)만 조회할 수 있고, 단순 가입자(`USER`)·비로그인 방문자는 차단된다.

### 핵심 결정 (브레인스토밍 확정)
- **앨범 = 콘텐츠 엔티티**: `BaseEntity` 상속(soft delete · `@Version` 낙관락 · `created_by`/`updated_by` · 작성자 표시). 스펙 §5 "작성자 표시 정책"의 콘텐츠 목록(설교·공지·갤러리·주보)에 포함되므로 `updated_by` 기준 작성자 표시 + 탈퇴 마스킹("(탈퇴한 사용자)")을 적용한다.
- **사진 = 경량 연결 엔티티**: `soft delete`·`version` 없이 `created_at`만. 연결 해제 = **hard delete**(연결 행만 제거, media 원본은 라이브러리에 보존).
- **앨범 soft-delete 시 그 앨범의 `gallery_photos` 행도 함께 hard delete**(연결 정리): `gallery_photos.media_id` FK가 살아있으면 Provider가 참조에서 빼더라도 media 차단삭제(`repository.delete`)가 **DB FK 위반(500)** 으로 실패한다. 사진 행을 지워야 Provider 판정과 실제 DB 상태가 일치한다(스펙 §5.12 "연결만 정리", 리뷰 Critical 반영).
- **사진 메타(순서·캡션) 편집은 MVP 제외**: 추가(맨 뒤 append)·연결 해제만 제공. `caption` 컬럼은 스키마에 두되 MVP 추가 시 `null`(후속 이슈에서 편집 엔드포인트 추가). 스펙 §5.12 엔드포인트 표와 정확히 일치.
- **대표 썸네일** = `sort_order` 가장 앞선 사진(별도 대표 컬럼 없음). 첫 사진이 해제되면 다음 사진이 자동 대표.
- **사진 추가 입력** = **단일 multipart 혼합** 엔드포인트: `files`(신규 업로드, optional) + `mediaIds`(기존 라이브러리 선택, optional)를 한 요청에서 처리.
- 앨범은 **글로벌 태그 풀** 공유(`resource_type = GALLERY_ALBUM`, 이미 enum 존재). `?tagId=` 필터.
- **미디어 차단 삭제에 두 경로로 합류**: 앨범 본문 `media:{id}`(LIKE) + 사진 `media_id`(FK `=`). 사진 FK 매칭은 **이 프로젝트 최초의 FK 기반 참조 Provider**다.

### 이미 배선되어 있어 추가 작업이 없는 부분
- `GALLERY_WRITE`/`GALLERY_VIEW` 권한 시드(`V2`), `MEMBER` 역할 → `GALLERY_VIEW` 매핑(`V2`).
- `SecurityConfig`: `/api/gallery/**` → `hasAuthority("GALLERY_VIEW")`, `/api/admin/**` → `authenticated()`(세부는 메서드 `@PreAuthorize`).
- `ContentResourceType.GALLERY_ALBUM` enum 값.

→ 본 작업은 `domain/gallery` 패키지 신규 추가 + `MediaService` 메서드 2개 추가로 **순수 가산적(additive)** 이다.

---

## 2. 데이터 모델 — `V11__create_gallery.sql`

V10(departments)이 마지막 → 갤러리는 **V11**. 컬럼·인덱스 관례는 V7~V10을 따른다.

### gallery_albums (`BaseEntity` 상속)
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | BIGINT PK (IDENTITY) | |
| title | VARCHAR(200) NOT NULL | 예: 2026 부활절, 여름 수련회 |
| description | TEXT | 마크다운(선택). 본문 이미지 `media:{id}` — 차단삭제 LIKE 검색 대상 |
| created_at / updated_at | TIMESTAMP | JPA Auditing |
| created_by / updated_by | BIGINT REFERENCES members(id) | 감사 FK (표시용 작성자 = updated_by) |
| deleted_at | TIMESTAMP | soft delete |
| version | BIGINT NOT NULL DEFAULT 0 | 낙관락 |

### gallery_photos (경량 — `BaseTimeEntity`, `created_at`만)
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | BIGINT PK (IDENTITY) | |
| album_id | BIGINT NOT NULL REFERENCES gallery_albums(id) | 소속 앨범 |
| media_id | BIGINT NOT NULL REFERENCES media(id) | 실제 이미지는 media 재사용 |
| caption | VARCHAR(500) | 선택. MVP 추가 시 null |
| sort_order | INT NOT NULL | 앨범 내 정렬(첫 사진=대표) |
| created_at | TIMESTAMP NOT NULL | |

### 인덱스 (스펙 §6)
```sql
CREATE INDEX idx_gallery_albums_created_at ON gallery_albums (created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_gallery_photos_album_sort ON gallery_photos (album_id, sort_order);
CREATE INDEX idx_gallery_photos_media_id   ON gallery_photos (media_id);
```
- `gallery_photos`엔 `deleted_at`이 없으므로 **비부분 인덱스**(스펙 §6의 `(album_id, sort_order)`와 일치). 부분 인덱스는 앨범에만.
- `idx_gallery_photos_media_id`는 FK 참조 Provider·차단삭제 검색용. 스펙 §5.10 "갤러리·주보의 `media_id` FK 검색은 일반 인덱스로 즉시 처리"를 뒷받침.
- `MigrationIndexTest`에 `idx_gallery_albums_created_at` 부분 인덱스 회귀 테스트 1건 추가(신규 도메인 관례).

### 엔티티 모델링 메모
- `GalleryPhoto`는 JPA 컬렉션(`@OneToMany`) 없이 `albumId`/`mediaId`를 **`Long` FK 컬럼**으로 보유하고, 앨범↔사진 관계는 리포지토리 조회로 다룬다(코드베이스의 저결합 FK 스타일·"작은 파일" 원칙 일치, lazy-load/cascade 복잡성 회피).

---

## 3. 미디어 참조 Provider 2종

`MediaReferenceProvider`(SPI): `List<ContentRef> findReferences(long mediaId)`, `ContentRef(String type, Long id, String title)`. `MediaService`가 모든 Provider 빈을 주입받아 union → 참조 있으면 `409 MEDIA_IN_USE` + `references` 반환.

### GalleryAlbumReferenceProvider (본문 LIKE — 기존 패턴)
```java
String pattern = "media:" + mediaId + "($|[^0-9])";
// select id, title from gallery_albums where deleted_at is null and description ~ :pattern
→ new ContentRef("gallery_album", id, title)
```
PostgreSQL 정규식 `~` + 경계(`($|[^0-9])`)로 `media:42`가 `media:420`을 오탐하지 않게 한다(event/department와 동일).

### GalleryPhotoReferenceProvider (FK = — 최초 FK 기반, 스펙 §5.10 SQL 그대로)
```sql
SELECT DISTINCT a.id AS id, a.title AS title
FROM gallery_photos p JOIN gallery_albums a ON a.id = p.album_id
WHERE p.media_id = :mediaId AND a.deleted_at IS NULL
```
```java
→ new ContentRef("gallery_photo", albumId, albumTitle)
```
- 사진 참조는 **소속 앨범**(id·title)으로 표면화된다(프론트는 그 앨범 편집으로 이동).
- `DISTINCT`: 같은 앨범 내 여러 사진이 같은 media를 쓰면 앨범 1건으로 합친다.
- **앨범 soft-delete 시 그 사진 행은 hard delete로 정리**되므로(§1·§6) 삭제된 앨범의 media는 참조에서 빠져 삭제 가능해진다. 조인의 `a.deleted_at IS NULL`은 스펙 §5.10 SQL과의 일치 + 방어선(혹시 남은 행 제외)으로 유지한다. **사진 행 정리와 이 필터가 함께 있어야** Provider 판정과 DB FK가 모순되지 않는다 — Provider만 제외하고 행을 남기면 media 차단삭제가 FK 위반으로 깨진다(리뷰 Critical).

---

## 4. API

### 4.1 조회 — `GalleryAlbumController` (회원 전용)
`/api/gallery/**`는 `SecurityConfig`가 `GALLERY_VIEW`를 강제하므로 메서드 애너테이션은 불필요(공개 조회 도메인이 SecurityConfig 경로 규칙에 의존하는 관례와 동일).

| 메서드 | 경로 | 응답 |
|---|---|---|
| GET | `/api/gallery/albums?tagId=&page=&size=&sort=` | `Page<GalleryAlbumCardResponse>` (기본 `createdAt,desc`) |
| GET | `/api/gallery/albums/{id}` | `GalleryAlbumDetailResponse` (사진 목록 포함) |

### 4.2 관리 — `AdminGalleryController` (`@PreAuthorize("hasAuthority('GALLERY_WRITE')")`)
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/admin/gallery/albums` | 앨범 생성 (201, `tagIds` 연결) |
| PATCH | `/api/admin/gallery/albums/{id}` | 부분 수정 (`version` 낙관락 + `tagIds` 갱신). 스펙은 PATCH만 — **PUT 없음** |
| DELETE | `/api/admin/gallery/albums/{id}` | 앨범 soft delete + 태그 정리 (204) |
| POST | `/api/admin/gallery/albums/{id}/photos` | **multipart/form-data**: `files`(신규)+`mediaIds`(기존) 둘 다 optional → append → 앨범 detail 반환 |
| DELETE | `/api/admin/gallery/photos/{photoId}` | 사진 연결 해제 = hard delete (204). media 원본 보존 |

---

## 5. DTO

```text
GalleryAlbumCreateRequest(title, description, List<Long> tagIds)
GalleryAlbumPatchRequest(title?, description?, List<Long> tagIds?, Long version)   // 부분 수정
GalleryAlbumCardResponse(id, title, thumbnailMediaId?, photoCount, createdAt, List<TagResponse> tags, String author)
GalleryAlbumDetailResponse(id, title, description, List<TagResponse> tags, String author,
                           createdAt, updatedAt, Long version, List<GalleryPhotoResponse> photos)
GalleryPhotoResponse(id, mediaId, caption, sortOrder)
```
- **카드는 본문 `description` 제외**(스펙 §5 목록 카드 정책). `thumbnailMediaId` = 첫 사진 `media_id`(사진 없으면 null), `photoCount` = 앨범 내 사진 수.
- 상세 응답은 편집 재전송용 `version` 포함.
- 사진 추가 요청은 별도 JSON DTO 없이 컨트롤러에서 `@RequestParam(required=false) List<MultipartFile> files`, `@RequestParam(required=false) List<Long> mediaIds`로 수신.

---

## 6. 서비스 로직

### GalleryAlbumService (`@Transactional(readOnly = true)`)
- **list(tagId, pageable)**: `tagId` 있으면 `contentTagService.resourceIdsWithTag(GALLERY_ALBUM, tagId)` → Specification(`deleted_at IS NULL` + `id IN`). 페이지 조회 후 **N+1 차단 배치 조회**로 카드 조립:
  - 썸네일: `SELECT DISTINCT ON (album_id) album_id, media_id FROM gallery_photos WHERE album_id IN :ids ORDER BY album_id, sort_order`(네이티브).
  - 사진수: `SELECT album_id, count(*) FROM gallery_photos WHERE album_id IN :ids GROUP BY album_id`.
  - 태그: `contentTagService.getTagsByResources(GALLERY_ALBUM, ids)`.
  - 작성자: `authorDisplayService.displayNames(updatedBy 목록)`.
- **get(id)**: 미삭제 앨범 로드(없으면 `RESOURCE_NOT_FOUND`) + 사진 `sort_order ASC` + 태그 + 작성자 → detail.
- **create(req)**: 저장 → `replaceLinks(GALLERY_ALBUM, id, tagIds)` → `repository.flush()` → detail.
- **patch(id, req)**: 로드 → `checkVersion(version)`(불일치 `409 OPTIMISTIC_LOCK_CONFLICT`) → present 필드만 반영 → `tagIds`가 있으면 `replaceLinks` → `repository.flush()`(수정 응답 version 보장) → detail. **tag-only 수정은 앨범 행 미변경이라 version 유지**(태그 링크는 별도 엔티티).
- **delete(id)**: 로드 → soft delete(앨범) → `contentTagService` 태그 정리 → **그 앨범의 `gallery_photos` 행 hard delete**(`deleteByAlbumId`). media 원본은 보존. 사진 행을 지워야 이후 media 차단삭제가 DB FK 위반 없이 동작한다(리뷰 Critical).

### GalleryPhotoService (`@Transactional`)
- **addPhotos(albumId, files, mediaIds, uploaderId)**:
  1. **앨범 행을 비관적 쓰기 락으로 로드**(`findByIdForUpdate`, `@Lock(PESSIMISTIC_WRITE)`; 미존재/삭제 시 404). 같은 앨범 동시 추가를 직렬화해 ③의 `max(sort_order)+1` read-modify-write 경합으로 인한 sort_order 중복을 차단한다(리뷰 Major — department 계층락과 동일 패턴; 락이 업로드 I/O까지 유지되나 관리자 전용·저빈도라 수용).
  2. **두 경로 모두 이미지만 허용** — 기존 `mediaIds`는 `mediaService.requireImages(mediaIds)`(존재+이미지 검증, fail-fast), 신규 `files`는 **`mediaService.uploadImage(file, uploaderId)`** — *저장 전*에 mime을 스니핑해 비이미지면 `INVALID_INPUT_VALUE`로 거부하므로 파일이 디스크에 쓰이지 않는다(리뷰 Major — `upload()` 후 검사하면 tx 롤백 시 DB 행만 사라지고 디스크 파일이 untracked 고아로 남는 문제 회피).
  3. 락 보유 중 `max(sort_order)+1`부터 1씩 `mediaIds`(주어진 순서) → `files`(주어진 순서)로 append 저장 → `albumService.get(albumId)`로 detail 반환(단방향 의존: photo → album service). 동률 방지로 사진 조회 정렬은 `ORDER BY sort_order, id`.
- **removePhoto(photoId)**: 사진 로드(없으면 404) → hard delete. media 원본은 라이브러리에 유지.

---

## 7. 미디어 도메인 변경 — `MediaService` 메서드 2개 추가

```java
/** 기존 mediaIds 선택 시: 존재 + 이미지 mime 검증. 없으면 RESOURCE_NOT_FOUND, 비이미지면 INVALID_INPUT_VALUE. */
public void requireImages(Collection<Long> mediaIds)

/** 신규 업로드 전용: detectMime(스니핑) 직후 store 이전에 이미지 가드를 끼워, 비이미지면 INVALID_INPUT_VALUE로
 *  즉시 거부(파일 미저장)·이미지면 기존 upload()와 동일하게 store→save. (detectMime이 이미 store보다 앞서므로 가드만 추가) */
public MediaResponse uploadImage(MultipartFile file, Long uploaderId)
```
- media가 "미디어 진실"의 소유자이므로, 갤러리가 `MediaRepository`/`fileStorage`를 직접 건드리지 않고 이 좁은 인터페이스로 검증·업로드하게 한다(도메인 경계 유지, 기존 `MimeTypeValidator` 재사용).
- `uploadImage`는 비이미지를 **디스크에 쓰기 전에** 막아 untracked 고아 파일을 원천 차단한다(리뷰 Major). 일반 라이브러리 업로드용 `upload()`(이미지+PDF)는 그대로 둔다. 공통 로직은 private 헬퍼로 추출해 중복을 피한다.

---

## 8. 테스트 전략 (TDD, 80%+)

- **MigrationIndexTest**: `idx_gallery_albums_created_at` 부분 인덱스(`created_at` + `deleted_at IS NULL`) 1건 추가.
- **Repository** (`@DataJpaTest` + flyway-on/Testcontainers): 앨범 soft-delete 필터·태그 Specification·썸네일(DISTINCT ON)·사진수 배치·사진 `media_id` 참조·`max(sort_order)` append·**앨범 삭제 시 `deleteByAlbumId` 정리**·`findByIdForUpdate` 락 조회.
- **GalleryAlbumService**: 생성/패치(버전 충돌 409)·태그 replaceLinks·작성자 표시(탈퇴 마스킹)·**삭제(soft + 태그 정리 + 사진 행 hard delete)**·**tag-only 패치 version 유지**.
- **GalleryPhotoService**: mediaIds 추가·파일 업로드 추가(`uploadImage` mock)·혼합·**이미지 타입 거부**(기존 mediaIds=비이미지 / 신규 업로드=비이미지 **저장 전 거부, 디스크 파일 미생성**)·append 순서·**동시 추가 시 sort_order 중복 없음**(앨범 락)·연결 해제(hard delete, media 보존).
- **Provider**: 앨범 LIKE 경계(`42` vs `420`)·사진 FK→앨범 표면화·삭제 앨범 제외·DISTINCT.
- **API 통합**: `GALLERY_VIEW` 없는 `USER`·비로그인 → 차단(403/401), `MEMBER` → 200 / 관리 엔드포인트 `GALLERY_WRITE` 강제 / **차단 삭제 409**(앨범 본문 + 사진 FK 둘 다 `references`에 포함) / **앨범 삭제 후 그 사진이 쓰던 media는 차단삭제가 FK 위반 없이 성공**(Critical 회귀) / 페이지네이션·태그 필터 / 낙관락 충돌.

---

## 9. 산출물

신규 `com.elipair.church.domain.gallery`:
- entity: `GalleryAlbum`, `GalleryPhoto`
- repository: `GalleryAlbumRepository`(+Specifications, `findByIdForUpdate` 비관락), `GalleryPhotoRepository`(썸네일·카운트 배치, `media_id` 참조, `deleteByAlbumId`)
- provider: `GalleryAlbumReferenceProvider`, `GalleryPhotoReferenceProvider`
- service: `GalleryAlbumService`, `GalleryPhotoService`
- controller: `GalleryAlbumController`(조회), `AdminGalleryController`(관리)
- dto: `GalleryAlbumCreateRequest`, `GalleryAlbumPatchRequest`, `GalleryAlbumCardResponse`, `GalleryAlbumDetailResponse`, `GalleryPhotoResponse`
- migration: `V11__create_gallery.sql`

기존 변경(최소): `MediaService.requireImages(...)` + `MediaService.uploadImage(...)` 2개 메서드(공통 로직은 private 헬퍼로 추출), `MigrationIndexTest` 테스트 1건.

---

## 10. 비범위 (Out of Scope)

- 사진 순서 변경·캡션 편집 엔드포인트(후속 이슈). `caption` 컬럼은 forward-compatible하게 미리 둠.
- 앨범 PUT 전체 교체(스펙은 PATCH만).
- media 실제 삭제는 갤러리에서 일어나지 않음 — 오직 중앙 미디어 라이브러리 차단형 삭제(`DELETE /api/admin/media/{id}`)로만.
- 멀티테넌시 컬럼·하드코딩 교회값(템플릿 원칙).
