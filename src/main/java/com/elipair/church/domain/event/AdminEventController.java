package com.elipair.church.domain.event;

import com.elipair.church.domain.event.dto.EventCreateRequest;
import com.elipair.church.domain.event.dto.EventDetailResponse;
import com.elipair.church.domain.event.dto.EventPatchRequest;
import com.elipair.church.domain.event.dto.EventUpdateRequest;
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
@RestController
@PreAuthorize("hasAuthority('EVENT_WRITE')")
public class AdminEventController {

    private final EventService service;

    public AdminEventController(EventService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/events")
    public ResponseEntity<EventDetailResponse> create(@Valid @RequestBody EventCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/api/admin/events/{id}")
    public EventDetailResponse update(@PathVariable Long id, @Valid @RequestBody EventUpdateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/api/admin/events/{id}")
    public EventDetailResponse patch(@PathVariable Long id, @Valid @RequestBody EventPatchRequest request) {
        return service.patch(id, request);
    }

    @DeleteMapping("/api/admin/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
