# 프론트 전달 문서: 부서 사진 연결 · 미디어 공개 범위 (church-backend #92)

대상: 새누리 프론트(saenuri-church #32), 은샘 프론트(eunsaem-frontend #181) 작업자.
백엔드 이슈: https://github.com/church-template/church-backend/issues/92
자세한 API 규칙의 정본은 `docs/church-frontend-guide.md` 5장·6장과 Swagger(`/docs/swagger-ui.html`)다. 이 문서는 "무엇이 바뀌었고 프론트가 무엇을 해야 하는지"만 추린 것이다.

> **#95로 일부 규칙이 바뀌었다(이 문서는 #92 시점 기록).** 공개 여부가 이제 교회의 메뉴 공개 범위와 설교·앨범의 "교인만"을 따르고, 설교·공지·일정 상세는 `mediaUrls`, 주보는 `url`(서명 주소)을 싣는다 — 아래 "설교 본문에는 이미지를 넣지 않는다"는 더 이상 맞지 않는다. 현재 규칙은 `docs/church-frontend-guide.md` 2.3·5.2.

## 1. 한눈에 보기

| 구분 | 내용 | 프론트 작업 |
|---|---|---|
| 필수 | `/api/media/{id}`가 더 이상 전부 공개가 아니다. 갤러리에만 있는 사진과 안 쓰인 파일은 **서명된 주소**로만 열린다 | 갤러리 화면, 관리 미디어 화면, 본문 편집 미리보기 |
| 선택 | 부서에 대표 사진(`heroMediaId`)과 활동 앨범(`albumId`)을 연결할 수 있다 | 공개 부서 페이지, 부서 관리 수정 창 |
| 변경 없음 | 공지·행사·부서 본문 이미지, 주보 PDF, 기존 부서 PUT/PATCH 요청 | 없음 |

**배포 순서**: ① 백엔드 배포(차단 꺼짐, 지금과 동작 같음) → ② **두 프론트가 2장 작업을 배포** → ③ 백엔드가 차단을 켠다. ②가 끝났다고 알려 줘야 ③을 진행한다. ② 전에 ③을 하면 갤러리와 관리 미디어 화면의 사진이 깨진다.

## 2. 필수: 미디어 공개 범위 대응

### 2.1 무엇이 공개인가

미디어가 아래 중 **한 곳이라도** 쓰이면 `GET /api/media/{id}`로 누구나 받을 수 있다. 요청 시점에 판단하므로, 공개된 곳에서 빼면 바로 비공개가 된다.

- 공지·행사·부서 본문의 `media:{id}`
- 부서 대표 사진
- 부서에 연결된 앨범의 사진
- 주보 PDF

아래는 공개 근거가 **아니다**. 차단이 켜지면 `/api/media/{id}`가 `404 RESOURCE_NOT_FOUND`다.

- 갤러리에만 있는 사진, 갤러리 앨범 설명 본문의 이미지
- 아직 아무 데도 안 쓰인 파일(방금 업로드한 것 포함)
- 설교 본문의 이미지 → 설교 본문에는 이미지를 넣지 않는다(서명 주소도 내려 주지 않는다)

### 2.2 서명된 주소

비공개 미디어는 API가 응답에 넣어 주는 주소로만 연다.

```
/api/media/123?exp=1794800000&sig=Zk3...   ← 응답에 들어 있는 값 (교회 접두사 없음)
${NEXT_PUBLIC_API_BASE}/api/media/123?exp=1794800000&sig=Zk3...   ← 실제 요청 주소
```

- 조립: `` `${NEXT_PUBLIC_API_BASE}${url}` ``. `url`을 고치거나 파라미터를 따로 만들지 않는다.
- 유효 시간 1시간. 만료되면 `404`. 화면을 오래 띄워 두었다가 사진이 깨지면 API를 다시 불러 새 주소를 받는다.
- 응답 헤더가 `Cache-Control: private`이다. 서명 주소가 든 API 응답을 ISR·서버 캐시·CDN에 1시간 넘게 담지 않는다.
- 서명 주소는 로그인한 교인·관리자에게만 내려간다. 공개 페이지나 공유 링크에 넣지 않는다.
- `exp`·`sig` 중 하나만 있거나 값이 틀리면 공개 사진이어도 `404`다. 공개 사진은 파라미터 없이 요청한다.

### 2.3 서명 주소가 들어 있는 응답

기존 필드는 그대로 있고, 아래 필드가 **추가**됐다.

| API | 추가 필드 | 쓰는 곳 |
|---|---|---|
| `GET /api/gallery/albums` (카드) | `thumbnailUrl` (썸네일 없으면 `null`) | 앨범 목록 썸네일. `thumbnailMediaId`로 주소를 조립하지 않는다 |
| `GET /api/gallery/albums/{id}` (상세) | `photos[].url` | 사진 표시 |
| 같은 응답 | `mediaUrls` (`{ "42": "/api/media/42?exp=…&sig=…" }`) | 앨범 설명 본문의 `media:{id}` 치환. 없는 id는 키가 없다 |
| `POST /api/admin/gallery/albums`, `PATCH …/{id}`, `POST …/{id}/photos` | 위와 같은 상세 응답 | 관리 화면 |
| `POST /api/admin/media`, `GET /api/admin/media`, `GET /api/admin/media/{id}` | `url` | 미디어 라이브러리 썸네일·"열기", 업로드 직후 미리보기 |

```jsonc
// GET /api/gallery/albums/7
{
  "id": 7, "title": "26년 야유회", "description": "![](media:42)",
  "photos": [
    { "id": 1, "mediaId": 101, "caption": null, "sortOrder": 0,
      "url": "/api/media/101?exp=1794800000&sig=Zk3..." }
  ],
  "mediaUrls": { "42": "/api/media/42?exp=1794800000&sig=9aQ..." }
}
```

### 2.4 할 일

- [ ] `api-docs.json` 사본 갱신
- [ ] 갤러리 앨범 목록: `thumbnailUrl` 사용
- [ ] 갤러리 앨범 상세: `photos[].url` 사용, 설명 본문의 `media:{id}`는 `mediaUrls`로 치환(키가 없으면 그 이미지는 표시하지 않는다)
- [ ] 관리 미디어 라이브러리: 썸네일과 "열기"를 `url`로
- [ ] **본문 편집 미리보기**: 공지·행사·부서 본문에 방금 넣은 이미지는 글을 저장하기 전까지 공개가 아니다. 미리보기는 업로드 응답(또는 관리 미디어 응답)의 `url`을 쓴다. 저장된 글을 보여 줄 때는 지금처럼 `/api/media/{id}`
- [ ] `next/image`를 쓰는 곳(권장 사항, 프론트의 Next.js 버전에서 확인할 것)
  - `remotePatterns`가 쿼리스트링 있는 주소를 허용하는지 확인한다
  - 이미지 최적화 캐시(`/_next/image?url=…`)는 주소가 만료되거나 사진을 내린 뒤에도 사진을 내줄 수 있다. 서명 주소 이미지는 `unoptimized`를 권한다
- [ ] 확인(차단을 켜기 전에도 주소 형식은 확인할 수 있다)
  - 로그인 상태: 갤러리 목록·상세, 관리 미디어 "열기", 편집 미리보기에서 요청 주소에 `exp`·`sig`가 붙어 있다
  - 로그아웃 상태: 공지·행사·부서·주보의 이미지와 PDF가 그대로 보인다

차단이 꺼진 동안에는 서명 없는 주소도 열리므로, 위 작업을 빠뜨려도 화면이 깨지지 않는다. 그래서 **요청 주소에 `exp`·`sig`가 붙었는지 직접 확인**해야 한다.

## 3. 선택: 부서 사진 연결

### 3.1 응답

```jsonc
// GET /api/departments  (공개, 카드 배열)
[{ "id": 3, "name": "오렌지 미션팀", "leader": "김목사", "parentId": null, "sortOrder": 10,
   "heroMediaId": 55 }]

// GET /api/departments/3  (공개)
{ "id": 3, "name": "오렌지 미션팀", "description": "…", "leader": "김목사", "parentId": null,
  "sortOrder": 10, "createdAt": "…", "updatedAt": "…", "version": 4,
  "heroMediaId": 55,          // 없으면 null
  "albumId": 7,               // 없거나 앨범이 삭제됐으면 null
  "photos": [101, 102, 103]   // 연결 앨범 사진의 mediaId, 앨범 순서. 없으면 []
}
```

- 사진 주소는 `${NEXT_PUBLIC_API_BASE}/api/media/{id}`(서명 없음). 부서에 연결된 사진은 공개다.
- 응답에 서명 주소가 없으므로 공개 부서 페이지는 ISR로 캐시해도 된다.

### 3.2 수정

`POST /api/admin/departments`, `PUT`·`PATCH /api/admin/departments/{id}` 요청에 `heroMediaId`, `albumId`를 넣을 수 있다(권한 `DEPT_WRITE`).

| 보낸 값 | PUT · PATCH 결과 |
|---|---|
| 칸을 안 보냄 | 기존 연결 **유지** |
| `null` | 연결 **비움** |
| 숫자 | 그 값으로 설정 |

- 그래서 **지금 프론트의 PUT은 고치지 않아도 사진 연결이 지워지지 않는다.**
- 비우려면 `null`을 명시한다. PATCH에는 `version`이 필수다: `{ "version": 4, "heroMediaId": null }`
- `400 INVALID_INPUT_VALUE`: 대표 사진이 없는 미디어이거나 이미지가 아닐 때, 앨범이 없거나 삭제됐을 때, 숫자가 아닌 값을 보냈을 때.

### 3.3 관리 화면에서 꼭 알릴 것

- **앨범을 부서에 연결하면 그 앨범의 사진 전체가 누구에게나 공개된다.** 연결한 뒤 그 앨범에 추가하는 사진도 마찬가지다. 앨범 선택 창과, 가능하면 갤러리 관리의 그 앨범 화면에 안내 문구를 넣는다. (백엔드의 갤러리 응답에는 "이 앨범은 공개 중"이라는 표시가 아직 없다.)
- 대표 사진으로 고른 이미지도 공개된다.

### 3.4 사진을 완전히 내리는 순서

1. 부서에서 연결을 끊는다: 대표 사진을 `null`로, 또는 앨범에서 그 사진을 뺀다. → 부서 페이지에서 바로 사라진다.
2. 미디어 라이브러리에서 그 사진을 삭제한다(`DELETE /api/admin/media/{id}`). 다른 곳에서 쓰고 있으면 `409 MEDIA_IN_USE`와 사용처 목록이 온다.
3. 이후 그 주소는 `404`다. 이미 본 방문자의 브라우저에는 최대 1일 남을 수 있다. 부서 페이지를 ISR로 캐시한다면 저장 직후 그 페이지 캐시를 비운다.

## 4. 상태 코드 요약

| 상황 | 응답 |
|---|---|
| 공개 미디어, 파라미터 없음 | `200`, `Cache-Control: public, max-age=86400` |
| 비공개 미디어, 파라미터 없음 | 차단 켜짐 `404` / 차단 꺼짐 `200` + `private` 캐시 |
| 유효한 서명 주소 | `200`, `Cache-Control: private, max-age=<남은 초>` |
| 서명 틀림·만료·`exp`/`sig` 하나만 있음 | `404 RESOURCE_NOT_FOUND` |
| 다른 교회의 미디어 id | `404 RESOURCE_NOT_FOUND` |
