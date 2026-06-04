package com.elipair.church.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.config.JpaConfig;
import com.elipair.testfixture.AuditingTestEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@EntityScan(basePackageClasses = AuditingTestEntity.class)
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class BaseEntityAuditingTest {

    @Autowired
    private TestEntityManager em;

    @Test
    void auditing_populates_timestamps_and_version_on_persist() {
        AuditingTestEntity saved = em.persistFlushFind(new AuditingTestEntity("샘플"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(0L);
        // 작성자는 AuditorAware 스텁이 빈 값을 반환하므로 #4까지 null
        assertThat(saved.getCreatedBy()).isNull();
        assertThat(saved.getUpdatedBy()).isNull();
        assertThat(saved.isDeleted()).isFalse();
    }

    @Test
    void softDelete_marks_entity_deleted() {
        AuditingTestEntity entity = em.persistFlushFind(new AuditingTestEntity("샘플"));
        assertThat(entity.isDeleted()).isFalse();

        entity.softDelete();
        em.flush();
        em.clear();

        AuditingTestEntity reloaded = em.find(AuditingTestEntity.class, entity.getId());
        assertThat(reloaded.isDeleted()).isTrue();
        assertThat(reloaded.getDeletedAt()).isNotNull();
    }
}
