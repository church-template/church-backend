package com.elipair.church.domain.event;

import com.elipair.church.domain.event.dto.EventCreateRequest;
import com.elipair.church.domain.event.dto.EventDetailResponse;
import com.elipair.church.domain.event.dto.EventPatchRequest;
import com.elipair.church.domain.event.dto.EventUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 일정 관리 API(스펙 §5.6). 전 메서드 EVENT_WRITE. */
@Tag(name = "일정")
@RestController
@PreAuthorize("hasAuthority('EVENT_WRITE')")
public class AdminEventController {

    private final EventService service;

    public AdminEventController(EventService service) {
        this.service = service;
    }

    @Operation(summary = "일정 등록", description = """
                    새 일정을 생성한다.

                    - 인증(JWT): 필요 — `EVENT_WRITE`
                    - 요청 본문: `EventCreateRequest` — 제목·description·장소·시작/종료 일시·종일(미지정 false)·`tagIds`; 종료는 시작보다 엄격히 이후 또는 null(점 이벤트)
                    - 반환값: `EventDetailResponse` — 생성된 일정 상세(201)
                    - 부수효과: `tagIds`로 태그 연결 · 메인 캐시(main) 무효화
                    """)
    @PostMapping("/api/admin/events")
    public ResponseEntity<EventDetailResponse> create(@Valid @RequestBody EventCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "일정 전체 수정", description = """
                    일정 전체 교체(PUT). 제공한 값으로 모든 필드를 덮어쓴다.

                    - 인증(JWT): 필요 — `EVENT_WRITE`
                    - 경로 변수: `id` — 수정할 일정 ID
                    - 요청 본문: `EventUpdateRequest` — 제목·description·장소·시작/종료 일시·종일·`tagIds`·`version`(필수); 종료는 시작보다 엄격히 이후 또는 null
                    - 반환값: `EventDetailResponse` — 수정된 일정 상세
                    - 부수효과: `version` 불일치 시 409 OPTIMISTIC_LOCK_CONFLICT · `tagIds`로 태그 재연결 · 메인 캐시(main) 무효화
                    """)
    @PutMapping("/api/admin/events/{id}")
    public EventDetailResponse update(@PathVariable Long id, @Valid @RequestBody EventUpdateRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "일정 부분 수정", description = """
                    일정 부분 수정(PATCH). 전달된(비-null) 필드만 적용한다.

                    - 인증(JWT): 필요 — `EVENT_WRITE`
                    - 경로 변수: `id` — 수정할 일정 ID
                    - 요청 본문: `EventPatchRequest` — 제목·description·장소·시작/종료 일시·종일·`tagIds`(null이면 태그 미변경)·`version`(필수); start/end 교차검증은 DB값과 합쳐 서비스가 수행(종료는 시작보다 이후)
                    - 반환값: `EventDetailResponse` — 수정된 일정 상세
                    - 부수효과: `version` 불일치 시 409 OPTIMISTIC_LOCK_CONFLICT · 메인 캐시(main) 무효화
                    """)
    @PatchMapping("/api/admin/events/{id}")
    public EventDetailResponse patch(@PathVariable Long id, @Valid @RequestBody EventPatchRequest request) {
        return service.patch(id, request);
    }

    @Operation(summary = "일정 삭제", description = """
                    일정을 삭제한다.

                    - 인증(JWT): 필요 — `EVENT_WRITE`
                    - 경로 변수: `id` — 삭제할 일정 ID
                    - 반환값: 없음(204)
                    - 부수효과: soft delete(deleted_at) · 연결 태그 정리 · 메인 캐시(main) 무효화
                    """)
    @DeleteMapping("/api/admin/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
