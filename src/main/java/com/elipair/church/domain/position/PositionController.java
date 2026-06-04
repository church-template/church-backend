package com.elipair.church.domain.position;

import com.elipair.church.domain.position.dto.PositionCreateRequest;
import com.elipair.church.domain.position.dto.PositionResponse;
import com.elipair.church.domain.position.dto.PositionUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 직분 API(스펙 §5.3). GET은 공개(SecurityConfig anyRequest permitAll),
 * admin 3종은 /api/admin/** 인증 + POSITION_MANAGE 메서드 보안.
 */
@RestController
public class PositionController {

    private final PositionService service;

    public PositionController(PositionService service) {
        this.service = service;
    }

    @GetMapping("/api/positions")
    public List<PositionResponse> list() {
        return service.list();
    }

    @PostMapping("/api/admin/positions")
    @PreAuthorize("hasAuthority('POSITION_MANAGE')")
    public ResponseEntity<PositionResponse> create(@Valid @RequestBody PositionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PatchMapping("/api/admin/positions/{id}")
    @PreAuthorize("hasAuthority('POSITION_MANAGE')")
    public PositionResponse update(@PathVariable Long id, @Valid @RequestBody PositionUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/api/admin/positions/{id}")
    @PreAuthorize("hasAuthority('POSITION_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
