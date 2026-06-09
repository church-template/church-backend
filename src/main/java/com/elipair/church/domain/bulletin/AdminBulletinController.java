package com.elipair.church.domain.bulletin;

import com.elipair.church.domain.bulletin.dto.BulletinDetailResponse;
import com.elipair.church.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 주보 관리 API(스펙 §5.13). 전 메서드 BULLETIN_WRITE. multipart(file XOR mediaId).
 * 스칼라는 required=false로 받고 필수성·XOR·공백은 서비스에서 검증(업로드 전, 설계 §6.1).
 */
@Tag(name = "주보", description = "주보 공개 조회/관리 API(스펙 §5.13). PDF media 재사용 기반.")
@RestController
@PreAuthorize("hasAuthority('BULLETIN_WRITE')")
public class AdminBulletinController {

    private final BulletinService service;

    public AdminBulletinController(BulletinService service) {
        this.service = service;
    }

    @Operation(summary = "주보 업로드", description = """
                    주보를 등록한다. PDF는 신규 업로드 또는 기존 미디어 재사용 중 하나로 연결한다.

                    - 인증(JWT): 필요 — `BULLETIN_WRITE`
                    - 요청 본문/업로드: multipart — `title`(필수)·`serviceDate`(필수, `yyyy-MM-dd`)·`file`(신규 PDF 업로드) XOR `mediaId`(기존 PDF media 재사용). 둘 다/둘 다 없음은 거부
                    - 반환값: `BulletinDetailResponse` — 생성된 주보 상세(`mediaId`로 PDF 참조), 201 Created
                    - 부수효과: `file`은 매직바이트로 PDF 검증 후 라이브러리 저장(한도 초과 시 413 FILE_SIZE_EXCEEDED) · `mediaId`는 PDF media 재사용 · 모든 검증은 디스크 쓰기보다 먼저(고아 파일 방지)
                    """)
    @PostMapping("/api/admin/bulletins")
    public ResponseEntity<BulletinDetailResponse> create(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) Long mediaId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(title, serviceDate, file, mediaId, principal.id()));
    }

    @Operation(summary = "주보 수정", description = """
                    주보를 부분 수정한다(PATCH). 전달된(비-null) 파라미터만 적용한다.

                    - 인증(JWT): 필요 — `BULLETIN_WRITE`
                    - 경로 변수: `id` — 수정할 주보 ID
                    - 요청 본문/업로드: multipart — `version`(필수, 낙관락)·`title`·`serviceDate`·`file` XOR `mediaId`(PDF 교체 시). null 파라미터는 미변경
                    - 반환값: `BulletinDetailResponse` — 수정된 주보 상세(`version` 증가분 반영)
                    - 부수효과: `version` 불일치 시 409 OPTIMISTIC_LOCK_CONFLICT(업로드보다 먼저 검사 → 충돌 시 파일 미생성) · PDF 교체 시 413 FILE_SIZE_EXCEEDED 가능
                    """)
    @PatchMapping("/api/admin/bulletins/{id}")
    public BulletinDetailResponse patch(
            @PathVariable Long id,
            @RequestParam(required = false) Long version,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) Long mediaId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return service.patch(id, version, title, serviceDate, file, mediaId, principal.id());
    }

    @Operation(summary = "주보 삭제", description = """
                    주보를 soft delete 한다.

                    - 인증(JWT): 필요 — `BULLETIN_WRITE`
                    - 경로 변수: `id` — 삭제할 주보 ID
                    - 반환값: 없음(204)
                    - 부수효과: soft delete · media 원본(PDF)은 라이브러리에 보존(연결해제)되므로 실제 파일 삭제는 미디어 차단형 삭제 API를 별도 사용
                    """)
    @DeleteMapping("/api/admin/bulletins/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
