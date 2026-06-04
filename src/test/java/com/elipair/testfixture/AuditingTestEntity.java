package com.elipair.testfixture;

import com.elipair.church.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/** BaseEntity 감사 동작 검증용 테스트 전용 엔티티. 앱 엔티티 스캔(com.elipair.church) 밖에 둬 전체 컨텍스트 테스트를 오염시키지 않는다. */
@Getter
@Entity
@Table(name = "auditing_test_entity")
public class AuditingTestEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    protected AuditingTestEntity() {}

    public AuditingTestEntity(String name) {
        this.name = name;
    }
}
