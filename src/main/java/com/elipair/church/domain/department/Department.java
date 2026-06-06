package com.elipair.church.domain.department;

import com.elipair.church.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 교구/부서(스펙 §5.8). 수정가능 콘텐츠라 BaseEntity(감사·소프트삭제·낙관락)를 상속.
 * parentId는 자기참조(평문 Long — created_by/updated_by 관례와 동일, 순환검사는 리포지토리로 체인 탐색).
 * leader는 담당 교역자 이름 평문(FK 아님). created_by/updated_by는 AuditorAware 자동 주입·응답 미노출(설계 §1).
 */
@Entity
@Table(name = "departments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String leader;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    private Department(String name, String description, String leader, Long parentId, Integer sortOrder) {
        this.name = name;
        this.description = description;
        this.leader = leader;
        this.parentId = parentId;
        this.sortOrder = sortOrder;
    }

    /** 팩토리. sortOrder는 서비스가 해석한 값(미지정 시 max+10)을 받는다. */
    public static Department create(String name, String description, String leader, Long parentId, Integer sortOrder) {
        return new Department(name, description, leader, parentId, sortOrder);
    }

    /** PUT 전체 교체 — parentId=null이면 루트화, sortOrder=null이면 기존값 유지(positions update 관례). */
    public void update(String name, String description, String leader, Long parentId, Integer sortOrder) {
        this.name = name;
        this.description = description;
        this.leader = leader;
        this.parentId = parentId;
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
    }

    /** PATCH 부분 수정 — null 인자는 미변경(parentId 비우기=루트화는 PUT 사용). */
    public void applyPatch(String name, String description, String leader, Long parentId, Integer sortOrder) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (leader != null) {
            this.leader = leader;
        }
        if (parentId != null) {
            this.parentId = parentId;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
    }
}
