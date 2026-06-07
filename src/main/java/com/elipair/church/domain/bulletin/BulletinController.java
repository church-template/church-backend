package com.elipair.church.domain.bulletin;

import com.elipair.church.domain.bulletin.dto.BulletinCardResponse;
import com.elipair.church.domain.bulletin.dto.BulletinDetailResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 주보 공개 조회 API(스펙 §5.13). 비인증 — SecurityConfig anyRequest permitAll. */
@RestController
public class BulletinController {

    private final BulletinService service;

    public BulletinController(BulletinService service) {
        this.service = service;
    }

    @GetMapping("/api/bulletins")
    public Page<BulletinCardResponse> list(
            @PageableDefault(size = 10, sort = "serviceDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/api/bulletins/{id}")
    public BulletinDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
