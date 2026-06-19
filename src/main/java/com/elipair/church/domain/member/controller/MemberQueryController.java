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
import org.springframework.web.bind.annotation.RequestParam;
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

    @Operation(summary = "교인 목록", description = """
                    전체 회원 카드 목록 조회(소프트 삭제 제외). 가입 승인·역할 관리용.

                    - 인증(JWT): 필요 — `MEMBER_MANAGE`
                    - 요청 파라미터: `q`(선택) — 이름 또는 전화번호 부분검색(없으면 전체) · `page`·`size`·`sort` — 페이지네이션
                    - 반환값: `Page<MemberCardResponse>` — uuid·이름·전화번호·직분·역할·`approved`(GALLERY_VIEW 보유 = 승인)·createdAt 카드 목록
                    """)
    @GetMapping
    public Page<MemberCardResponse> list(@RequestParam(required = false) String q, Pageable pageable) {
        return service.list(q, pageable);
    }

    @Operation(summary = "교인 상세", description = """
                    uuid로 특정 회원의 상세 정보를 조회.

                    - 인증(JWT): 필요 — `MEMBER_MANAGE`
                    - 경로 변수: `uuid` — 조회할 회원 uuid
                    - 반환값: `MemberDetailResponse` — 이메일·직분·역할·권한·`approved`(GALLERY_VIEW 보유 = 승인)·약관 동의 상태 포함 상세
                    """)
    @GetMapping("/{uuid}")
    public MemberDetailResponse detail(@PathVariable UUID uuid) {
        return service.detail(uuid);
    }
}
