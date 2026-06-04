package com.elipair.church.global.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** G3 경로·메서드 인가 검증용 테스트 컨트롤러(도메인 컨트롤러 부재 대체). */
@RestController
public class SecuredTestController {

    @GetMapping("/api/public/ping")
    public String publicPing() {
        return "public";
    }

    @GetMapping("/api/admin/ping")
    @PreAuthorize("hasAuthority('SERMON_WRITE')")
    public String adminPing() {
        return "admin";
    }

    @GetMapping("/api/gallery/ping")
    public String galleryPing() {
        return "gallery";
    }

    @GetMapping("/api/me/ping")
    @PreAuthorize("isAuthenticated()")
    public String mePing() {
        return "me";
    }
}
