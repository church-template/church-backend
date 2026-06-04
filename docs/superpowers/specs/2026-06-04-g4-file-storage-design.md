# G4 · 파일 저장 추상화 (FileStorage) 설계

> 작성일: 2026-06-04
> 대상 이슈: GitHub #5 (FileStorage 추상화 계층) — 로드맵 Phase 1 · 선행 의존 없음
> 출처 스펙: [`docs/church-backend-spec.md`](../../church-backend-spec.md) §8 (파일 저장), §10 (환경변수), §5.10 (미디어 — 후속 소비자)
> 상위 로드맵: [`2026-06-04-church-backend-workflow-design.md`](./2026-06-04-church-backend-workflow-design.md)
> 선행: [G2 공통·예외](./2026-06-04-g2-common-exception-design.md) (`ErrorCode`·`BusinessException` 재사용·확장)
> 후속: 미디어 라이브러리 도메인(§5.10) — 본 설계의 `FileStorage`를 **호출**해 `media` 테이블·업로드·서빙·참조추적·차단삭제를 완성

## 목표 / 성공 기준

스펙 §8의 파일 저장 토대를 `global/storage`에 못 박는다. **도메인 코드(`media` 테이블·컨트롤러·서빙 엔드포인트)는 만들지 않는다** — 업로드 파일을 로컬 디스크에 저장·조회·삭제하는 **순수 인프라**만 짓고, 컨트롤러가 없으므로 `@TempDir` 단위 테스트로 검증한다.

> 범위 확정(브레인스토밍): `media` 테이블의 `uploaded_by` FK가 아직 없는 **member 도메인에 의존**하므로, 서빙 엔드포인트(`GET /api/media/{id}`)와 미디어 라이브러리 전체는 후속 이슈로 미룬다. #5는 member 도메인 없이 완결되는 스토리지 추상화만 담당한다.

성공 기준:
1. `FileStorage` 인터페이스가 `store`·`load`·`delete` 3연산을 선언하고, 1차 구현 `LocalFileStorage`가 이를 충족한다.
2. `store`가 업로드 파일을 `FILE_UPLOAD_DIR` 아래 `yyyy/MM/{uuid}.{ext}`로 저장하고, DB가 보관할 **상대 경로 문자열**을 반환한다.
3. `FILE_MAX_SIZE` 초과 업로드를 `store` 시점에 거부한다(`413 FILE_SIZE_EXCEEDED`). 빈 파일도 거부한다(`400 INVALID_INPUT_VALUE`).
4. `load`가 저장 경로로 파일을 `Resource`로 돌려주고(향후 서빙용), 없으면 `404 RESOURCE_NOT_FOUND`.
5. `delete`가 저장 경로의 파일을 제거하고, 없으면 조용히 무시(idempotent).
6. 경로 우회(`../`) 공격을 `store`/`load`/`delete` 모든 경로에서 방어한다(정규화 + 업로드 루트 내부 검증).
7. 디스크 I/O 실패는 `500 FILE_STORAGE_ERROR`로 일관 매핑한다.
8. `global → domain` 의존 0 유지(ArchUnit green), 빌드·테스트 green, 신규 코드 커버리지 80%+.

## 핵심 결정

브레인스토밍에서 확정한 갈림길(권장안 채택):

1. **범위 = 스토리지 추상화만.** `FileStorage`·`LocalFileStorage`·`FileProperties` + `ErrorCode` 2종. `media` 테이블·업로드 컨트롤러·서빙 엔드포인트·참조추적·차단삭제는 **제외**(member 도메인 의존 → 후속). 이슈 본문 "선행 의존 없음(Phase 1)"과 정확히 일치.
2. **입력 = `MultipartFile` 직접.** `store(MultipartFile)`. 가장 단순하며 향후 미디어 컨트롤러가 업로드받은 `MultipartFile`을 그대로 전달. Local·S3 구현 모두 처리 가능. `global`이 `spring-web`(이미 classpath)에 의존하는 게 유일 비용 — simplicity-first 원칙에 부합.
3. **디스크 레이아웃 = 날짜 샤딩 + UUID.** `yyyy/MM/{uuid}.{ext}`. 단일 디렉터리 비대를 막고 백업·관리자 브라우징이 쉽다. 원본 파일명은 **디스크에 쓰지 않고**(충돌·경로우회 차단), 향후 `media.filename` 컬럼이 보관한다. **확장자는 basename에서만 추출**(예 `StringUtils.getFilenameExtension`)해 소문자 ASCII 화이트리스트(`^[a-z0-9]{1,10}$`)를 통과할 때만 보존하고(서빙 시 content-type 힌트), 통과 못 하거나 없으면 **확장자 없이** 저장한다 — `a.jpg/../evil`·`weird.<script>`·`a.` 같은 입력이 키 계약을 깨지 못하게 한다(슬래시·`..`·빈/비정상 확장자 차단). `startsWith(root)` 가드만으론 `jpg/../../x`류가 루트 내부 다른 경로로 새는 것을 못 막으므로 확장자 화이트리스트가 1차 방어다.
4. **크기 검증 = `store()` 내부만.** `file.getSize() > maxSize`면 `FILE_SIZE_EXCEEDED`(413). #5엔 업로드 엔드포인트가 없으므로 서블릿 multipart 한도(`spring.servlet.multipart.max-file-size`)와 `MaxUploadSizeExceededException` 핸들러는 **후속 미디어 이슈**로 미룬다. 추상화가 규칙을 소유하고, 웹 레이어 없이 `store` 단위로 완결 테스트된다.
5. **상한은 운영자 조정값(단일 전역).** `FILE_MAX_SIZE`(바이트, 기본 10MB) 하나로 모든 업로드에 적용. 코드 하드코딩 없이 교회별 `.env`로 조정. 초과 시 **거부**할 뿐 리사이즈·압축하지 않음(렌더·가공은 프론트 몫). 타입별 한도(이미지/PDF)는 MIME 분기가 필요해 media 도메인 몫 → 도입 시 후속. (스펙 §8·§10에 명문화 완료.)
6. **URL 조립은 스토리지에 넣지 않음.** 스펙 §8 "URL = `FILE_BASE_URL` + 미디어 id"의 *미디어 id*는 `media` 테이블(DB) 개념이고 `FileStorage`는 `stored_path`만 다룬다. 프론트가 렌더 시 `media:{id}` → `${FILE_BASE_URL}/{id}`로 치환하고, 백엔드는 향후 `GET /api/media/{id}`로 바이트만 서빙. #5에선 `FileProperties.baseUrl()`을 **바인딩만** 해두고 실제 id 조립은 media 도메인으로 미뤄, 스토리지 계층에 media-id 의미가 새지 않게 한다.
7. **MIME 무관.** 이미지·PDF 화이트리스트(§5.10)는 media 도메인 정책. `FileStorage`는 타입 무관하게 바이트만 저장. 이슈 본문도 크기 검증만 명시.
8. **`FileProperties` 등록 = `LocalFileStorage`에 colocate.** 빈 `StorageConfig` 클래스를 새로 만들지 않고, 유일 소비자인 `LocalFileStorage`에 `@EnableConfigurationProperties(FileProperties.class)`를 부착(기존 `SecurityConfig`의 `JwtProperties` 등록 패턴 준용).

## 범위 경계 (G4에서 하는 것 vs 후속 미디어 도메인으로 미루는 것)

| 영역 | G4 (이번) | 미디어 도메인 (후속) |
|---|---|---|
| 저장 추상화 | `FileStorage` 인터페이스 + `LocalFileStorage` 구현 | 업로드 시 `store(file)` **호출** |
| 크기 검증 | `store()`에서 `FILE_MAX_SIZE` 강제 | 서블릿 multipart 한도·`MaxUploadSizeExceededException` 핸들러 |
| 설정 | `FileProperties`(uploadDir·baseUrl·maxSize) 바인딩·검증 | `baseUrl`로 id→URL 조립 (또는 프론트가 치환) |
| 파일 식별 | 디스크 `stored_path` 생성·반환 | `media.id`·`media.filename`·`mime_type` 컬럼 보관 |
| MIME 정책 | 무관(타입 불문 저장) | 이미지·PDF 화이트리스트 |
| 엔드포인트 | **없음** (`@TempDir` 단위테스트로 검증) | `POST /api/admin/media`, `GET /api/media/{id}` 등 |
| 삭제 | 파일 물리 제거(idempotent) | 참조추적(LIKE+FK) + 차단삭제(409 `MEDIA_IN_USE`) |

## 산출물 (파일)

신규 — `global/storage/`:

```text
FileStorage.java        // 인터페이스: store(MultipartFile)->String, load(String)->Resource, delete(String)->void
LocalFileStorage.java   // @Component 구현 + @EnableConfigurationProperties(FileProperties.class)
FileProperties.java     // @Validated @ConfigurationProperties("file") record: uploadDir(@NotBlank), baseUrl(@NotBlank · G4 미사용=바인딩/검증만, media 도메인이 소비), maxSize(@Positive)
```

수정 — 기존 파일:

- `global/exception/ErrorCode.java` — `FILE_SIZE_EXCEEDED`(413 PAYLOAD_TOO_LARGE), `FILE_STORAGE_ERROR`(500 INTERNAL_SERVER_ERROR) 2종 추가. 빈 파일은 기존 `INVALID_INPUT_VALUE` 재사용.
- `.env.example` — `FILE_MAX_SIZE` 줄에 운영자용 주석 추가(스펙 §10과 일관).

신규 — 테스트:

```text
test/.../global/storage/LocalFileStorageTest.java   // @TempDir 단위 (Spring 컨텍스트 불필요)
```

> `application.yml`의 `file.upload-dir`·`file.base-url`·`file.max-size`는 이미 바인딩되어 있다(이전 이슈). `.env.example`의 `FILE_MAX_SIZE` 주석은 구현 시 운영자 일관성용으로 함께 다듬는다.

## 인터페이스 계약

```java
public interface FileStorage {

    /**
     * 파일을 저장하고 저장 키(루트 기준 상대 경로, 예: "2026/06/{uuid}.jpg")를 반환한다.
     * 반환값은 media.stored_path에 그대로 보관된다.
     * @throws BusinessException INVALID_INPUT_VALUE(빈 파일), FILE_SIZE_EXCEEDED(한도 초과), FILE_STORAGE_ERROR(I/O 실패)
     */
    String store(MultipartFile file);

    /**
     * 저장 키로 파일을 조회한다(서빙·다운로드용). 루트 내부의 일반 파일만 대상.
     * @throws BusinessException RESOURCE_NOT_FOUND(없음·루트밖·디렉터리/특수파일), FILE_STORAGE_ERROR(I/O 실패)
     */
    Resource load(String storedPath);

    /**
     * 저장 키의 파일을 삭제한다. 관리 대상(루트 내부 일반 파일)만 제거하고,
     * 미존재·루트밖·디렉터리/특수파일은 모두 no-op(idempotent). @throws BusinessException FILE_STORAGE_ERROR(I/O 실패)
     */
    void delete(String storedPath);
}
```

- 반환 `String` = 슬래시(`/`)로 정규화한 OS 독립 상대 경로.
- `load` 반환 = `org.springframework.core.io.Resource` (향후 서빙 컨트롤러의 `ResponseEntity<Resource>`에 직결).

## LocalFileStorage 동작

**생성:** `FileProperties` 주입 → `root = Paths.get(uploadDir).toAbsolutePath().normalize()` 보관.

**store(file):**
1. `file.isEmpty()` → `INVALID_INPUT_VALUE`(400).
2. `file.getSize() > maxSize` → `FILE_SIZE_EXCEEDED`(413).
3. 키 생성: `LocalDate.now()` → `"yyyy/MM"`, 파일명 = `UUID.randomUUID()` + 안전 확장자. **확장자 = basename에서만 추출**(`getFilenameExtension`) → 소문자화 → 화이트리스트 `^[a-z0-9]{1,10}$` 통과 시에만 `"." + ext`, 아니면 생략. 원본 파일명 자체는 디스크 경로에 미사용(슬래시·`..`·비정상 문자 차단).
4. `target = root.resolve(key).normalize()` → `target.startsWith(root)` 검증(방어), `Files.createDirectories(target.getParent())`, `file.transferTo(target)` (또는 `Files.copy(in, target)`).
5. `IOException` → 부분 저장 파일 best-effort 삭제(`Files.deleteIfExists(target)`) 후 `FILE_STORAGE_ERROR`(500). 성공 시 `key`(슬래시 정규화) 반환. (임시파일+`ATOMIC_MOVE`는 이번 범위 밖 — best-effort 삭제로 충분.)

**load(storedPath):**
1. `target = root.resolve(storedPath).normalize()` → `target.startsWith(root)` 검증(경로우회 차단). 위반 시 `RESOURCE_NOT_FOUND`(존재 비노출).
2. `Files.exists && Files.isRegularFile && Files.isReadable`가 **모두 참일 때만** 정상 → `PathResource`/`UrlResource` 반환. 그 외(미존재·비가독·디렉터리/특수파일·루트밖) → `RESOURCE_NOT_FOUND`(404).

**delete(storedPath):**
1. `load`와 동일한 정규화 + 루트내부 검증.
2. **관리 대상(루트 내부의 일반 파일, `Files.isRegularFile`)일 때만** `Files.deleteIfExists(target)`로 제거. 그 외(미존재·루트밖·디렉터리/특수파일)는 모두 **no-op**(best-effort, 필요 시 로그). `IOException` → `FILE_STORAGE_ERROR`.

> delete는 "관리 파일을 없앤다"가 목표라 **idempotent로 통일**한다 — 미존재·경로우회·디렉터리는 전부 조용히 no-op(루트 밖은 애초에 건드리지 않음). 예외는 실제 I/O 실패(`FILE_STORAGE_ERROR`)뿐이라, 호출자에게 보안 이벤트를 노출하지 않는다. load는 값을 돌려줘야 하므로 같은 비정상 입력을 `RESOURCE_NOT_FOUND`로 던진다(반환 불가라 무시할 수 없음).

## 보안 — 경로 우회 차단

- `store` 키는 우리가 UUID로 생성하므로 원천 안전. 추가로 `target.startsWith(root)`를 항상 검증.
- `load`/`delete`가 받는 `storedPath`는 (현재는 신뢰 가능한 DB 유래지만) 항상 `normalize()` 후 루트 내부인지 검증해 `../` 탈출을 방어한다(defense-in-depth).
- 원본 파일명은 디스크 경로에 절대 반영하지 않는다(파일명 인젝션·충돌 차단). 확장자도 basename 추출 후 `^[a-z0-9]{1,10}$` 화이트리스트만 허용 — `startsWith(root)` 가드를 통과하더라도 키 계약을 깨는 입력(`jpg/../../x`)을 1차에서 거른다.
- `load`/`delete`는 루트 내부의 **일반 파일**(`Files.isRegularFile`)만 대상 — 디렉터리·특수파일은 조회/삭제하지 않는다(데이터 무결성·defense-in-depth). 단 현재 서빙 경로는 id 기반이라 외부가 원시 경로를 직접 넘기지는 않는다.

## 테스트 (TDD, `@TempDir` 단위)

`@TempDir` 경로로 만든 `FileProperties`로 `LocalFileStorage`를 직접 생성 → Spring 컨텍스트 없이 빠르게 검증:

| # | 케이스 | 기대 |
|---|---|---|
| 1 | 정상 store | 반환 키가 `yyyy/MM/<uuid>.<ext>` 형식, 실제 파일 기록·내용 일치 |
| 2 | 크기 초과 | `FILE_SIZE_EXCEEDED` |
| 3 | 빈 파일 | `INVALID_INPUT_VALUE` |
| 4 | 확장자 없는 원본명 | 키에 확장자 없이 저장 성공 |
| 5 | 원본명 `a.jpg/../evil`·`weird.<script>`·`a.` 등 | 반환 키가 정확히 `yyyy/MM/<uuid>(.<safe-ext>)?` 패턴(슬래시·`..`·비정상 문자 0), 안전 확장자만 보존 또는 확장자 없이 저장 |
| 6 | load 존재 | `Resource` 가독·내용 일치 |
| 7 | load 미존재(루트 내부) | `RESOURCE_NOT_FOUND` |
| 8 | load storedPath=`../../etc/passwd` | `RESOURCE_NOT_FOUND`(루트밖, 외부 파일 미노출) |
| 9 | delete storedPath=`../../etc/passwd` | no-op(외부 파일 무손상, 예외 없음) |
| 10 | load/delete storedPath=디렉터리(`2026/06`) | load `RESOURCE_NOT_FOUND` / delete no-op (`isRegularFile`) |
| 11 | delete 존재 | 파일 제거 |
| 12 | delete 미존재(루트 내부) | no-op(idempotent) |
| 13 | (선택) store I/O 실패 | 부분 파일 미잔존(best-effort 삭제) |
| 14 | (선택) `FileProperties` 검증 | `maxSize` 0/음수·빈 `uploadDir` 기동 fail-fast (`ApplicationContextRunner`) |

## 미루는 것 (명시적 비범위)

- `media` 테이블·엔티티·레포지토리 (member 도메인 `uploaded_by` FK 의존).
- 업로드/조회/삭제 **컨트롤러**와 `GET /api/media/{id}` 서빙 엔드포인트.
- 참조추적(본문 LIKE + 갤러리/주보 FK)과 차단형 삭제(409 `MEDIA_IN_USE`).
- 서블릿 multipart 한도·`MaxUploadSizeExceededException` 핸들러(엔드포인트 생길 때).
- MIME 화이트리스트(이미지·PDF), 타입별 크기 한도.
- id→URL 조립(프론트 치환/미디어 도메인).
- S3·OCI Object Storage 구현체(인터페이스만 미리 고정).
