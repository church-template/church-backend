package com.elipair.church.domain.sermon;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.config.JpaConfig;
import java.time.LocalDate;
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
class SermonRepositoryTest {

    @Autowired
    private SermonRepository repository;

    private Sermon sermon(String title) {
        return Sermon.create(title, "김목사", "산상수훈", "마 5:1", "본문", null, null, LocalDate.of(2026, 6, 1));
    }

    @Test
    void save_populates_audit_columns() {
        Sermon saved = repository.saveAndFlush(sermon("설교 A"));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getViewCount()).isZero();
    }

    @Test
    void findByIdAndDeletedAtIsNull_excludes_soft_deleted() {
        Sermon active = repository.saveAndFlush(sermon("활성"));
        Sermon deleted = sermon("삭제");
        deleted.softDelete();
        Sermon savedDeleted = repository.saveAndFlush(deleted);

        assertThat(repository.findByIdAndDeletedAtIsNull(active.getId())).isPresent();
        assertThat(repository.findByIdAndDeletedAtIsNull(savedDeleted.getId())).isEmpty();
    }

    @Test
    void incrementViewCountBy_adds_delta_and_skips_deleted() {
        Sermon s = repository.saveAndFlush(sermon("조회수"));

        int updated = repository.incrementViewCountBy(s.getId(), 5L);

        assertThat(updated).isEqualTo(1);
        assertThat(repository
                        .findByIdAndDeletedAtIsNull(s.getId())
                        .orElseThrow()
                        .getViewCount())
                .isEqualTo(5L);
    }

    @Test
    void incrementViewCountBy_returns_zero_for_deleted() {
        Sermon deleted = sermon("삭제됨");
        deleted.softDelete();
        Sermon saved = repository.saveAndFlush(deleted);

        assertThat(repository.incrementViewCountBy(saved.getId(), 3L)).isZero();
    }

    @Test
    void filter_by_preacher_series_and_date_range() {
        repository.saveAndFlush(Sermon.create("A", "김목사", "시리즈1", "마 5", "b", null, null, LocalDate.of(2026, 1, 10)));
        repository.saveAndFlush(Sermon.create("B", "이목사", "시리즈1", "마 6", "b", null, null, LocalDate.of(2026, 2, 10)));
        repository.saveAndFlush(Sermon.create("C", "김목사", "시리즈2", "마 7", "b", null, null, LocalDate.of(2026, 3, 10)));

        assertThat(repository
                        .findAll(
                                SermonSpecifications.filter("김목사", null, null, null, null, null), PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(2);
        assertThat(repository
                        .findAll(
                                SermonSpecifications.filter(null, "시리즈1", null, null, null, null),
                                PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(2);
        assertThat(repository
                        .findAll(
                                SermonSpecifications.filter(
                                        null, null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), null, null),
                                PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(1);
    }

    @Test
    void filter_q_matches_card_fields_case_insensitively() {
        repository.saveAndFlush(
                Sermon.create("은혜의 강", "김목사", "시리즈1", "마 5", "b", null, null, LocalDate.of(2026, 1, 1)));
        repository.saveAndFlush(
                Sermon.create("믿음", "은혜교 목사", "시리즈2", "마 6", "b", null, null, LocalDate.of(2026, 1, 2)));

        // title 1건 + preacher 1건 = 2건이 "은혜"로 매칭
        assertThat(repository
                        .findAll(SermonSpecifications.filter(null, null, null, null, "은혜", null), PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(2);
    }

    @Test
    void filter_taggedIds_empty_returns_none_and_excludes_deleted() {
        Sermon s = repository.saveAndFlush(
                Sermon.create("A", "김목사", "s", "마 5", "b", null, null, LocalDate.of(2026, 1, 1)));
        Sermon deleted = Sermon.create("D", "김목사", "s", "마 5", "b", null, null, LocalDate.of(2026, 1, 2));
        deleted.softDelete();
        repository.saveAndFlush(deleted);

        assertThat(repository
                        .findAll(
                                SermonSpecifications.filter(null, null, null, null, null, List.of()),
                                PageRequest.of(0, 10))
                        .getTotalElements())
                .isZero();
        assertThat(repository
                        .findAll(
                                SermonSpecifications.filter(null, null, null, null, null, List.of(s.getId())),
                                PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(1);
        // 삭제건은 어떤 필터로도 안 잡힘
        assertThat(repository
                        .findAll(SermonSpecifications.filter(null, null, null, null, null, null), PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(1);
    }
}
