package com.elipair.church.domain.challenge;

import com.elipair.church.domain.challenge.dto.ChallengeCreateRequest;
import com.elipair.church.domain.challenge.dto.ChallengeDetailResponse;
import com.elipair.church.domain.challenge.dto.ChallengePatchRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 통독 챌린지 관리 API(설계 §3). 전 메서드 CHALLENGE_MANAGE. */
@Tag(name = "통독 챌린지", description = "성경 통독 챌린지 API(설계 2026-07-06)")
@RestController
@PreAuthorize("hasAuthority('CHALLENGE_MANAGE')")
public class AdminBibleChallengeController {

    private final BibleChallengeService service;

    public AdminBibleChallengeController(BibleChallengeService service) {
        this.service = service;
    }

    @Operation(summary = "챌린지 개설", description = """
                    새 통독 챌린지를 개설한다(201 Created).

                    - 인증(JWT): 필요 — `CHALLENGE_MANAGE`
                    - 요청 본문: `ChallengeCreateRequest` — 제목(필수)·설명·권 구간(`startBook`~`endBook`, 1~66)·시작일·목표 일수
                    - 반환값: `ChallengeDetailResponse` — 파생값(종료일·총 장수·하루 목표·상태) 포함
                    - 부수효과: 없음 (성경 구조는 코드 상수 — 별도 데이터 준비 불필요)
                    """)
    @PostMapping("/api/admin/bible-challenges")
    public ResponseEntity<ChallengeDetailResponse> create(@Valid @RequestBody ChallengeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "챌린지 수정", description = """
                    챌린지를 부분 수정한다(PATCH). null 필드는 미변경.

                    - 인증(JWT): 필요 — `CHALLENGE_MANAGE`
                    - 경로 변수: `id` — 수정할 챌린지 ID
                    - 요청 본문: `ChallengePatchRequest` — 변경 필드 + `version`(낙관락, 필수)
                    - 반환값: `ChallengeDetailResponse`(`version`은 증가 후 값)
                    - 부수효과: `version` 불일치 시 409 · 참여자 존재 시 구간·기간(startBook/endBook/startDate/targetDays) 수정은 400
                    """)
    @PatchMapping("/api/admin/bible-challenges/{id}")
    public ChallengeDetailResponse patch(@PathVariable Long id, @Valid @RequestBody ChallengePatchRequest request) {
        return service.patch(id, request);
    }

    @Operation(summary = "챌린지 삭제", description = """
                    챌린지를 삭제한다(204 No Content).

                    - 인증(JWT): 필요 — `CHALLENGE_MANAGE`
                    - 경로 변수: `id` — 삭제할 챌린지 ID
                    - 반환값: 없음(204)
                    - 부수효과: soft delete — 참여·로그는 이력 보존(마이페이지에 계속 표시), 신규 기록만 차단
                    """)
    @DeleteMapping("/api/admin/bible-challenges/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
