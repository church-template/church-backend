package com.elipair.church;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 마이그레이션이 만든 **부분 인덱스**(WHERE deleted_at IS NULL)가 실제 DB에 존재하고 조건절이 살아있는지 검증.
 *
 * <p>일반 리포지토리 슬라이스는 {@code flyway.enabled=false} + {@code ddl-auto=create-drop}이라 마이그레이션 인덱스를
 * 만들지 않고, 운영의 {@code ddl-auto=validate}도 인덱스는 검사하지 않는다. 그래서 이 테스트만 Flyway를 켜서
 * 마이그레이션을 실제 적용하고 {@code pg_indexes.indexdef}를 조회해 인덱스 정의(컬럼·부분 조건)를 확인한다.
 * 신규 콘텐츠 도메인이 부분 인덱스를 추가하면 여기에 검증 1건을 추가한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
class MigrationIndexTest {

    @PersistenceContext
    private EntityManager em;

    private String indexDef(String indexName) {
        List<?> rows = em.createNativeQuery("select indexdef from pg_indexes where indexname = :name")
                .setParameter("name", indexName)
                .getResultList();
        return rows.isEmpty() ? null : (String) rows.get(0);
    }

    @Test
    void sermons_preached_at_is_partial_on_active_rows() {
        assertThat(indexDef("idx_sermons_preached_at"))
                .as("V7 설교 목록 인덱스")
                .isNotNull()
                .contains("preached_at")
                .contains("deleted_at IS NULL");
    }

    @Test
    void notices_pinned_created_is_partial_on_active_rows() {
        assertThat(indexDef("idx_notices_pinned_created"))
                .as("V8 공지 상단고정 정렬 인덱스")
                .isNotNull()
                .contains("is_pinned")
                .contains("created_at")
                .contains("deleted_at IS NULL");
    }

    @Test
    void members_phone_unique_is_partial_on_active_rows() {
        assertThat(indexDef("uq_members_phone_active"))
                .as("V3 전화번호 부분 유니크(번호 재활용 허용)")
                .isNotNull()
                .contains("phone")
                .contains("deleted_at IS NULL");
    }

    @Test
    void events_start_at_is_partial_on_active_rows() {
        assertThat(indexDef("idx_events_start_at"))
                .as("V9 일정 시작일 범위 인덱스")
                .isNotNull()
                .contains("start_at")
                .contains("deleted_at IS NULL");
    }
}
