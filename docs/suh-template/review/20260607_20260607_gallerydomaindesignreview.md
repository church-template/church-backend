# Gallery Domain Design Review

대상: `docs/superpowers/specs/2026-06-07-gallery-domain-design.md`

## Critical

### `docs/superpowers/specs/2026-06-07-gallery-domain-design.md:49`

`gallery_photos.media_id`가 `media(id)`를 FK로 참조하는데, 앨범 삭제 시 사진 행을 보존한다고 설계되어 있습니다. 동시에 문서는 삭제된 앨범의 media가 더 이상 참조로 막히지 않아 삭제 가능하다고 설명합니다.

Provider가 `a.deleted_at IS NULL`로 참조를 제외하더라도 DB FK는 그대로 남습니다. 따라서 `MediaService.delete()`가 media row를 hard delete할 때 `gallery_photos.media_id` FK가 삭제를 막아, 설계가 보장하는 "앨범 삭제 후 그 media는 삭제 가능" 동작이 실제로는 실패할 수 있습니다.

해결: 앨범 soft delete 시 `gallery_photos` 연결 행을 hard delete하도록 설계를 바꾸는 것이 스펙의 "gallery_photos 연결만 정리, media 원본 보존"과 가장 잘 맞습니다. 보존이 꼭 필요하면 FK에 `ON DELETE CASCADE` 같은 DB 정책까지 명시해야 하지만, 차단형 삭제 정책과 데이터 의미상 연결 행 삭제가 더 단순합니다.

## Major

### `docs/superpowers/specs/2026-06-07-gallery-domain-design.md:150`

신규 파일 업로드 경로에서 `mediaService.upload()` 후 `MediaResponse.mimeType()`이 이미지가 아니면 예외를 던지도록 되어 있습니다. 현재 `MediaService.upload()`는 이미지와 PDF를 모두 정상 저장하며, `GalleryPhotoService.addPhotos()` 트랜잭션 안에서 호출될 가능성이 높습니다.

PDF 업로드 후 갤러리 서비스가 예외를 던지면 DB transaction은 rollback되지만, `FileStorage.store()`로 이미 쓴 파일은 transaction에 묶이지 않습니다. 문서의 "무해한 고아 media"가 아니라 DB row 없는 untracked orphan file이 생겨 중앙 미디어 라이브러리에서도 삭제할 수 없습니다.

해결: 저장 전에 이미지 여부를 검증할 수 있도록 `MediaService.uploadImage(...)` 또는 `MediaService.validateUploadableImage(MultipartFile)` 같은 좁은 인터페이스를 추가하세요. "기존 mediaIds 검증용 `requireImages` 1개만 추가"로는 신규 업로드 경로의 누수를 막을 수 없습니다.

### `docs/superpowers/specs/2026-06-07-gallery-domain-design.md:151`

사진 append가 `max(sort_order)+1` 기준인데, 같은 앨범에 동시에 사진을 추가하는 요청을 직렬화하는 장치가 없습니다. 인덱스도 `(album_id, sort_order)` 일반 인덱스라 중복을 막지 않습니다.

두 요청이 같은 `max(sort_order)`를 읽으면 같은 `sort_order`를 가진 사진이 생길 수 있고, 대표 썸네일과 목록 순서가 비결정적으로 흔들립니다.

해결: 앨범 row를 `SELECT ... FOR UPDATE`로 잠그거나, 앨범별 advisory lock을 잡은 뒤 append를 수행하도록 설계에 명시하세요. 추가로 `(album_id, sort_order)` unique constraint를 두면 회귀 방어가 됩니다.

## Minor

없음.

## Positive

- 갤러리 조회를 `GALLERY_VIEW`로 분리한 점은 스펙의 개인정보성 이미지 보호 목적과 일치합니다.
- 본문 media 참조에 정규식 경계를 둔 점은 기존 event/department Provider 관례와 맞고 `media:42` vs `media:420` 오탐을 줄입니다.
- 카드 조립에서 썸네일, 사진수, 태그, 작성자를 배치 조회로 분리한 점은 N+1 위험을 설계 단계에서 잘 차단합니다.
- `GalleryPhoto`를 컬렉션 매핑 없이 FK id로만 다루는 방향은 현재 코드베이스의 저결합 패턴과 맞습니다.

## Summary

전체 평가: Request Changes

이슈 통계: Critical 1개 / Major 2개 / Minor 0개

핵심 수정:
1. 앨범 삭제 시 `gallery_photos` 행 보존 정책과 media FK hard delete 정책의 충돌을 해소
2. 신규 파일 업로드를 저장 전에 이미지로 제한해 untracked orphan file 방지
3. 사진 append 순서 계산을 앨범 단위로 직렬화
