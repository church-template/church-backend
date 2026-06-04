package com.elipair.church.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 생성 시각만 담는 최상위 감사 기반 클래스(@MappedSuperclass).
 * 마스터·회원·미디어 등 "생성 시각은 필요하나 수정추적/소프트삭제/낙관락은 불필요"한 엔티티가 상속한다.
 * 감사 리스너는 서브클래스(BaseEntity)가 상속하므로, 거기 선언된 @LastModified*도 함께 처리된다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
