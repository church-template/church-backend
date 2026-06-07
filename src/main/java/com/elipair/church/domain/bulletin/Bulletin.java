package com.elipair.church.domain.bulletin;

import com.elipair.church.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주보(스펙 §5.13). 수정가능 콘텐츠라 BaseEntity(감사·소프트삭제·낙관락)를 상속.
 * media_id는 평문 Long FK(저결합, 갤러리 관례). 컬럼은 nullable + ON DELETE SET NULL(설계 §2.1 —
 * soft-deleted 주보의 FK 댕글링 방지)이지만, 활성 주보의 비-null은 서비스(생성 시 file XOR mediaId 필수)가 보장한다.
 * created_by/updated_by는 AuditorAware가 자동 주입(서비스 수동 세팅 안 함).
 */
@Entity
@Table(name = "bulletins")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bulletin extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "media_id")
    private Long mediaId;

    private Bulletin(String title, LocalDate serviceDate, Long mediaId) {
        this.title = title;
        this.serviceDate = serviceDate;
        this.mediaId = mediaId;
    }

    public static Bulletin create(String title, LocalDate serviceDate, Long mediaId) {
        return new Bulletin(title, serviceDate, mediaId);
    }

    /** PATCH 부분 수정 — null 인자는 미변경. mediaId는 새 PDF 해소 결과(없으면 null=기존 유지). */
    public void applyPatch(String title, LocalDate serviceDate, Long mediaId) {
        if (title != null) {
            this.title = title;
        }
        if (serviceDate != null) {
            this.serviceDate = serviceDate;
        }
        if (mediaId != null) {
            this.mediaId = mediaId;
        }
    }
}
