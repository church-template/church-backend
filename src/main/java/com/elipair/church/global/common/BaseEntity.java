package com.elipair.church.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * 수정가능 콘텐츠(설교·공지·일정·부서·갤러리앨범·주보)가 상속하는 전체 감사 기반 클래스.
 * BaseTimeEntity(createdAt)에 수정추적·작성자·소프트삭제·낙관락을 더한다.
 * created_by/updated_by는 Member 연관이 아니라 member.id(Long) — global→domain 의존을 피한다.
 * 작성자 값은 AuditorAware가 SecurityContext를 읽는 #4부터 채워진다(현재 null).
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseTimeEntity {

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
