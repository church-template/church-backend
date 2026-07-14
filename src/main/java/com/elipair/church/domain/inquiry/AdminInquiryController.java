package com.elipair.church.domain.inquiry;

import com.elipair.church.domain.inquiry.dto.InquiryCardResponse;
import com.elipair.church.domain.inquiry.dto.InquiryCompleteRequest;
import com.elipair.church.domain.inquiry.dto.InquiryDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 문의 관리 API(이슈 #50). 전 메서드 INQUIRY_MANAGE — 문의에는 개인정보가 담기므로 조회부터 권한이 필요하다. */
@Tag(name = "문의")
@RestController
@PreAuthorize("hasAuthority('INQUIRY_MANAGE')")
public class AdminInquiryController {

    private final InquiryService service;

    public AdminInquiryController(InquiryService service) {
        this.service = service;
    }

    @Operation(summary = "문의 목록", description = """
                    문의 카드 목록을 완료 여부로 필터해 조회한다(최신순).

                    - 인증(JWT): 필요 — `INQUIRY_MANAGE`
                    - 요청 파라미터: `completed` — 미지정 시 전체, `false`면 미처리만, `true`면 완료만; `page`·`size`·`sort` — 페이지네이션(기본 `createdAt,desc`)
                    - 반환값: `Page<InquiryCardResponse>` — 카드 메타만(문의 내용 `content` 제외)·페이지네이션
                    """)
    @GetMapping("/api/admin/inquiries")
    public Page<InquiryCardResponse> list(
            @RequestParam(required = false) Boolean completed,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(completed, pageable);
    }

    @Operation(summary = "문의 상세", description = """
                    문의 한 건의 상세를 조회한다(문의 내용 포함).

                    - 인증(JWT): 필요 — `INQUIRY_MANAGE`
                    - 경로 변수: `id` — 조회할 문의 ID
                    - 반환값: `InquiryDetailResponse` — 이름·연락처·이메일·문의 내용·완료 여부
                    """)
    @GetMapping("/api/admin/inquiries/{id}")
    public InquiryDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "문의 완료 처리", description = """
                    문의를 완료로 체크하거나 완료를 취소한다.

                    - 인증(JWT): 필요 — `INQUIRY_MANAGE`
                    - 경로 변수: `id` — 처리할 문의 ID
                    - 요청 본문: `InquiryCompleteRequest` — `completed`(true=완료, false=완료 취소)
                    - 반환값: `InquiryDetailResponse` — 갱신된 상세(`completedAt` 반영)
                    - 참고: 답변 자체는 담당자가 이메일/문자로 직접 발송한다. API는 처리 여부만 기록하며, 처리한 관리자는 `updated_by`에 남는다
                    """)
    @PatchMapping("/api/admin/inquiries/{id}/complete")
    public InquiryDetailResponse complete(@PathVariable Long id, @Valid @RequestBody InquiryCompleteRequest request) {
        return service.complete(id, request.completed());
    }

    @Operation(summary = "문의 삭제", description = """
                    문의를 삭제한다(204 No Content).

                    - 인증(JWT): 필요 — `INQUIRY_MANAGE`
                    - 경로 변수: `id` — 삭제할 문의 ID
                    - 반환값: 없음(204)
                    - 부수효과: soft delete(`deleted_at` 설정) — 목록·상세에서 즉시 제외된다
                    """)
    @DeleteMapping("/api/admin/inquiries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
