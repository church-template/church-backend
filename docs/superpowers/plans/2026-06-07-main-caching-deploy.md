# #18 메인 통합 API · Redis 캐싱 · view_count · 배포 검증 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /api/main` 통합 조회 + Redis 캐싱 + view_count Redis 버퍼/주기 플러시를 구현하고, 검증에서 드러난 예외·배포·Swagger 갭을 닫는다.

**Architecture:** 신규 `domain/main`이 기존 설교·공지·일정 서비스를 조합한다. Redis는 `global/config/CacheConfig`(`@EnableCaching`+`RedisCacheManager`)로 캐시 추상화를 켜고, view_count는 `global/viewcount`의 Redis 버퍼 + `@Scheduled` 플러시(도메인이 `ViewCountFlushTarget` SPI 구현, domain→global 단방향)로 전환한다. Swagger는 공통 에러응답을 `OperationCustomizer`로 일괄 주입하고 컨트롤러/DTO에 `@Tag`/`@Operation`/`@Schema`를 수기로 단다.

**Tech Stack:** Java 21, Spring Boot 4.0.6 (webmvc, data-jpa, data-redis, cache, scheduling), PostgreSQL 16, Redis 7, springdoc-openapi 3.0.0, Testcontainers, JUnit 5 + MockMvc + Mockito + AssertJ.

**설계 근거:** `docs/superpowers/specs/2026-06-07-main-caching-deploy-design.md`

---

## 코드베이스 관례 (구현 전 필독)

- **테스트 종류 3종(기존 패턴):**
  - **통합(API):** `@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class)` + `MockMvc`. 토큰은 `JwtTokenProvider.issueAccess(new MemberPrincipal(id, "uuid-"+id, "관리자", 1000), null, List.of(권한))`. `@BeforeEach`로 author 시드, `@AfterEach`로 `repository.deleteAll()`.
  - **리포지토리(슬라이스):** `@DataJpaTest @AutoConfigureTestDatabase(replace = NONE) @Import({TestcontainersConfiguration.class, JpaConfig.class}) @TestPropertySource(properties = {"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"})`. **주의:** 슬라이스라 Flyway 부분인덱스는 안 생긴다.
  - **서비스(단위):** 순수 Mockito `mock(...)`, 컨테이너 없음.
- **예외 던지기:** `throw new BusinessException(ErrorCode.XXX)` → `GlobalExceptionHandler`가 RFC 7807로 매핑.
- **카드 DTO(본문 제외):** `SermonCardResponse(id,title,preacher,series,scripture,preachedAt,viewCount,tags,author)` · `NoticeCardResponse(id,title,isPinned,viewCount,createdAt,tags,author)` · `EventCardResponse(id,title,location,startAt,endAt,allDay,tags)`.
- **서비스 시그니처:** `SermonService.list(preacher,series,from,to,q,tagId,Pageable)` → `Page<SermonCardResponse>` · `NoticeService.list(q,tagId,Pageable)` → `Page<NoticeCardResponse>` · `EventService.list(DateRange,tagId,Pageable)` → `Page<EventCardResponse>`.
- **Redis 관례:** `StringRedisTemplate` 주입(`RefreshTokenStore` 참고). 키 스캔은 `KEYS` 금지 → `ScanOptions`+`Cursor`. Redis 7은 `GETDEL`(`opsForValue().getAndDelete`) 지원.
- **빌드/테스트:** `./gradlew test`(전체) · `./gradlew test --tests 'FQCN'`(단건). 포맷터 `palantirJavaFormat` 적용 — 커밋 전 `./gradlew spotlessApply`(있으면) 또는 빌드가 포맷 위반을 잡는다.
- **커밋:** `<type> : <설명> #18` (콜론 앞 공백, 한글, type=feat/fix/refactor/docs/test/chore).

---

## 파일 구조 (생성/수정 맵)

**생성:**

| 파일 | 책임 |
|---|---|
| `domain/main/MainController.java` | `GET /api/main` 공개 엔드포인트 |
| `domain/main/MainService.java` | 설교3+공지3+다가오는일정5 조합, `@Cacheable("main")` |
| `domain/main/dto/MainResponse.java` | `{sermons,notices,upcomingEvents}` 레코드 |
| `global/config/CacheConfig.java` | `@EnableCaching` + `RedisCacheManager`(TTL env) |
| `global/config/SchedulingConfig.java` | `@EnableScheduling` |
| `global/viewcount/ViewCountStore.java` | Redis INCR/GETDEL/SCAN 캡슐화 |
| `global/viewcount/ViewCountFlushTarget.java` | SPI: `namespace()`, `applyDeltas(Map)` |
| `global/viewcount/ViewCountFlushScheduler.java` | `@Scheduled` 플러시 |
| `domain/sermon/SermonViewCountFlushTarget.java` | 설교 플러시 구현 |
| `domain/notice/NoticeViewCountFlushTarget.java` | 공지 플러시 구현 |
| 테스트들 | 아래 각 Task에 명시 |

**수정:**

| 파일 | 변경 |
|---|---|
| `docker-compose.yml` | backend env에 `ADMIN_*`·`CACHE_TTL`·`VIEW_FLUSH_INTERVAL` |
| `.env.example` | `CACHE_TTL`·`VIEW_FLUSH_INTERVAL` |
| `src/main/resources/application.yml` | `cache.ttl`·`view.flush-interval` 바인딩 |
| `global/exception/GlobalExceptionHandler.java` | `MethodArgumentTypeMismatchException` 핸들러 |
| `global/exception/ErrorResponse.java` | `@Schema` 문서화 |
| `global/config/OpenApiConfig.java` | `OperationCustomizer`(공통 에러응답·공개 GET 자물쇠 제거) |
| `ChurchBackendApplication.java` | (대안) `@EnableScheduling`·`@EnableCaching` 위치 |
| `domain/event/EventRepository.java` | `findUpcoming` |
| `domain/event/EventService.java` | `upcoming(int)` |
| `domain/sermon/SermonRepository.java` | `incrementViewCount`→`incrementViewCountBy` |
| `domain/notice/NoticeRepository.java` | 동일 |
| `domain/sermon/SermonService.java` | `get()` Redis 버퍼 + `@CacheEvict` |
| `domain/notice/NoticeService.java` | 동일 |
| `domain/sermon/SermonController.java`·`AdminSermonController.java` + 22개 컨트롤러 | `@Tag`/`@Operation` |
| 기존 테스트 | `SermonServiceTest`·`SermonRepositoryTest`·`NoticeRepositoryTest`·`NoticeServiceTest`(있으면) view_count 흐름 반영 |

---

# Phase 0 — 빠른 갭 보완 (compose, 예외)

## Task 0.1: docker-compose에 SUPER_ADMIN/신규 env 주입

**Files:**
- Modify: `docker-compose.yml:35-49` (backend `environment:` 블록)

- [ ] **Step 1: backend environment 블록에 6줄 추가**

`docker-compose.yml`의 backend `environment:` 마지막 줄(`SWAGGER_ENABLED: ${SWAGGER_ENABLED:-true}`) **다음에** 추가:

```yaml
      SWAGGER_ENABLED: ${SWAGGER_ENABLED:-true}
      ADMIN_PHONE: ${ADMIN_PHONE:-}
      ADMIN_NAME: ${ADMIN_NAME:-}
      ADMIN_PASSWORD: ${ADMIN_PASSWORD:-}
      CACHE_TTL: ${CACHE_TTL:-60}
      VIEW_FLUSH_INTERVAL: ${VIEW_FLUSH_INTERVAL:-60000}
```

- [ ] **Step 2: compose 파일 유효성 검증**

Run: `docker compose config >/dev/null && echo OK`
Expected: `OK` (구문 오류 없음). Docker 미설치 환경이면 이 검증은 건너뛰고 Step 3로.

- [ ] **Step 3: 커밋**

```bash
git add docker-compose.yml
git commit -m "fix : docker-compose backend에 SUPER_ADMIN·캐시·플러시 env 주입 #18"
```

## Task 0.2: 타입 변환 예외 → 400 매핑

**Files:**
- Modify: `global/exception/GlobalExceptionHandler.java`
- Modify: `src/test/java/com/elipair/church/global/exception/ExceptionTestController.java`
- Test: `src/test/java/com/elipair/church/global/exception/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: 실패하는 테스트 추가**

`ExceptionTestController.java`에 타입 불일치를 유발하는 엔드포인트 추가(클래스 안, 기존 메서드들 옆):

```java
    @GetMapping("/test/type-mismatch")
    void typeMismatch(@org.springframework.web.bind.annotation.RequestParam int value) {
        // value 변환 실패 시 MethodArgumentTypeMismatchException 발생
    }
```

`GlobalExceptionHandlerTest.java`에 테스트 추가:

```java
    @Test
    void type_mismatch_param_maps_to_invalid_input_value() throws Exception {
        mockMvc.perform(get("/test/type-mismatch").param("value", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.exception.GlobalExceptionHandlerTest'`
Expected: FAIL — `?value=abc`가 500(`INTERNAL_ERROR`)로 떨어져 400 단언 실패.

- [ ] **Step 3: 핸들러 구현**

`GlobalExceptionHandler.java` import 추가:

```java
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
```

`handleValidation` 메서드 **위에** 추가:

```java
    /** @RequestParam/@PathVariable 타입 변환 실패(예: ?year=abc, ?version=abc) — 400 INVALID_INPUT_VALUE. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ErrorResponse.of(
                        ErrorCode.INVALID_INPUT_VALUE,
                        "요청 파라미터 '%s'의 형식이 올바르지 않습니다".formatted(e.getName()),
                        request.getRequestURI()));
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.exception.GlobalExceptionHandlerTest'`
Expected: PASS (6 → 7 테스트).

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/elipair/church/global/exception/GlobalExceptionHandler.java src/test/java/com/elipair/church/global/exception/ExceptionTestController.java src/test/java/com/elipair/church/global/exception/GlobalExceptionHandlerTest.java
git commit -m "fix : 파라미터 타입 변환 실패를 400 INVALID_INPUT_VALUE로 매핑 #18"
```

---

# Phase 1 — 다가오는 일정 쿼리

## Task 1.1: `EventRepository.findUpcoming` + `EventService.upcoming`

**Files:**
- Modify: `domain/event/EventRepository.java`
- Modify: `domain/event/EventService.java`
- Test: `src/test/java/com/elipair/church/domain/event/EventUpcomingTest.java` (생성)

- [ ] **Step 1: 실패하는 통합 테스트 작성**

Create `src/test/java/com/elipair/church/domain/event/EventUpcomingTest.java`:

```java
package com.elipair.church.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EventUpcomingTest {

    @Autowired
    private EventService service;

    @Autowired
    private EventRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    private void save(String title, LocalDateTime start) {
        repository.saveAndFlush(Event.create(title, "본문", "본당", start, start.plusHours(1), false));
    }

    @Test
    void upcoming_excludes_past_orders_ascending_and_limits() {
        LocalDateTime now = LocalDateTime.now();
        save("과거", now.minusDays(1));
        save("내일", now.plusDays(1));
        save("모레", now.plusDays(2));
        save("다음주", now.plusDays(7));

        List<?> result = service.upcoming(2);

        assertThat(result).hasSize(2);
        assertThat(result).extracting("title").containsExactly("내일", "모레");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.event.EventUpcomingTest'`
Expected: FAIL — `EventService.upcoming` 메서드 없음(컴파일 에러).

- [ ] **Step 3: 리포지토리 쿼리 추가**

`EventRepository.java`에 import 추가:

```java
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
```

`findByIdAndDeletedAtIsNull` 아래에 추가:

```java
    /** 다가오는 일정(start_at >= now, 미삭제, start_at ASC). idx_events_start_at 부분인덱스 활용. */
    @Query("select e from Event e where e.startAt >= :now and e.deletedAt is null order by e.startAt asc")
    List<Event> findUpcoming(@Param("now") LocalDateTime now, Pageable pageable);
```

- [ ] **Step 4: 서비스 메서드 추가**

`EventService.java`에 import 추가:

```java
import com.elipair.church.domain.event.dto.EventCardResponse;
import org.springframework.data.domain.PageRequest;
```

`list(...)` 메서드 아래에 추가:

```java
    /** 메인페이지용 다가오는 일정 카드 N건(스펙 §5.9). start_at >= now, start_at ASC. */
    public List<EventCardResponse> upcoming(int limit) {
        List<Event> events = repository.findUpcoming(LocalDateTime.now(), PageRequest.of(0, limit));
        List<Long> ids = events.stream().map(Event::getId).toList();
        Map<Long, List<TagResponse>> tagsMap = contentTagService.getTagsByResources(TYPE, ids);
        return events.stream()
                .map(e -> new EventCardResponse(
                        e.getId(),
                        e.getTitle(),
                        e.getLocation(),
                        e.getStartAt(),
                        e.getEndAt(),
                        e.isAllDay(),
                        tagsMap.getOrDefault(e.getId(), List.of())))
                .toList();
    }
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.event.EventUpcomingTest'`
Expected: PASS.

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/elipair/church/domain/event/EventRepository.java src/main/java/com/elipair/church/domain/event/EventService.java src/test/java/com/elipair/church/domain/event/EventUpcomingTest.java
git commit -m "feat : 다가오는 일정 조회(EventService.upcoming) 추가 #18"
```

---

# Phase 2 — `GET /api/main` 통합 API

## Task 2.1: `MainResponse` DTO

**Files:**
- Create: `domain/main/dto/MainResponse.java`

- [ ] **Step 1: 레코드 생성**

Create `src/main/java/com/elipair/church/domain/main/dto/MainResponse.java`:

```java
package com.elipair.church.domain.main.dto;

import com.elipair.church.domain.event.dto.EventCardResponse;
import com.elipair.church.domain.notice.dto.NoticeCardResponse;
import com.elipair.church.domain.sermon.dto.SermonCardResponse;
import java.io.Serializable;
import java.util.List;

/** 메인페이지 통합 응답(스펙 §5.9). 카드 메타만(본문 제외). Redis 캐시 직렬화 대상이라 Serializable. */
public record MainResponse(
        List<SermonCardResponse> sermons,
        List<NoticeCardResponse> notices,
        List<EventCardResponse> upcomingEvents)
        implements Serializable {}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/elipair/church/domain/main/dto/MainResponse.java
git commit -m "feat : 메인 통합 응답 DTO(MainResponse) 추가 #18"
```

## Task 2.2: `MainService`

**Files:**
- Create: `domain/main/MainService.java`
- Test: `src/test/java/com/elipair/church/domain/main/MainServiceTest.java` (생성)

- [ ] **Step 1: 실패하는 단위 테스트 작성**

Create `src/test/java/com/elipair/church/domain/main/MainServiceTest.java`:

```java
package com.elipair.church.domain.main;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.event.EventService;
import com.elipair.church.domain.event.dto.EventCardResponse;
import com.elipair.church.domain.main.dto.MainResponse;
import com.elipair.church.domain.notice.NoticeService;
import com.elipair.church.domain.notice.dto.NoticeCardResponse;
import com.elipair.church.domain.sermon.SermonService;
import com.elipair.church.domain.sermon.dto.SermonCardResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class MainServiceTest {

    @Test
    void getMain_collects_sermons_notices_and_upcoming_events() {
        SermonService sermonService = mock(SermonService.class);
        NoticeService noticeService = mock(NoticeService.class);
        EventService eventService = mock(EventService.class);

        SermonCardResponse sermon =
                new SermonCardResponse(1L, "설교", "김목사", "s", "마5", LocalDate.now(), 0L, List.of(), "관리자");
        NoticeCardResponse notice = new NoticeCardResponse(2L, "공지", false, 0L, LocalDateTime.now(), List.of(), "관리자");
        EventCardResponse event =
                new EventCardResponse(3L, "행사", "본당", LocalDateTime.now(), LocalDateTime.now(), false, List.of());

        when(sermonService.list(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sermon)));
        when(noticeService.list(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(notice)));
        when(eventService.upcoming(5)).thenReturn(List.of(event));

        MainService service = new MainService(sermonService, noticeService, eventService);
        MainResponse result = service.getMain();

        assertThat(result.sermons()).containsExactly(sermon);
        assertThat(result.notices()).containsExactly(notice);
        assertThat(result.upcomingEvents()).containsExactly(event);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.main.MainServiceTest'`
Expected: FAIL — `MainService` 클래스 없음.

- [ ] **Step 3: `MainService` 구현**

Create `src/main/java/com/elipair/church/domain/main/MainService.java`:

```java
package com.elipair.church.domain.main;

import com.elipair.church.domain.event.EventService;
import com.elipair.church.domain.main.dto.MainResponse;
import com.elipair.church.domain.notice.NoticeService;
import com.elipair.church.domain.sermon.SermonService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 메인페이지 통합 조회(스펙 §5.9). 설교·공지·다가오는 일정 카드를 조합한다.
 * Redis 캐싱(@Cacheable). 설교/공지/일정 CUD 시 각 서비스의 @CacheEvict("main")로 무효화된다.
 */
@Service
public class MainService {

    private static final int SERMON_COUNT = 3;
    private static final int NOTICE_COUNT = 3;
    private static final int EVENT_COUNT = 5;

    private final SermonService sermonService;
    private final NoticeService noticeService;
    private final EventService eventService;

    public MainService(SermonService sermonService, NoticeService noticeService, EventService eventService) {
        this.sermonService = sermonService;
        this.noticeService = noticeService;
        this.eventService = eventService;
    }

    @Cacheable("main")
    public MainResponse getMain() {
        return new MainResponse(
                sermonService
                        .list(null, null, null, null, null, null, PageRequest.of(0, SERMON_COUNT))
                        .getContent(),
                noticeService.list(null, null, PageRequest.of(0, NOTICE_COUNT)).getContent(),
                eventService.upcoming(EVENT_COUNT));
    }
}
```

> **참고:** `@Cacheable("main")`은 Phase 4에서 `CacheConfig`가 켜지기 전까지는 no-op으로 동작한다(캐시 매니저 부재 시 Spring이 무시). 본 Task의 단위 테스트는 캐시와 무관하게 통과한다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.main.MainServiceTest'`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/elipair/church/domain/main/MainService.java src/test/java/com/elipair/church/domain/main/MainServiceTest.java
git commit -m "feat : 메인 통합 서비스(MainService) 추가 #18"
```

## Task 2.3: `MainController` + 통합 테스트

**Files:**
- Create: `domain/main/MainController.java`
- Test: `src/test/java/com/elipair/church/domain/main/MainApiTest.java` (생성)

- [ ] **Step 1: 실패하는 통합 테스트 작성**

Create `src/test/java/com/elipair/church/domain/main/MainApiTest.java`:

```java
package com.elipair.church.domain.main;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.event.Event;
import com.elipair.church.domain.event.EventRepository;
import com.elipair.church.domain.notice.Notice;
import com.elipair.church.domain.notice.NoticeRepository;
import com.elipair.church.domain.sermon.Sermon;
import com.elipair.church.domain.sermon.SermonRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MainApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SermonRepository sermonRepository;

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private EventRepository eventRepository;

    @AfterEach
    void cleanup() {
        sermonRepository.deleteAll();
        noticeRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    void main_is_public_and_returns_three_sections() throws Exception {
        sermonRepository.saveAndFlush(
                Sermon.create("설교1", "김목사", "s", "마5", "본문", null, null, LocalDate.of(2026, 6, 1)));
        noticeRepository.saveAndFlush(Notice.create("공지1", "본문", false));
        eventRepository.saveAndFlush(Event.create(
                "다가오는행사", "본문", "본당", LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1), false));

        mockMvc.perform(get("/api/main"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sermons[0].title").value("설교1"))
                .andExpect(jsonPath("$.sermons[0].content").doesNotExist())
                .andExpect(jsonPath("$.notices[0].title").value("공지1"))
                .andExpect(jsonPath("$.upcomingEvents[0].title").value("다가오는행사"));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.main.MainApiTest'`
Expected: FAIL — `/api/main` 404(컨트롤러 없음).

- [ ] **Step 3: `MainController` 구현**

Create `src/main/java/com/elipair/church/domain/main/MainController.java`:

```java
package com.elipair.church.domain.main;

import com.elipair.church.domain.main.dto.MainResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 메인페이지 통합 조회 API(스펙 §5.9). 공개 — SecurityConfig anyRequest permitAll. */
@RestController
public class MainController {

    private final MainService service;

    public MainController(MainService service) {
        this.service = service;
    }

    @GetMapping("/api/main")
    public MainResponse main() {
        return service.getMain();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.main.MainApiTest'`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/elipair/church/domain/main/MainController.java src/test/java/com/elipair/church/domain/main/MainApiTest.java
git commit -m "feat : 메인 통합 조회 API(GET /api/main) 추가 #18"
```

---

# Phase 3 — view_count Redis 버퍼 + 주기 플러시

> **설계:** 상세조회마다 동기 `UPDATE` → Redis `INCR` 버퍼. `@Scheduled` 잡이 `GETDEL`로 누적분을 빼 DB에 일괄 반영. 라이브 조회수 = DB값 + 방금 INCR 반환값.

## Task 3.1: `ViewCountStore` (Redis 버퍼)

**Files:**
- Create: `global/viewcount/ViewCountStore.java`
- Test: `src/test/java/com/elipair/church/global/viewcount/ViewCountStoreTest.java` (생성)

- [ ] **Step 1: 실패하는 통합 테스트 작성**

Create `src/test/java/com/elipair/church/global/viewcount/ViewCountStoreTest.java`:

```java
package com.elipair.church.global.viewcount;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ViewCountStoreTest {

    @Autowired
    private ViewCountStore store;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanup() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void increment_returns_running_buffer_value() {
        assertThat(store.increment("sermon", 7L)).isEqualTo(1L);
        assertThat(store.increment("sermon", 7L)).isEqualTo(2L);
    }

    @Test
    void drain_returns_deltas_and_clears_keys() {
        store.increment("sermon", 7L);
        store.increment("sermon", 7L);
        store.increment("sermon", 9L);

        Map<Long, Long> deltas = store.drain("sermon");

        assertThat(deltas).containsEntry(7L, 2L).containsEntry(9L, 1L);
        // 비운 뒤 재증가는 1부터
        assertThat(store.increment("sermon", 7L)).isEqualTo(1L);
        assertThat(store.drain("sermon")).containsExactly(Map.entry(7L, 1L));
    }

    @Test
    void drain_isolates_namespaces() {
        store.increment("sermon", 1L);
        store.increment("notice", 1L);

        assertThat(store.drain("sermon")).containsExactly(Map.entry(1L, 1L));
        assertThat(store.drain("notice")).containsExactly(Map.entry(1L, 1L));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.viewcount.ViewCountStoreTest'`
Expected: FAIL — `ViewCountStore` 없음.

- [ ] **Step 3: `ViewCountStore` 구현**

Create `src/main/java/com/elipair/church/global/viewcount/ViewCountStore.java`:

```java
package com.elipair.church.global.viewcount;

import java.util.HashMap;
import java.util.Map;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 조회수 버퍼(스펙 §9). 키 = view:{namespace}:{id}, 값 = 미플러시 누적 조회수.
 * 상세조회는 increment(원자 INCR)로 카운팅하고, 주기 잡이 drain(SCAN+GETDEL)으로 빼 DB에 반영한다.
 * KEYS 금지(Redis keyspace 공유) → SCAN 사용. GETDEL(Redis 6.2+)로 get+삭제를 원자화해 유실/중복을 막는다.
 */
@Component
public class ViewCountStore {

    static final String PREFIX = "view:";

    private final StringRedisTemplate redis;

    public ViewCountStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String key(String namespace, long id) {
        return PREFIX + namespace + ":" + id;
    }

    /** 조회수 +1. 증가 후의 미플러시 누적값을 반환(라이브 표시용). */
    public long increment(String namespace, long id) {
        Long value = redis.opsForValue().increment(key(namespace, id));
        return value == null ? 0L : value;
    }

    /** 해당 namespace의 모든 버퍼를 원자적으로 비우고(id → delta) 반환. */
    public Map<Long, Long> drain(String namespace) {
        String prefix = PREFIX + namespace + ":";
        ScanOptions options =
                ScanOptions.scanOptions().match(prefix + "*").count(100).build();
        Map<Long, Long> deltas = new HashMap<>();
        try (Cursor<String> cursor = redis.scan(options)) {
            cursor.forEachRemaining(key -> {
                String value = redis.opsForValue().getAndDelete(key); // GETDEL: 원자 get+삭제
                if (value != null) {
                    long id = Long.parseLong(key.substring(prefix.length()));
                    deltas.merge(id, Long.parseLong(value), Long::sum);
                }
            });
        }
        return deltas;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.viewcount.ViewCountStoreTest'`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/elipair/church/global/viewcount/ViewCountStore.java src/test/java/com/elipair/church/global/viewcount/ViewCountStoreTest.java
git commit -m "feat : 조회수 Redis 버퍼 저장소(ViewCountStore) 추가 #18"
```

## Task 3.2: `ViewCountFlushTarget` SPI + 스케줄러 + `@EnableScheduling`

**Files:**
- Create: `global/viewcount/ViewCountFlushTarget.java`
- Create: `global/viewcount/ViewCountFlushScheduler.java`
- Create: `global/config/SchedulingConfig.java`

- [ ] **Step 1: SPI 인터페이스 생성**

Create `src/main/java/com/elipair/church/global/viewcount/ViewCountFlushTarget.java`:

```java
package com.elipair.church.global.viewcount;

import java.util.Map;

/**
 * 조회수 플러시 대상(SPI). 각 도메인(설교·공지)이 자기 namespace와 DB 반영 방법을 제공한다.
 * global → domain 역참조를 피하기 위한 인터페이스(스펙 §7 도메인→global 단방향).
 */
public interface ViewCountFlushTarget {

    /** Redis 버퍼 키의 namespace(예: "sermon", "notice"). */
    String namespace();

    /** id → 누적 delta를 DB view_count에 +반영한다. */
    void applyDeltas(Map<Long, Long> deltas);
}
```

- [ ] **Step 2: 스케줄러 생성**

Create `src/main/java/com/elipair/church/global/viewcount/ViewCountFlushScheduler.java`:

```java
package com.elipair.church.global.viewcount;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 조회수 버퍼를 주기적으로 DB에 반영한다(스펙 §9). VIEW_FLUSH_INTERVAL(ms, 기본 60000)마다 실행.
 * 등록된 모든 ViewCountFlushTarget에 대해 store.drain → target.applyDeltas. 크래시 시 최대 1주기 유실(허용).
 */
@Slf4j
@Component
public class ViewCountFlushScheduler {

    private final ViewCountStore store;
    private final List<ViewCountFlushTarget> targets;

    public ViewCountFlushScheduler(ViewCountStore store, List<ViewCountFlushTarget> targets) {
        this.store = store;
        this.targets = targets;
    }

    @Scheduled(fixedDelayString = "${view.flush-interval:60000}")
    public void flush() {
        for (ViewCountFlushTarget target : targets) {
            Map<Long, Long> deltas = store.drain(target.namespace());
            if (!deltas.isEmpty()) {
                target.applyDeltas(deltas);
                log.debug("조회수 플러시: namespace={}, rows={}", target.namespace(), deltas.size());
            }
        }
    }
}
```

- [ ] **Step 3: 스케줄링 활성화**

Create `src/main/java/com/elipair/church/global/config/SchedulingConfig.java`:

```java
package com.elipair.church.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @Scheduled 활성화(스펙 §9 조회수 주기 플러시). */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
```

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. (실 검증은 Task 3.5 통합 테스트에서.)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/elipair/church/global/viewcount/ViewCountFlushTarget.java src/main/java/com/elipair/church/global/viewcount/ViewCountFlushScheduler.java src/main/java/com/elipair/church/global/config/SchedulingConfig.java
git commit -m "feat : 조회수 플러시 SPI·스케줄러·@EnableScheduling 추가 #18"
```

## Task 3.3: 설교 — `incrementViewCountBy` + `get()` 버퍼 전환 + FlushTarget

**Files:**
- Modify: `domain/sermon/SermonRepository.java`
- Modify: `domain/sermon/SermonService.java`
- Create: `domain/sermon/SermonViewCountFlushTarget.java`
- Modify: `src/test/java/com/elipair/church/domain/sermon/SermonRepositoryTest.java`
- Modify: `src/test/java/com/elipair/church/domain/sermon/SermonServiceTest.java`

- [ ] **Step 1: 리포지토리 테스트를 `incrementViewCountBy`로 수정(실패 유도)**

`SermonRepositoryTest.java`의 `incrementViewCount_is_atomic_and_skips_deleted`·`incrementViewCount_returns_zero_for_deleted` 두 메서드를 아래로 **교체**:

```java
    @Test
    void incrementViewCountBy_adds_delta_and_skips_deleted() {
        Sermon s = repository.saveAndFlush(sermon("조회수"));

        int updated = repository.incrementViewCountBy(s.getId(), 5L);

        assertThat(updated).isEqualTo(1);
        assertThat(repository
                        .findByIdAndDeletedAtIsNull(s.getId())
                        .orElseThrow()
                        .getViewCount())
                .isEqualTo(5L);
    }

    @Test
    void incrementViewCountBy_returns_zero_for_deleted() {
        Sermon deleted = sermon("삭제됨");
        deleted.softDelete();
        Sermon saved = repository.saveAndFlush(deleted);

        assertThat(repository.incrementViewCountBy(saved.getId(), 3L)).isZero();
    }
```

- [ ] **Step 2: 서비스 단위 테스트의 `get` 케이스 수정(실패 유도)**

`SermonServiceTest.java` 수정:

(a) 필드·`init()`에 `ViewCountStore` 목 추가. 상단 import 추가:

```java
import com.elipair.church.global.viewcount.ViewCountStore;
```

필드 추가(`private SermonService service;` 위):

```java
    private ViewCountStore viewCountStore;
```

`init()`의 `service = new SermonService(...)` 줄을 교체:

```java
        viewCountStore = mock(ViewCountStore.class);
        service = new SermonService(repository, contentTagService, authorDisplayService, viewCountStore);
```

(b) 기존 `get_unknown_throws_404`·`get_increments_view_count_before_loading` 두 메서드를 아래로 **교체**:

```java
    @Test
    void get_unknown_throws_404_without_counting() {
        when(repository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        verify(viewCountStore, never()).increment(any(), anyLong());
    }

    @Test
    void get_buffers_view_and_returns_live_count() {
        Sermon s = mockSermonWithVersion(0L);
        when(s.getViewCount()).thenReturn(5L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(s));
        when(viewCountStore.increment("sermon", 10L)).thenReturn(3L);

        assertThat(service.get(10L).viewCount()).isEqualTo(8L); // DB 5 + 버퍼 3
    }
```

import 추가(상단):

```java
import static org.mockito.ArgumentMatchers.anyLong;
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.sermon.SermonRepositoryTest' --tests 'com.elipair.church.domain.sermon.SermonServiceTest'`
Expected: FAIL — `incrementViewCountBy`·새 생성자·버퍼 흐름 미구현(컴파일/단언 실패).

- [ ] **Step 4: 리포지토리 메서드 교체**

`SermonRepository.java`의 `incrementViewCount` 메서드를 아래로 교체:

```java
    /** 플러시 잡이 누적 조회수를 +delta 반영. 벌크 UPDATE라 @Version·감사필드 미변경. clear로 L1 stale 방지. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Sermon s set s.viewCount = s.viewCount + :delta where s.id = :id and s.deletedAt is null")
    int incrementViewCountBy(@Param("id") Long id, @Param("delta") long delta);
```

- [ ] **Step 5: 서비스 `get()` 버퍼 전환 + `detail` 오버로드**

`SermonService.java` import 추가:

```java
import com.elipair.church.global.viewcount.ViewCountStore;
```

생성자·필드 수정 — `viewCountStore` 추가:

```java
    private final SermonRepository repository;
    private final ContentTagService contentTagService;
    private final AuthorDisplayService authorDisplayService;
    private final ViewCountStore viewCountStore;

    public SermonService(
            SermonRepository repository,
            ContentTagService contentTagService,
            AuthorDisplayService authorDisplayService,
            ViewCountStore viewCountStore) {
        this.repository = repository;
        this.contentTagService = contentTagService;
        this.authorDisplayService = authorDisplayService;
        this.viewCountStore = viewCountStore;
    }
```

`get(...)` 메서드 교체(DB write 없음 → `readOnly`):

```java
    @Transactional(readOnly = true)
    public SermonDetailResponse get(Long id) {
        Sermon sermon = repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        long buffered = viewCountStore.increment(SermonViewCountFlushTarget.NAMESPACE, id);
        return detail(sermon, sermon.getViewCount() + buffered);
    }
```

`detail(Sermon s)`를 viewCount 파라미터 받는 형태로 교체:

```java
    private SermonDetailResponse detail(Sermon s) {
        return detail(s, s.getViewCount());
    }

    private SermonDetailResponse detail(Sermon s, long viewCount) {
        return new SermonDetailResponse(
                s.getId(),
                s.getTitle(),
                s.getPreacher(),
                s.getSeries(),
                s.getScripture(),
                s.getContent(),
                s.getVideoUrl(),
                s.getAudioUrl(),
                s.getPreachedAt(),
                viewCount,
                s.getCreatedAt(),
                s.getUpdatedAt(),
                s.getVersion(),
                contentTagService.getTags(TYPE, s.getId()),
                authorDisplayService.displayName(s.getUpdatedBy()));
    }
```

> create/update/patch는 기존 `detail(sermon)` 호출 그대로 둔다(오버로드가 `s.getViewCount()` 사용).

- [ ] **Step 6: 설교 FlushTarget 생성**

Create `src/main/java/com/elipair/church/domain/sermon/SermonViewCountFlushTarget.java`:

```java
package com.elipair.church.domain.sermon;

import com.elipair.church.global.viewcount.ViewCountFlushTarget;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 설교 조회수 플러시 대상(스펙 §9). Redis 버퍼 누적분을 sermons.view_count에 +반영. */
@Component
public class SermonViewCountFlushTarget implements ViewCountFlushTarget {

    public static final String NAMESPACE = "sermon";

    private final SermonRepository repository;

    public SermonViewCountFlushTarget(SermonRepository repository) {
        this.repository = repository;
    }

    @Override
    public String namespace() {
        return NAMESPACE;
    }

    @Override
    @Transactional
    public void applyDeltas(Map<Long, Long> deltas) {
        deltas.forEach(repository::incrementViewCountBy);
    }
}
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.sermon.*'`
Expected: PASS (Repository·Service·Api 전부).

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/elipair/church/domain/sermon/ src/test/java/com/elipair/church/domain/sermon/
git commit -m "feat : 설교 조회수를 Redis 버퍼+플러시로 전환 #18"
```

## Task 3.4: 공지 — Task 3.3과 동일 패턴 적용

**Files:**
- Modify: `domain/notice/NoticeRepository.java`
- Modify: `domain/notice/NoticeService.java`
- Create: `domain/notice/NoticeViewCountFlushTarget.java`
- Modify: `src/test/java/com/elipair/church/domain/notice/NoticeRepositoryTest.java`
- Modify: `src/test/java/com/elipair/church/domain/notice/NoticeServiceTest.java` (있으면; 없으면 생략)

- [ ] **Step 1: 리포지토리 테스트 교체(실패 유도)**

`NoticeRepositoryTest.java`의 `incrementViewCount_is_atomic_and_skips_deleted`·`incrementViewCount_returns_zero_for_deleted`를 교체:

```java
    @Test
    void incrementViewCountBy_adds_delta_and_skips_deleted() {
        Notice n = repository.saveAndFlush(notice("조회수"));

        int updated = repository.incrementViewCountBy(n.getId(), 5L);

        assertThat(updated).isEqualTo(1);
        assertThat(repository
                        .findByIdAndDeletedAtIsNull(n.getId())
                        .orElseThrow()
                        .getViewCount())
                .isEqualTo(5L);
    }

    @Test
    void incrementViewCountBy_returns_zero_for_deleted() {
        Notice deleted = notice("삭제됨");
        deleted.softDelete();
        Notice saved = repository.saveAndFlush(deleted);

        assertThat(repository.incrementViewCountBy(saved.getId(), 3L)).isZero();
    }
```

- [ ] **Step 2: 리포지토리 메서드 교체**

`NoticeRepository.java`의 `incrementViewCount`를 교체(Sermon과 동일 형태):

```java
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notice n set n.viewCount = n.viewCount + :delta where n.id = :id and n.deletedAt is null")
    int incrementViewCountBy(@Param("id") Long id, @Param("delta") long delta);
```

> `NoticeRepository.java`에 `@Modifying`·`@Param` import가 없으면 추가:
> ```java
> import org.springframework.data.jpa.repository.Modifying;
> import org.springframework.data.repository.query.Param;
> ```

- [ ] **Step 3: 서비스 `get()` 버퍼 전환**

`NoticeService.java` import 추가:

```java
import com.elipair.church.global.viewcount.ViewCountStore;
```

생성자에 `ViewCountStore viewCountStore` 추가(필드+대입, Sermon Task 3.3 Step 5와 동일 패턴).

`get(...)` 교체:

```java
    @Transactional(readOnly = true)
    public NoticeDetailResponse get(Long id) {
        Notice notice = repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        long buffered = viewCountStore.increment(NoticeViewCountFlushTarget.NAMESPACE, id);
        return detail(notice, notice.getViewCount() + buffered);
    }
```

`detail(Notice n)`를 오버로드로 교체:

```java
    private NoticeDetailResponse detail(Notice n) {
        return detail(n, n.getViewCount());
    }

    private NoticeDetailResponse detail(Notice n, long viewCount) {
        return new NoticeDetailResponse(
                n.getId(),
                n.getTitle(),
                n.getContent(),
                n.isPinned(),
                viewCount,
                n.getCreatedAt(),
                n.getUpdatedAt(),
                n.getVersion(),
                contentTagService.getTags(TYPE, n.getId()),
                authorDisplayService.displayName(n.getUpdatedBy()));
    }
```

- [ ] **Step 4: 공지 FlushTarget 생성**

Create `src/main/java/com/elipair/church/domain/notice/NoticeViewCountFlushTarget.java`:

```java
package com.elipair.church.domain.notice;

import com.elipair.church.global.viewcount.ViewCountFlushTarget;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 공지 조회수 플러시 대상(스펙 §9). Redis 버퍼 누적분을 notices.view_count에 +반영. */
@Component
public class NoticeViewCountFlushTarget implements ViewCountFlushTarget {

    public static final String NAMESPACE = "notice";

    private final NoticeRepository repository;

    public NoticeViewCountFlushTarget(NoticeRepository repository) {
        this.repository = repository;
    }

    @Override
    public String namespace() {
        return NAMESPACE;
    }

    @Override
    @Transactional
    public void applyDeltas(Map<Long, Long> deltas) {
        deltas.forEach(repository::incrementViewCountBy);
    }
}
```

- [ ] **Step 5: 공지 단위 테스트 보정(존재 시)**

`NoticeServiceTest.java`가 있으면 Task 3.3 Step 2와 동일하게 `ViewCountStore` 목을 생성자에 추가하고 `get` 관련 케이스를 버퍼 흐름으로 수정한다. `NoticeApiTest`의 상세조회 조회수 단언이 있으면 "조회 후 viewCount ≥ 1"이 여전히 성립한다(버퍼 즉시 반영).

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.notice.*'`
Expected: PASS.

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/elipair/church/domain/notice/ src/test/java/com/elipair/church/domain/notice/
git commit -m "feat : 공지 조회수를 Redis 버퍼+플러시로 전환 #18"
```

## Task 3.5: 플러시 종단 통합 테스트

**Files:**
- Test: `src/test/java/com/elipair/church/global/viewcount/ViewCountFlushIntegrationTest.java` (생성)

- [ ] **Step 1: 통합 테스트 작성**

Create `src/test/java/com/elipair/church/global/viewcount/ViewCountFlushIntegrationTest.java`:

```java
package com.elipair.church.global.viewcount;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.sermon.Sermon;
import com.elipair.church.domain.sermon.SermonRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ViewCountFlushIntegrationTest {

    @Autowired
    private ViewCountStore store;

    @Autowired
    private ViewCountFlushScheduler scheduler;

    @Autowired
    private SermonRepository sermonRepository;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanup() {
        sermonRepository.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void flush_applies_buffered_views_to_db_and_clears_buffer() {
        Sermon s = sermonRepository.saveAndFlush(
                Sermon.create("설교", "김목사", "s", "마5", "본문", null, null, LocalDate.of(2026, 6, 1)));
        store.increment("sermon", s.getId());
        store.increment("sermon", s.getId());
        store.increment("sermon", s.getId());

        scheduler.flush();

        assertThat(sermonRepository
                        .findByIdAndDeletedAtIsNull(s.getId())
                        .orElseThrow()
                        .getViewCount())
                .isEqualTo(3L);
        // 버퍼가 비워져 재플러시는 변화 없음
        scheduler.flush();
        assertThat(sermonRepository
                        .findByIdAndDeletedAtIsNull(s.getId())
                        .orElseThrow()
                        .getViewCount())
                .isEqualTo(3L);
    }
}
```

- [ ] **Step 2: 테스트 실행 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.viewcount.ViewCountFlushIntegrationTest'`
Expected: PASS.

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/com/elipair/church/global/viewcount/ViewCountFlushIntegrationTest.java
git commit -m "test : 조회수 플러시 종단 통합 테스트 추가 #18"
```

---

# Phase 4 — Redis 캐싱

> **⚠️ Context7 확인:** 구현 전 Spring Boot 4.0.6 기준으로 (1) `RedisCacheManager` 사용에 `spring-boot-starter-cache` 추가 필요 여부, (2) SB4=**Jackson 3** 환경의 캐시 값 JSON 직렬화기 정확한 좌표(`GenericJackson2JsonRedisSerializer` 대체 여부), (3) `PageImpl` JSON 역직렬화 가능 여부를 확인하라. 아래 코드는 표준 SB3 형태이며, Task 4.4 통합 테스트가 직렬화 실패를 잡는다.

## Task 4.1: `CacheConfig` (`@EnableCaching` + `RedisCacheManager`)

**Files:**
- Modify: `build.gradle` (필요 시 starter-cache)
- Create: `global/config/CacheConfig.java`
- Modify: `src/main/resources/application.yml`
- Modify: `.env.example`

- [ ] **Step 1: (필요 시) 의존성 추가**

Context7 확인 결과 starter-cache가 필요하면 `build.gradle` dependencies에 추가:

```gradle
    implementation 'org.springframework.boot:spring-boot-starter-cache'
```

- [ ] **Step 2: `CacheConfig` 생성**

Create `src/main/java/com/elipair/church/global/config/CacheConfig.java`:

```java
package com.elipair.church.global.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Redis 캐시 추상화(스펙 §9). /api/main·설교 목록 첫 페이지를 @Cacheable, 콘텐츠 CUD 시 @CacheEvict.
 * TTL은 CACHE_TTL(초, 기본 60)로 주입 — 교회별 조정값(멀티-교회 템플릿: 하드코딩 금지).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private final long ttlSeconds;

    public CacheConfig(@Value("${cache.ttl:60}") long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer()));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
```

- [ ] **Step 3: application.yml에 TTL 바인딩 추가**

`application.yml`의 `cors:` 블록 위(또는 커스텀 설정 영역)에 추가:

```yaml
cache:
  ttl: ${CACHE_TTL:60}            # 캐시 TTL(초). /api/main·설교 목록 첫 페이지
view:
  flush-interval: ${VIEW_FLUSH_INTERVAL:60000}   # 조회수 버퍼 플러시 주기(ms)
```

- [ ] **Step 4: .env.example에 신규 env 추가**

`.env.example`의 `SWAGGER_ENABLED=false` 줄 아래(또는 ADMIN 블록 위)에 추가:

```bash
# 캐시/조회수
CACHE_TTL=60                 # 캐시 TTL(초). /api/main·설교 목록 첫 페이지
VIEW_FLUSH_INTERVAL=60000    # 조회수 버퍼 → DB 반영 주기(밀리초)
```

- [ ] **Step 5: 컨텍스트 로드 확인**

Run: `./gradlew test --tests 'com.elipair.church.ChurchBackendApplicationTests'`
Expected: PASS (캐시 매니저 빈 추가 후 컨텍스트 정상 기동).

- [ ] **Step 6: 커밋**

```bash
git add build.gradle src/main/java/com/elipair/church/global/config/CacheConfig.java src/main/resources/application.yml .env.example
git commit -m "feat : Redis 캐시 매니저(CacheConfig·@EnableCaching) 추가 #18"
```

## Task 4.2: 캐시 적용 + 무효화 (`@Cacheable`/`@CacheEvict`)

**Files:**
- Modify: `domain/sermon/SermonService.java` (`list` @Cacheable, CUD @CacheEvict)
- Modify: `domain/notice/NoticeService.java` (CUD @CacheEvict)
- Modify: `domain/event/EventService.java` (CUD @CacheEvict)
- (MainService.getMain은 Task 2.2에서 이미 `@Cacheable("main")`)

- [ ] **Step 1: 설교 목록 첫 페이지 캐시 + CUD 무효화**

`SermonService.java` import 추가:

```java
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
```

`list(...)`에 `@Cacheable` 부여(필터 전부 null + 0페이지일 때만, 키는 size+sort):

```java
    @Cacheable(
            value = "sermonListFirstPage",
            key = "#pageable.pageSize + ':' + #pageable.sort.toString()",
            condition =
                    "#preacher == null and #series == null and #from == null and #to == null and #q == null and #tagId == null and #pageable.pageNumber == 0")
    public Page<SermonCardResponse> list(
            String preacher, String series, LocalDate from, LocalDate to, String q, Long tagId, Pageable pageable) {
```

`create`·`update`·`patch`·`delete` 각 메서드의 `@Transactional` 위에 추가:

```java
    @CacheEvict(
            value = {"main", "sermonListFirstPage"},
            allEntries = true)
```

- [ ] **Step 2: 공지·일정 CUD 무효화**

`NoticeService.java`·`EventService.java`에 import 추가:

```java
import org.springframework.cache.annotation.CacheEvict;
```

각각 `create`·`update`·`patch`·`delete` 의 `@Transactional` 위에 추가:

```java
    @CacheEvict(value = "main", allEntries = true)
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. (동작 검증은 Task 4.4.)

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/elipair/church/domain/sermon/SermonService.java src/main/java/com/elipair/church/domain/notice/NoticeService.java src/main/java/com/elipair/church/domain/event/EventService.java
git commit -m "feat : /api/main·설교 목록 캐싱과 CUD 캐시 무효화 추가 #18"
```

## Task 4.3: 캐싱 동작 통합 테스트

**Files:**
- Test: `src/test/java/com/elipair/church/domain/main/MainCacheTest.java` (생성)

- [ ] **Step 1: 캐시 hit/evict 통합 테스트 작성**

`MainService`를 스파이로 감싸 DB 호출 횟수를 직접 세는 대신, **하위 서비스 호출 횟수**를 검증한다. `@MockitoSpyBean`으로 `SermonService`를 감싸 `getMain` 2회 호출 시 `sermonService.list`가 1회만 불리는지(캐시 hit) 확인한다.

Create `src/test/java/com/elipair/church/domain/main/MainCacheTest.java`:

```java
package com.elipair.church.domain.main;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.sermon.SermonService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MainCacheTest {

    @Autowired
    private MainService mainService;

    @MockitoSpyBean
    private SermonService sermonService;

    @Autowired
    private CacheManager cacheManager;

    @AfterEach
    void evict() {
        cacheManager.getCache("main").clear();
        cacheManager.getCache("sermonListFirstPage").clear();
    }

    @Test
    void second_getMain_is_served_from_cache() {
        mainService.getMain();
        mainService.getMain();

        // main 캐시 hit이면 하위 sermonService.list는 1회만 호출된다.
        verify(sermonService, times(1))
                .list(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(Pageable.class));
    }
}
```

> **직렬화 검증:** 이 테스트가 `MainResponse`(및 내부 카드 리스트)의 Redis 직렬화/역직렬화를 실제로 검증한다. 역직렬화 예외가 나면 `CacheConfig`의 값 직렬화기를 조정하라(Context7 확인 항목). `PageImpl` 직렬화 문제로 `sermonListFirstPage`가 실패하면, 해당 캐시는 첫 페이지를 `List`/안정 DTO로 캐싱하도록 조정한다(또는 `sermonListFirstPage` 캐싱을 보류하고 `main`만 유지).

- [ ] **Step 2: 테스트 실행 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.main.MainCacheTest'`
Expected: PASS. 실패 시 위 직렬화 노트에 따라 `CacheConfig`/캐시 대상 조정 후 재실행.

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/com/elipair/church/domain/main/MainCacheTest.java
git commit -m "test : 메인 통합 캐시 hit 통합 테스트 추가 #18"
```

---

# Phase 5 — Swagger 전 도메인 문서화

> **방식(확정):** 공통 RFC 7807 에러응답은 `OperationCustomizer`로 모든 오퍼레이션에 일괄 주입한다. 컨트롤러엔 `@Tag`, 엔드포인트엔 `@Operation`, 핵심 DTO엔 `@Schema`를 수기로 단다. 설교를 레퍼런스로 패턴을 정립한 뒤 나머지 도메인에 동일 적용한다.

## Task 5.1: `ErrorResponse` 스키마 + 공통 에러응답 `OperationCustomizer`

**Files:**
- Modify: `global/exception/ErrorResponse.java`
- Modify: `global/config/OpenApiConfig.java`
- Test: `src/test/java/com/elipair/church/global/config/OpenApiOperationCustomizerTest.java` (생성)

- [ ] **Step 1: 실패하는 스모크 테스트 작성**

`/v3/api-docs` JSON에 공통 에러응답(예: 400)과 `ErrorResponse` 스키마가 노출되는지 검증.

Create `src/test/java/com/elipair/church/global/config/OpenApiOperationCustomizerTest.java`:

```java
package com.elipair.church.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiOperationCustomizerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void api_docs_expose_error_response_schema_and_common_error_on_operations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // ErrorResponse 스키마 노출
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists())
                // 공개 목록 GET에 공통 400 응답 주입
                .andExpect(jsonPath("$.paths['/api/sermons'].get.responses['400']").exists());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.config.OpenApiOperationCustomizerTest'`
Expected: FAIL — 공통 400 응답/스키마 미주입.

- [ ] **Step 3: `ErrorResponse`에 스키마 설명 부여**

`ErrorResponse.java` 레코드 컴포넌트에 `@Schema(description, example)`를 단다(필드명은 기존 그대로 유지). 클래스 위에 `@Schema(name = "ErrorResponse", description = "RFC 7807 공통 에러 응답")`. 예시:

```java
@Schema(name = "ErrorResponse", description = "RFC 7807 공통 에러 응답")
public record ErrorResponse(
        @Schema(description = "클라이언트 분기용 코드", example = "INVALID_INPUT_VALUE") String errorCode,
        @Schema(description = "사용자 표시용 한글 제목", example = "유효하지 않은 입력값") String title,
        @Schema(description = "HTTP 상태", example = "400") int status,
        @Schema(description = "상세 설명", example = "입력값이 유효성 검사를 통과하지 못했습니다") String detail,
        @Schema(description = "오류가 난 요청 경로", example = "/api/auth/login") String instance,
        ... // 기존 errors/references 등 나머지 필드 유지 + @Schema 부여
) { ... }
```

import: `import io.swagger.v3.oas.annotations.media.Schema;` (기존 필드·중첩 record 구조는 그대로 두고 애너테이션만 추가).

- [ ] **Step 4: `OpenApiConfig`에 `OperationCustomizer` 추가**

`OpenApiConfig.java`에 빈 추가:

```java
    @Bean
    public org.springdoc.core.customizers.OperationCustomizer commonErrorResponses() {
        return (operation, handlerMethod) -> {
            io.swagger.v3.oas.models.media.Content content =
                    new io.swagger.v3.oas.models.media.Content()
                            .addMediaType(
                                    "application/json",
                                    new io.swagger.v3.oas.models.media.MediaType()
                                            .schema(new io.swagger.v3.oas.models.media.Schema<>()
                                                    .$ref("#/components/schemas/ErrorResponse")));
            io.swagger.v3.oas.models.responses.ApiResponses responses = operation.getResponses();
            addIfAbsent(responses, "400", "유효하지 않은 입력값", content);
            addIfAbsent(responses, "401", "인증 실패/토큰 무효", content);
            addIfAbsent(responses, "403", "권한 없음", content);
            addIfAbsent(responses, "404", "리소스 없음", content);
            addIfAbsent(responses, "409", "충돌(중복·낙관락·참조)", content);
            return operation;
        };
    }

    private static void addIfAbsent(
            io.swagger.v3.oas.models.responses.ApiResponses responses,
            String code,
            String description,
            io.swagger.v3.oas.models.media.Content content) {
        if (responses.get(code) == null) {
            responses.addApiResponse(
                    code,
                    new io.swagger.v3.oas.models.responses.ApiResponse()
                            .description(description)
                            .content(content));
        }
    }
```

> 공개 GET 자물쇠 제거(LOW)는 선택: 동일 customizer에서 `handlerMethod`의 경로가 `/api/admin`·`/api/gallery`로 시작하지 않으면 `operation.setSecurity(java.util.List.of())`. 필요 시 추가.

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.config.OpenApiOperationCustomizerTest' --tests 'com.elipair.church.global.config.OpenApiConfigTest' --tests 'com.elipair.church.global.config.SwaggerToggleTest'`
Expected: PASS(기존 Swagger 테스트 포함 회귀 없음).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/elipair/church/global/exception/ErrorResponse.java src/main/java/com/elipair/church/global/config/OpenApiConfig.java src/test/java/com/elipair/church/global/config/OpenApiOperationCustomizerTest.java
git commit -m "feat : 공통 RFC 7807 에러응답 Swagger 일괄주입·ErrorResponse 스키마 #18"
```

## Task 5.2: 레퍼런스 컨트롤러(설교) + 핵심 DTO 문서화

**Files:**
- Modify: `domain/sermon/SermonController.java`, `domain/sermon/AdminSermonController.java`
- Modify: `domain/sermon/dto/SermonCreateRequest.java`, `dto/SermonCardResponse.java`(대표)
- Modify: `domain/main/MainController.java`

- [ ] **Step 1: 설교 공개 컨트롤러 문서화**

`SermonController.java` import 추가:

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
```

클래스에 `@Tag`, 메서드에 `@Operation` 부여:

```java
@Tag(name = "설교", description = "설교 공개 조회/관리 API(스펙 §5.5)")
@RestController
public class SermonController {
    ...
    @Operation(summary = "설교 목록", description = "공개. 카드 메타만(content 제외). 설교자·시리즈·날짜·태그 필터, q 검색, 페이지네이션.")
    @GetMapping("/api/sermons")
    public Page<SermonCardResponse> list( ... @Parameter(description = "설교자 필터") @RequestParam(required = false) String preacher, ... ) { ... }

    @Operation(summary = "설교 상세", description = "공개. content 포함. 조회 시 view_count 버퍼 +1.")
    @GetMapping("/api/sermons/{id}")
    public SermonDetailResponse get(@PathVariable Long id) { ... }
}
```

- [ ] **Step 2: 설교 관리 컨트롤러 문서화**

`AdminSermonController.java`에 동일 import + `@Tag(name = "설교")`(같은 그룹), 각 메서드 `@Operation`:

```java
    @Operation(summary = "설교 등록", description = "SERMON_WRITE 필요. tagIds로 태그 연결.")
    // POST
    @Operation(summary = "설교 전체 수정", description = "SERMON_WRITE. 낙관락(version) 필요, 충돌 시 409.")
    // PUT
    @Operation(summary = "설교 부분 수정", description = "SERMON_WRITE. 낙관락. null 필드는 미변경.")
    // PATCH
    @Operation(summary = "설교 삭제", description = "SERMON_WRITE. soft delete.")
    // DELETE
```

- [ ] **Step 3: 핵심 DTO 스키마 부여**

`SermonCreateRequest.java`·`SermonCardResponse.java` 레코드 컴포넌트에 `@Schema(description, example)` 부여(import `io.swagger.v3.oas.annotations.media.Schema`). 규약성 필드 우선:

```java
@Schema(description = "본문 마크다운. 이미지는 media:{id} 참조", example = "은혜로운 말씀 ![](media:42)")
String content
```

- [ ] **Step 4: `MainController` 문서화**

`MainController.java`에 `@Tag(name = "메인")`, `main()`에 `@Operation(summary = "메인 통합", description = "공개. 최신 설교 3·공지 3·다가오는 일정 5. Redis 캐싱.")`.

- [ ] **Step 5: 빌드/Swagger 회귀 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.config.*' --tests 'com.elipair.church.domain.sermon.*' --tests 'com.elipair.church.domain.main.*'`
Expected: PASS.

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/elipair/church/domain/sermon/ src/main/java/com/elipair/church/domain/main/MainController.java
git commit -m "docs : 설교·메인 컨트롤러 Swagger 문서화(레퍼런스 패턴) #18"
```

## Task 5.3: 나머지 22개 컨트롤러에 패턴 전파

> Task 5.2의 패턴(`@Tag` + 메서드별 `@Operation(summary, description)`)을 아래 컨트롤러에 동일 적용한다. 공통 에러응답은 Task 5.1 customizer가 자동 주입하므로 `@ApiResponse`는 성공 응답 위주로만 추가(또는 생략). 각 컨트롤러는 **하나의 커밋**으로 묶어 진행하면 리뷰가 쉽다.

각 컨트롤러: import 3종(`Operation`/`Tag`, 필요 시 `Parameter`) 추가 → 클래스 `@Tag(name)` → 메서드 `@Operation(summary, description; admin은 필요 권한·부수효과 명시)`.

| 컨트롤러 | @Tag name | 엔드포인트별 summary |
|---|---|---|
| `auth/controller/AuthController` | 인증 | 회원가입 / 로그인 / 토큰 재발급 / 로그아웃 |
| `member/controller/MeController` | 내 정보 | 내 정보 조회 / 내 정보 수정 / 내 동의 조회 / 재동의 제출 |
| `member/controller/MemberQueryController` | 회원(관리) | 교인 목록 / 교인 상세 |
| `member/controller/MemberAdminController` | 회원(관리) | 회원 정보 수정 / 비밀번호 초기화 |
| `member/controller/AgreementAdminController` | 약관(관리) | 동의 일괄 리셋 |
| `role/RoleController` | 역할 | 역할 목록 / 생성 / 수정 / 삭제 / 권한 일괄설정 |
| `role/PermissionController` | 권한 | 권한 목록 |
| `position/PositionController` | 직분 | 직분 목록 / 추가 / 수정 / 삭제 |
| `notice/NoticeController` | 공지 | 공지 목록 / 상세 |
| `notice/AdminNoticeController` | 공지 | 등록 / 전체수정 / 부분수정 / 삭제 |
| `event/EventController` | 일정 | 일정 목록(달력) / 상세 |
| `event/AdminEventController` | 일정 | 등록 / 전체수정 / 부분수정 / 삭제 |
| `department/DepartmentController` | 부서 | 부서 목록 / 상세 |
| `department/AdminDepartmentController` | 부서 | 등록 / 전체수정 / 부분수정 / 삭제 |
| `tag/TagController` | 태그 | 태그 목록 |
| `tag/AdminTagController` | 태그 | 추가 / 수정 / 삭제 |
| `media/MediaController` | 미디어 | 파일 서빙(공개) |
| `media/AdminMediaController` | 미디어(관리) | 업로드 / 목록 / 단건 / 참조목록 / 삭제 |
| `gallery/GalleryAlbumController` | 갤러리 | 앨범 목록(회원전용) / 앨범 상세 |
| `gallery/AdminGalleryController` | 갤러리(관리) | 앨범 생성 / 수정 / 삭제 / 사진 추가 / 사진 해제 |
| `bulletin/BulletinController` | 주보 | 주보 목록 / 상세 |
| `bulletin/AdminBulletinController` | 주보 | 업로드 / 수정 / 삭제 |

- [ ] **Step 1: 도메인별로 `@Tag`+`@Operation` 적용** (위 표 따라; 도메인 단위로 진행)

각 도메인 적용 후:

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: 전체 Swagger 문서 생성 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.config.OpenApiOperationCustomizerTest'`
Expected: PASS.

- [ ] **Step 3: 커밋(도메인 묶음별 또는 일괄)**

```bash
git add src/main/java/com/elipair/church/domain/
git commit -m "docs : 전 도메인 컨트롤러 Swagger 문서화(@Tag·@Operation) #18"
```

---

# Phase 6 — 종단 검증 & 마무리

## Task 6.1: 전체 빌드·테스트 그린 확인

- [ ] **Step 1: 포맷 적용(있으면)**

Run: `./gradlew spotlessApply` (없으면 건너뜀)

- [ ] **Step 2: 전체 빌드**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. 전 테스트 통과(기존 + 신규).

- [ ] **Step 3: 실패 시 대응**

타입/직렬화/슬라이스 관련 실패는 각 Task의 노트(특히 Phase 4 Context7 항목)를 참조해 수정 후 재빌드.

## Task 6.2: 새 교회 배포 검증 문서(§12)

**Files:**
- Modify: `docs/superpowers/specs/2026-06-07-main-caching-deploy-design.md` 또는 `.report/` (구현 보고서는 별도 /report 단계에서)

- [ ] **Step 1: §12 절차 수행 가능성 확인(문서)**

설계 §10의 배포 검증 5단계가 코드/설정으로 충족되는지 점검 결과를 기록:
- `.env.example` → `.env` 키 일치(신규 `CACHE_TTL`·`VIEW_FLUSH_INTERVAL`·`ADMIN_*` 포함)
- `docker-compose` backend env에 ADMIN_*/캐시/플러시 주입됨(Task 0.1)
- `SuperAdminInitializer` 멱등 시드 로그
- `GET /api/main` 200, Swagger UI 노출(`SWAGGER_ENABLED=true`)

- [ ] **Step 2: 커밋(문서 변경 시)**

```bash
git add docs/
git commit -m "docs : #18 배포 검증 절차 점검 기록 #18"
```

---

## 자체 검토 (작성자 체크)

- **스펙 커버리지:** D-1(Phase 2)·D-2(Phase 4)·D-3(Phase 3)·D-4(Phase 0.2)·D-5(Phase 0.1)·D-6(Phase 5)·D-7(각 Phase 테스트)·§12(Phase 6) 전부 Task 매핑됨.
- **플레이스홀더:** 신규/수정 코드는 실제 코드 제공. Swagger 22개 컨트롤러는 패턴(완전 코드)+표(컨트롤러별 태그·요약)로 처리 — 반복 스캐폴딩의 의도적 DRY.
- **타입 일관성:** `incrementViewCountBy(Long,long)`·`ViewCountStore.increment(String,long):long`·`drain(String):Map<Long,Long>`·`ViewCountFlushTarget.namespace()/applyDeltas`·`MainResponse(List,List,List)`·`detail(s,viewCount)` 오버로드가 전 Task에서 일치.
- **리스크(명시):** Phase 4의 SB4/Jackson3 직렬화·`PageImpl` 캐시 직렬화는 Context7 확인 + Task 4.3 통합 테스트가 검증. 실패 시 캐시 대상/직렬화기 조정 경로를 각 노트에 기재.
