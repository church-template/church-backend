package com.elipair.church.domain.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.config.JpaConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class NoticeRepositoryTest {

    @Autowired
    private NoticeRepository repository;

    private Notice notice(String title) {
        return Notice.create(title, "본문", false);
    }

    @Test
    void save_populates_audit_columns() {
        Notice saved = repository.saveAndFlush(notice("공지 A"));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getViewCount()).isZero();
        assertThat(saved.isPinned()).isFalse();
    }

    @Test
    void findByIdAndDeletedAtIsNull_excludes_soft_deleted() {
        Notice active = repository.saveAndFlush(notice("활성"));
        Notice deleted = notice("삭제");
        deleted.softDelete();
        Notice savedDeleted = repository.saveAndFlush(deleted);

        assertThat(repository.findByIdAndDeletedAtIsNull(active.getId())).isPresent();
        assertThat(repository.findByIdAndDeletedAtIsNull(savedDeleted.getId())).isEmpty();
    }

    @Test
    void incrementViewCount_is_atomic_and_skips_deleted() {
        Notice n = repository.saveAndFlush(notice("조회수"));

        int updated = repository.incrementViewCount(n.getId());

        assertThat(updated).isEqualTo(1);
        assertThat(repository
                        .findByIdAndDeletedAtIsNull(n.getId())
                        .orElseThrow()
                        .getViewCount())
                .isEqualTo(1L);
    }

    @Test
    void incrementViewCount_returns_zero_for_deleted() {
        Notice deleted = notice("삭제됨");
        deleted.softDelete();
        Notice saved = repository.saveAndFlush(deleted);

        assertThat(repository.incrementViewCount(saved.getId())).isZero();
    }

    @Test
    void filter_q_matches_title_case_insensitively() {
        repository.saveAndFlush(notice("부활절 안내"));
        repository.saveAndFlush(notice("성탄절 안내"));

        assertThat(repository
                        .findAll(NoticeSpecifications.filter("부활", null), PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(1);
    }

    @Test
    void filter_taggedIds_empty_returns_none_and_excludes_deleted() {
        Notice n = repository.saveAndFlush(notice("A"));
        Notice deleted = notice("D");
        deleted.softDelete();
        repository.saveAndFlush(deleted);

        assertThat(repository
                        .findAll(NoticeSpecifications.filter(null, List.of()), PageRequest.of(0, 10))
                        .getTotalElements())
                .isZero();
        assertThat(repository
                        .findAll(NoticeSpecifications.filter(null, List.of(n.getId())), PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(1);
        assertThat(repository
                        .findAll(NoticeSpecifications.filter(null, null), PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(1);
    }
}
