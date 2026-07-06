package com.elipair.church.domain.challenge;

import com.elipair.church.domain.challenge.dto.ChallengeCardResponse;
import com.elipair.church.domain.challenge.dto.ChallengeDetailResponse;
import com.elipair.church.domain.challenge.dto.ChallengeReadRequest;
import com.elipair.church.domain.challenge.dto.MyParticipationResponse;
import com.elipair.church.domain.challenge.dto.MyProgressResponse;
import com.elipair.church.domain.challenge.dto.ReadingLogResponse;
import com.elipair.church.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통독 챌린지 회원 API(설계 §3). 인가는 SecurityConfig 경로 규칙(/api/bible-challenges/** → CHALLENGE_PARTICIPATE) —
 * 갤러리 패턴이라 메서드 어노테이션 없음. read 본문은 선택(빈 {} 허용 — 기본값: 오늘·남은 목표치).
 */
@Tag(name = "통독 챌린지")
@RestController
public class BibleChallengeController {

    private final BibleChallengeService challengeService;
    private final ChallengeProgressService progressService;

    public BibleChallengeController(BibleChallengeService challengeService, ChallengeProgressService progressService) {
        this.challengeService = challengeService;
        this.progressService = progressService;
    }

    @Operation(summary = "챌린지 목록", description = """
                    챌린지 카드 목록(파생 status 포함, 본문 description 제외).

                    - 인증(JWT): 필요 — `CHALLENGE_PARTICIPATE`(MEMBER)
                    - 요청 파라미터: `page`·`size`·`sort`(기본 startDate,desc)
                    - 반환값: `Page<ChallengeCardResponse>`
                    """)
    @GetMapping("/api/bible-challenges")
    public Page<ChallengeCardResponse> list(
            @PageableDefault(size = 10, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return challengeService.list(pageable);
    }

    @Operation(summary = "챌린지 상세", description = """
                    챌린지 상세(본문·내 참여 여부 `joined` 포함).

                    - 인증(JWT): 필요 — `CHALLENGE_PARTICIPATE`
                    - 경로 변수: `id`
                    - 반환값: `ChallengeDetailResponse`
                    """)
    @GetMapping("/api/bible-challenges/{id}")
    public ChallengeDetailResponse get(@PathVariable Long id, @AuthenticationPrincipal MemberPrincipal principal) {
        return challengeService.get(id, principal.id());
    }

    @Operation(summary = "챌린지 참여", description = """
                    챌린지에 참여한다(201 Created).

                    - 인증(JWT): 필요 — `CHALLENGE_PARTICIPATE`
                    - 경로 변수: `id`
                    - 반환값: `MyProgressResponse` — 초기 대시보드(진행 0)
                    - 부수효과: 중복 참여 시 409 DUPLICATE_RESOURCE
                    """)
    @PostMapping("/api/bible-challenges/{id}/join")
    public ResponseEntity<MyProgressResponse> join(
            @PathVariable Long id, @AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(progressService.join(id, principal.id()));
    }

    @Operation(summary = "읽음 기록", description = """
                    "오늘 N장 읽음"을 기록한다. 본문 생략/빈 객체 = 해당 날짜의 남은 목표치.

                    - 인증(JWT): 필요 — `CHALLENGE_PARTICIPATE`
                    - 경로 변수: `id` — 챌린지 ID
                    - 요청 본문: `ChallengeReadRequest` — `chapters`(기본 남은 목표치)·`date`(기본 오늘, 소급 = 챌린지 시작일~오늘)
                    - 반환값: `MyProgressResponse` — 갱신된 대시보드
                    - 부수효과: 같은 날 재기록은 누적 · 구간 끝 도달 시 회독 +1·초과분 이월 · 동시 클릭 시 409
                    """)
    @PostMapping("/api/bible-challenges/{id}/read")
    public MyProgressResponse read(
            @PathVariable Long id,
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody(required = false) ChallengeReadRequest request) {
        ChallengeReadRequest req = request != null ? request : new ChallengeReadRequest(null, null);
        return progressService.read(id, principal.id(), req);
    }

    @Operation(summary = "읽음 기록 취소", description = """
                    해당 날짜의 기록을 취소한다(실수 클릭 복구).

                    - 인증(JWT): 필요 — `CHALLENGE_PARTICIPATE`
                    - 경로 변수: `id` — 챌린지 ID
                    - 요청 파라미터: `date`(기본 오늘)
                    - 반환값: `MyProgressResponse` — 롤백된 대시보드
                    - 부수효과: 해당 날짜 로그 물리 삭제 + 포인터 롤백(회독 경계 역이월) · 로그 없으면 404
                    """)
    @DeleteMapping("/api/bible-challenges/{id}/read")
    public MyProgressResponse cancelRead(
            @PathVariable Long id,
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return progressService.cancelRead(id, principal.id(), date);
    }

    @Operation(summary = "내 진행 대시보드", description = """
                    진행률·현재 위치·오늘 현황·스트릭·회독·페이스를 한 번에 반환한다(UI 원샷).

                    - 인증(JWT): 필요 — `CHALLENGE_PARTICIPATE`
                    - 경로 변수: `id` — 챌린지 ID
                    - 반환값: `MyProgressResponse` — `currentPosition` null=회독 시작 전, `paceDays` null=기간 종료
                    """)
    @GetMapping("/api/bible-challenges/{id}/my-progress")
    public MyProgressResponse myProgress(@PathVariable Long id, @AuthenticationPrincipal MemberPrincipal principal) {
        return progressService.myProgress(id, principal.id());
    }

    @Operation(summary = "내 읽기 로그", description = """
                    달력 히트맵용 날짜별 로그(배열 — 페이지 아님).

                    - 인증(JWT): 필요 — `CHALLENGE_PARTICIPATE`
                    - 경로 변수: `id` — 챌린지 ID
                    - 요청 파라미터: `from`·`to`(생략 시 챌린지 기간 전체)
                    - 반환값: `List<ReadingLogResponse>` — 날짜 오름차순
                    """)
    @GetMapping("/api/bible-challenges/{id}/my-logs")
    public List<ReadingLogResponse> myLogs(
            @PathVariable Long id,
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return progressService.myLogs(id, principal.id(), from, to);
    }

    @Operation(summary = "내 참여 이력", description = """
                    마이페이지용 — 전 챌린지 참여 이력(과거 포함, 삭제된 챌린지 이력도 보존 표시).

                    - 인증(JWT): 필요 — `CHALLENGE_PARTICIPATE`
                    - 요청 파라미터: `page`·`size`·`sort`(기본 createdAt,desc = 최근 참여순)
                    - 반환값: `Page<MyParticipationResponse>` — 진행률·회독·완주 여부·스트릭 포함
                    """)
    @GetMapping("/api/bible-challenges/my-participations")
    public Page<MyParticipationResponse> myParticipations(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return progressService.myParticipations(principal.id(), pageable);
    }
}
