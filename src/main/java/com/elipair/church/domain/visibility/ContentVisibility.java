package com.elipair.church.domain.visibility;

import com.elipair.church.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * 교회별 메뉴 공개 범위 한 행(#95). 5개 값을 통째로 덮어쓰는 설정이라 soft delete·낙관락 없이 마지막 저장이 이긴다(설계 7).
 */
@Entity
@Table(
        name = "content_visibility",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_content_visibility_church_domain",
                        columnNames = {"church_id", "domain"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentVisibility extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentDomain domain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisibilityLevel level;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private ContentVisibility(ContentDomain domain, VisibilityLevel level) {
        this.domain = domain;
        this.level = level;
    }

    public static ContentVisibility create(ContentDomain domain, VisibilityLevel level) {
        return new ContentVisibility(domain, level);
    }

    public void change(VisibilityLevel level) {
        this.level = level;
    }
}
