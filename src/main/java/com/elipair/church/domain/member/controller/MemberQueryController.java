package com.elipair.church.domain.member.controller;

import com.elipair.church.domain.member.MemberService;
import com.elipair.church.domain.member.dto.MemberCardResponse;
import com.elipair.church.domain.member.dto.MemberDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 회원 조회(스펙 §5.2). path는 /api/members지만 메서드 보안으로 MEMBER_MANAGE 강제. */
@Tag(name = "회원(관리)", description = "교인 목록·상세 조회 및 관리 API(스펙 §5.2). MEMBER_MANAGE 또는 ROLE_MANAGE 필요.")
@RestController
@RequestMapping("/api/members")
@PreAuthorize("hasAuthority('MEMBER_MANAGE')")
public class MemberQueryController {

    private final MemberService service;

    public MemberQueryController(MemberService service) {
        this.service = service;
    }

    @Operation(summary = "교인 목록", description = "MEMBER_MANAGE 필요. 전체 교인 카드 목록. 페이지네이션.")
    @GetMapping
    public Page<MemberCardResponse> list(Pageable pageable) {
        return service.list(pageable);
    }

    @Operation(summary = "교인 상세", description = "MEMBER_MANAGE 필요. uuid로 특정 교인의 상세 정보(역할·직분 포함) 조회.")
    @GetMapping("/{uuid}")
    public MemberDetailResponse detail(@PathVariable UUID uuid) {
        return service.detail(uuid);
    }
}
