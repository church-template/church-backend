# G4 파일 저장(FileStorage 추상화) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 업로드 파일을 로컬 디스크에 저장·조회·삭제하는 `FileStorage` 추상화 계층을 `global/storage`에 구현한다(향후 S3/OCI로 구현체만 교체 가능).

**Architecture:** `FileStorage` 인터페이스 + 1차 구현 `LocalFileStorage`(`@Component`). 파일은 `FILE_UPLOAD_DIR` 아래 `yyyy/MM/{uuid}.{ext}`로 저장하고 DB가 보관할 상대 키를 반환한다. 설정은 `@Validated @ConfigurationProperties("file")` record(`FileProperties`)로 바인딩·fail-fast 검증한다. 도메인 코드(`media` 테이블·컨트롤러·서빙)는 만들지 않는다 — 순수 인프라만.

**Tech Stack:** Java 21, Spring Boot 4.0.6, `spring-web`(MultipartFile), `org.springframework.util.StringUtils`, JUnit 5(`@TempDir`), AssertJ, `MockMultipartFile`(spring-test), JaCoCo, palantir spotless.

**참조 설계:** [`docs/superpowers/specs/2026-06-04-g4-file-storage-design.md`](../specs/2026-06-04-g4-file-storage-design.md) (코드리뷰 반영 완료).

**브랜치:** `20260603_#5_FileStorage_추상화_LocalFileStorage_구현` (이미 체크아웃됨).

---

## File Structure

생성:
- `src/main/java/com/elipair/church/global/storage/FileProperties.java` — `file.*` 설정 record(검증)
- `src/main/java/com/elipair/church/global/storage/FileStorage.java` — 저장 추상화 인터페이스
- `src/main/java/com/elipair/church/global/storage/LocalFileStorage.java` — 로컬 디스크 구현(`@Component`, `FileProperties` 등록 colocate)
- `src/test/java/com/elipair/church/global/storage/FilePropertiesValidationTest.java` — 설정 검증 fail-fast 증명
- `src/test/java/com/elipair/church/global/storage/LocalFileStorageTest.java` — `@TempDir` 단위 테스트(저장·조회·삭제·보안)

수정:
- `src/main/java/com/elipair/church/global/exception/ErrorCode.java` — `FILE_SIZE_EXCEEDED`·`FILE_STORAGE_ERROR` 2종 추가
- `.env.example` — `FILE_MAX_SIZE` 줄에 운영자용 주석 추가

> **공통 규약:** 각 Task 마지막 커밋 전 `./gradlew spotlessApply`로 palantir 포맷을 맞춘다. 커밋 메시지는 한국어 컨벤셔널(`feat`/`chore`)이며 끝에 `#5`를 붙인다. **Co-Authored-By 태그 금지.** push 하지 않는다.

---

## Task 1: FileProperties 설정 바인딩·검증

**Files:**
- Create: `src/main/java/com/elipair/church/global/storage/FileProperties.java`
- Test: `src/test/java/com/elipair/church/global/storage/FilePropertiesValidationTest.java`

- [ ] **Step 1: 실패하는 검증 테스트 작성**

`src/test/java/com/elipair/church/global/storage/FilePropertiesValidationTest.java`:

```java
package com.elipair.church.global.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * @Validated 검증 fail-fast 증명. file.max-size=0(또는 음수)·빈 upload-dir이면 컨텍스트 기동이 실패해야 한다.
 */
class FilePropertiesValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(EnableFileProps.class);

    @Test
    void context_fails_when_max_size_not_positive() {
        runner.withPropertyValues("file.upload-dir=/tmp/up", "file.base-url=http://x/api/media", "file.max-size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void context_fails_when_upload_dir_blank() {
        runner.withPropertyValues("file.upload-dir=", "file.base-url=http://x/api/media", "file.max-size=1024")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void context_starts_with_valid_properties() {
        runner.withPropertyValues("file.upload-dir=/tmp/up", "file.base-url=http://x/api/media", "file.max-size=1024")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @EnableConfigurationProperties(FileProperties.class)
    @Configuration
    static class EnableFileProps {}
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew compileTestJava`
Expected: FAIL — `FileProperties` 심볼을 찾을 수 없음(`cannot find symbol: class FileProperties`).

- [ ] **Step 3: FileProperties record 작성**

`src/main/java/com/elipair/church/global/storage/FileProperties.java`:

```java
package com.elipair.church.global.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 파일 저장 설정(스펙 §8·§10). uploadDir=저장 루트, maxSize=업로드 최대 바이트.
 * baseUrl(공개 URL 베이스)은 G4에서는 바인딩/검증만 하며 LocalFileStorage 동작에는 관여하지 않는다(media 도메인이 소비).
 * @Validated로 기동 시 fail-fast(빈 경로·0/음수 한도 거부) — application.yml 기본값이 있어 정상 배포는 통과한다.
 */
@Validated
@ConfigurationProperties(prefix = "file")
public record FileProperties(
        @NotBlank String uploadDir,
        @NotBlank String baseUrl,
        @Positive long maxSize) {}
```

- [ ] **Step 4: 검증 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.storage.FilePropertiesValidationTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: 포맷 후 커밋**

```bash
./gradlew spotlessApply
git add src/main/java/com/elipair/church/global/storage/FileProperties.java \
        src/test/java/com/elipair/church/global/storage/FilePropertiesValidationTest.java
git commit -m "feat : FileProperties 설정 바인딩·검증 추가 #5"
```

---

## Task 2: 파일 에러 코드 2종 추가

**Files:**
- Modify: `src/main/java/com/elipair/church/global/exception/ErrorCode.java`

- [ ] **Step 1: ErrorCode enum에 2종 추가**

`ErrorCode.java`에서 `DUPLICATE_RESOURCE` 줄과 `INTERNAL_ERROR` 줄 사이에 두 상수를 삽입한다. 아래 블록의 `old` → `new`로 교체:

old:
```java
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", "이미 존재하는 리소스입니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
```

new:
```java
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", "이미 존재하는 리소스입니다"),
    FILE_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_SIZE_EXCEEDED", "파일 크기가 허용 한도를 초과했습니다"),
    FILE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_ERROR", "파일 처리 중 오류가 발생했습니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다");
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: PASS (`BUILD SUCCESSFUL`). 두 상수는 Task 4의 단위 테스트가 소비·검증한다.

- [ ] **Step 3: 포맷 후 커밋**

```bash
./gradlew spotlessApply
git add src/main/java/com/elipair/church/global/exception/ErrorCode.java
git commit -m "feat : 파일 에러 코드(FILE_SIZE_EXCEEDED·FILE_STORAGE_ERROR) 추가 #5"
```

---

## Task 3: FileStorage 인터페이스 정의

**Files:**
- Create: `src/main/java/com/elipair/church/global/storage/FileStorage.java`

- [ ] **Step 1: 인터페이스 작성**

`src/main/java/com/elipair/church/global/storage/FileStorage.java`:

```java
package com.elipair.church.global.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 파일 저장 추상화(스펙 §8). 1차 구현은 LocalFileStorage(로컬 디스크).
 * 향후 S3/OCI Object Storage로 옮길 때 구현체만 교체한다.
 */
public interface FileStorage {

    /**
     * 파일을 저장하고 저장 키(루트 기준 상대 경로, 예: "2026/06/{uuid}.jpg")를 반환한다.
     * 반환값은 media.stored_path에 그대로 보관된다.
     * @throws com.elipair.church.global.exception.BusinessException
     *         INVALID_INPUT_VALUE(빈 파일), FILE_SIZE_EXCEEDED(한도 초과), FILE_STORAGE_ERROR(I/O 실패)
     */
    String store(MultipartFile file);

    /**
     * 저장 키로 파일을 조회한다(서빙·다운로드용). 루트 내부의 일반 파일만 대상.
     * @throws com.elipair.church.global.exception.BusinessException
     *         RESOURCE_NOT_FOUND(없음·루트밖·디렉터리/특수파일), FILE_STORAGE_ERROR(I/O 실패)
     */
    Resource load(String storedPath);

    /**
     * 저장 키의 파일을 삭제한다. 관리 대상(루트 내부 일반 파일)만 제거하고,
     * 미존재·루트밖·디렉터리/특수파일은 모두 no-op(idempotent).
     * @throws com.elipair.church.global.exception.BusinessException FILE_STORAGE_ERROR(I/O 실패)
     */
    void delete(String storedPath);
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: PASS.

- [ ] **Step 3: 포맷 후 커밋**

```bash
./gradlew spotlessApply
git add src/main/java/com/elipair/church/global/storage/FileStorage.java
git commit -m "feat : FileStorage 인터페이스 정의 #5"
```

---

## Task 4: LocalFileStorage 로컬 디스크 구현 (저장·조회·삭제)

**Files:**
- Create: `src/main/java/com/elipair/church/global/storage/LocalFileStorage.java`
- Test: `src/test/java/com/elipair/church/global/storage/LocalFileStorageTest.java`

- [ ] **Step 1: 실패하는 단위 테스트 작성(전체)**

`src/test/java/com/elipair/church/global/storage/LocalFileStorageTest.java`:

```java
package com.elipair.church.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

/**
 * @TempDir 기반 단위 테스트(Spring 컨텍스트 불필요). maxSize=1024로 설정.
 */
class LocalFileStorageTest {

    @TempDir
    Path tempDir;

    private LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage(new FileProperties(tempDir.toString(), "http://localhost/api/media", 1024));
    }

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "image/jpeg", content);
    }

    @Test
    void store_writes_file_and_returns_sharded_key() throws IOException {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        String key = storage.store(file("photo.JPG", content));

        assertThat(key).matches("\\d{4}/\\d{2}/[0-9a-f-]{36}\\.jpg");
        assertThat(Files.readAllBytes(tempDir.resolve(key))).isEqualTo(content);
    }

    @Test
    void store_rejects_oversized_file() {
        byte[] big = new byte[1025]; // maxSize=1024
        assertThatThrownBy(() -> storage.store(file("big.jpg", big)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
    }

    @Test
    void store_rejects_empty_file() {
        assertThatThrownBy(() -> storage.store(file("empty.jpg", new byte[0])))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void store_without_extension_succeeds() {
        String key = storage.store(file("noext", "x".getBytes(StandardCharsets.UTF_8)));
        assertThat(key).matches("\\d{4}/\\d{2}/[0-9a-f-]{36}");
    }

    @Test
    void store_sanitizes_malicious_filename_extension() {
        for (String name : new String[] {"a.jpg/../evil", "weird.<script>", "a."}) {
            String key = storage.store(file(name, "x".getBytes(StandardCharsets.UTF_8)));
            assertThat(key).matches("\\d{4}/\\d{2}/[0-9a-f-]{36}(\\.[a-z0-9]{1,10})?");
            assertThat(key).doesNotContain("..", "<", ">");
            assertThat(tempDir.resolve(key).normalize()).startsWith(tempDir);
        }
    }

    @Test
    void load_returns_readable_resource() throws IOException {
        byte[] content = "data".getBytes(StandardCharsets.UTF_8);
        String key = storage.store(file("a.png", content));

        Resource resource = storage.load(key);

        assertThat(resource.getContentAsByteArray()).isEqualTo(content);
    }

    @Test
    void load_missing_throws_not_found() {
        assertThatThrownBy(() -> storage.load("2026/06/does-not-exist.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void load_path_traversal_throws_not_found() {
        assertThatThrownBy(() -> storage.load("../../etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void load_directory_throws_not_found() {
        String key = storage.store(file("a.jpg", "x".getBytes(StandardCharsets.UTF_8)));
        String dir = key.substring(0, key.indexOf('/', key.indexOf('/') + 1)); // "yyyy/MM"

        assertThatThrownBy(() -> storage.load(dir))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void delete_removes_existing_file() {
        String key = storage.store(file("a.jpg", "x".getBytes(StandardCharsets.UTF_8)));

        storage.delete(key);

        assertThat(Files.exists(tempDir.resolve(key))).isFalse();
    }

    @Test
    void delete_missing_is_noop() {
        assertThatCode(() -> storage.delete("2026/06/missing.jpg")).doesNotThrowAnyException();
    }

    @Test
    void delete_path_traversal_is_noop_and_keeps_external_file() throws IOException {
        Path outside = tempDir.resolveSibling("external-" + tempDir.getFileName() + ".txt");
        Files.writeString(outside, "keep");
        try {
            String traversal = "../" + outside.getFileName();
            assertThatCode(() -> storage.delete(traversal)).doesNotThrowAnyException();
            assertThat(Files.exists(outside)).isTrue();
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void delete_directory_is_noop() {
        String key = storage.store(file("a.jpg", "x".getBytes(StandardCharsets.UTF_8)));
        String dir = key.substring(0, key.indexOf('/', key.indexOf('/') + 1));

        assertThatCode(() -> storage.delete(dir)).doesNotThrowAnyException();
        assertThat(Files.exists(tempDir.resolve(dir))).isTrue();
    }
}
```

- [ ] **Step 2: 컴파일/실행 실패 확인**

Run: `./gradlew compileTestJava`
Expected: FAIL — `LocalFileStorage` 심볼을 찾을 수 없음.

- [ ] **Step 3: LocalFileStorage 구현**

`src/main/java/com/elipair/church/global/storage/LocalFileStorage.java`:

```java
package com.elipair.church.global.storage;

import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로컬 디스크 파일 저장 1차 구현(스펙 §8). FILE_UPLOAD_DIR 아래 yyyy/MM/{uuid}.{ext}로 저장한다.
 * FileProperties 등록은 유일 소비자인 본 클래스에 colocate(@EnableConfigurationProperties).
 */
@Component
@EnableConfigurationProperties(FileProperties.class)
public class LocalFileStorage implements FileStorage {

    private static final DateTimeFormatter SHARD = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final Pattern SAFE_EXT = Pattern.compile("^[a-z0-9]{1,10}$");

    private final Path root;
    private final long maxSize;

    public LocalFileStorage(FileProperties properties) {
        this.root = Paths.get(properties.uploadDir()).toAbsolutePath().normalize();
        this.maxSize = properties.maxSize();
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "빈 파일은 저장할 수 없습니다");
        }
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
        String key =
                SHARD.format(LocalDate.now()) + "/" + UUID.randomUUID() + extensionSuffix(file.getOriginalFilename());
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
        }
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target);
        } catch (IOException e) {
            deleteQuietly(target); // 부분 저장 파일 best-effort 정리
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
        }
        return key;
    }

    @Override
    public Resource load(String storedPath) {
        Path target = resolveWithinRoot(storedPath);
        if (target == null || !Files.isRegularFile(target) || !Files.isReadable(target)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return new PathResource(target);
    }

    @Override
    public void delete(String storedPath) {
        Path target = resolveWithinRoot(storedPath);
        if (target == null || !Files.isRegularFile(target)) {
            return; // 미존재·루트밖·디렉터리 → no-op (idempotent)
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    /** basename 확장자만 추출 → 소문자 → 화이트리스트(^[a-z0-9]{1,10}$) 통과 시 ".ext", 아니면 "". */
    private String extensionSuffix(String originalFilename) {
        String ext = StringUtils.getFilenameExtension(originalFilename);
        if (ext == null) {
            return "";
        }
        ext = ext.toLowerCase(Locale.ROOT);
        return SAFE_EXT.matcher(ext).matches() ? "." + ext : "";
    }

    /** storedPath를 루트 기준으로 정규화. 빈 값이거나 루트를 벗어나면 null. */
    private Path resolveWithinRoot(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }
        Path target = root.resolve(storedPath).normalize();
        return target.startsWith(root) ? target : null;
    }

    private void deleteQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
```

- [ ] **Step 4: 단위 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.storage.LocalFileStorageTest'`
Expected: PASS (13 tests).

- [ ] **Step 5: 포맷 후 커밋**

```bash
./gradlew spotlessApply
git add src/main/java/com/elipair/church/global/storage/LocalFileStorage.java \
        src/test/java/com/elipair/church/global/storage/LocalFileStorageTest.java
git commit -m "feat : LocalFileStorage 로컬 디스크 구현(저장·조회·삭제) #5"
```

---

## Task 5: .env.example 주석 + 전체 빌드·검증

**Files:**
- Modify: `.env.example`

- [ ] **Step 1: .env.example의 FILE_MAX_SIZE 줄에 주석 추가**

old:
```bash
FILE_MAX_SIZE=10485760
```

new:
```bash
FILE_MAX_SIZE=10485760       # 업로드 최대 크기(바이트). 운영자 조정값, 기본 10MB. 초과 시 업로드 거부
```

- [ ] **Step 2: 전체 빌드·테스트(컨텍스트 로드·ArchUnit 포함)**

Run: `./gradlew clean build`
Expected: `BUILD SUCCESSFUL`.
- `ChurchBackendApplicationTests`(컨텍스트 로드)가 `LocalFileStorage` 빈 배선(@EnableConfigurationProperties on @Component)을 검증한다 — application.yml의 `file.*` 기본값으로 통과한다.
- `ArchitectureTest`(domain → global 단방향)는 storage가 `global/exception`·JDK·Spring만 의존하므로 green.
- 실패 시: 새 코드 외 회귀가 아닌지 확인하고 해당 Task로 돌아가 수정.

- [ ] **Step 3: 신규 코드 커버리지 확인(80%+)**

Run: `./gradlew jacocoTestReport`
Then 확인: `build/reports/jacoco/test/html/com.elipair.church.global.storage/index.html`
Expected: `LocalFileStorage`·`FileProperties` 라인 커버리지 80% 이상(13+3 테스트로 store/load/delete·helper 전 분기 커버).

- [ ] **Step 4: 커밋**

```bash
git add .env.example
git commit -m "chore : .env.example FILE_MAX_SIZE 운영자 주석 추가 #5"
```

---

## Self-Review (계획 작성자 점검 완료)

**1. 스펙 커버리지** — 설계 문서 대비:
- FileStorage 인터페이스(store/load/delete) → Task 3 ✓
- LocalFileStorage(날짜 샤딩+UUID·확장자 화이트리스트·크기검증·경로우회·isRegularFile·best-effort 정리·no-op delete) → Task 4 ✓
- FileProperties(@Validated, baseUrl 미사용 명시) → Task 1 ✓
- ErrorCode 2종(FILE_SIZE_EXCEEDED 413·FILE_STORAGE_ERROR 500) → Task 2 ✓
- @TempDir 단위 테스트 → Task 1(설정 검증 3) + Task 4(13) = 16개 ✓. 설계 표 row 1~12·14 전부 커버(디렉터리 row는 load/delete 2개로 분리). **단 row 13(선택: store I/O 실패→부분 파일 미잔존)은 실패 주입이 필요해 단위 테스트에서 제외** — best-effort 정리는 구현 코드(`deleteQuietly`)로만 보장한다(설계에서도 선택 항목).
- .env.example 주석 → Task 5 ✓
- 미루는 것(media 테이블·컨트롤러·서빙·서블릿 multipart·MIME·id→URL·S3) → 본 계획에서 전부 제외 ✓

**2. Placeholder 스캔** — TBD/TODO/“적절히 처리” 없음. 모든 코드·명령·기대 출력 구체화 ✓

**3. 타입 일관성** — `FileProperties(uploadDir, baseUrl, maxSize)` 생성자 시그니처가 Task 1 정의·Task 4 테스트(`new FileProperties(tempDir.toString(), ..., 1024)`)·LocalFileStorage 생성자에서 일치. `ErrorCode.FILE_SIZE_EXCEEDED`/`FILE_STORAGE_ERROR`가 Task 2 정의·Task 4 단언에서 일치. `store→String`/`load→Resource`/`delete→void`가 인터페이스·구현·테스트에서 일치 ✓
