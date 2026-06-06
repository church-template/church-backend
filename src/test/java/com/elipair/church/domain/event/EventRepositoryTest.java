package com.elipair.church.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.config.JpaConfig;
import java.time.LocalDateTime;
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
class EventRepositoryTest {

    @Autowired
    private EventRepository repository;

    private Event event(String title, LocalDateTime start, LocalDateTime end) {
        return Event.create(title, "본문", "본당", start, end, false);
    }

    private long countInRange(DateRange range) {
        return repository
                .findAll(EventSpecifications.filter(range, null), PageRequest.of(0, 50))
                .getTotalElements();
    }

    private List<String> titlesInRange(DateRange range) {
        return repository
                .findAll(EventSpecifications.filter(range, null), PageRequest.of(0, 50))
                .map(Event::getTitle)
                .getContent();
    }

    @Test
    void save_populates_audit_columns() {
        Event saved = repository.saveAndFlush(
                event("행사", LocalDateTime.of(2026, 6, 10, 10, 0), LocalDateTime.of(2026, 6, 10, 11, 0)));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.isAllDay()).isFalse();
    }

    @Test
    void findByIdAndDeletedAtIsNull_excludes_soft_deleted() {
        Event active = repository.saveAndFlush(
                event("활성", LocalDateTime.of(2026, 6, 10, 10, 0), LocalDateTime.of(2026, 6, 10, 11, 0)));
        Event deleted = event("삭제", LocalDateTime.of(2026, 6, 11, 10, 0), LocalDateTime.of(2026, 6, 11, 11, 0));
        deleted.softDelete();
        Event savedDeleted = repository.saveAndFlush(deleted);

        assertThat(repository.findByIdAndDeletedAtIsNull(active.getId())).isPresent();
        assertThat(repository.findByIdAndDeletedAtIsNull(savedDeleted.getId())).isEmpty();
    }

    @Test
    void range_includes_event_inside_month_and_excludes_other_months() {
        repository.saveAndFlush(
                event("6월행사", LocalDateTime.of(2026, 6, 10, 10, 0), LocalDateTime.of(2026, 6, 10, 11, 0)));

        assertThat(titlesInRange(DateRange.resolve(2026, 6, null, null))).containsExactly("6월행사");
        assertThat(countInRange(DateRange.resolve(2026, 5, null, null))).isZero();
        assertThat(countInRange(DateRange.resolve(2026, 7, null, null))).isZero();
    }

    @Test
    void multi_day_event_appears_in_every_overlapping_month() {
        repository.saveAndFlush(event("수련회", LocalDateTime.of(2026, 6, 28, 0, 0), LocalDateTime.of(2026, 7, 2, 0, 0)));

        assertThat(titlesInRange(DateRange.resolve(2026, 6, null, null))).containsExactly("수련회");
        assertThat(titlesInRange(DateRange.resolve(2026, 7, null, null))).containsExactly("수련회");
    }

    @Test
    void event_ending_exactly_on_month_boundary_is_not_double_counted() {
        // end_at == 7/1 00:00 (다음 달 시작 경계). end_at 배타라 7월에는 노출되지 않아야 한다(off-by-one 차단).
        repository.saveAndFlush(
                event("경계행사", LocalDateTime.of(2026, 6, 30, 22, 0), LocalDateTime.of(2026, 7, 1, 0, 0)));

        assertThat(titlesInRange(DateRange.resolve(2026, 6, null, null))).containsExactly("경계행사");
        assertThat(countInRange(DateRange.resolve(2026, 7, null, null))).isZero();
    }

    @Test
    void null_end_point_event_matches_by_start_at() {
        // 8/1 00:00 종일/점 이벤트(end_at null). 경계 from(8/1)의 점 이벤트는 8월에 포함되어야 한다.
        repository.saveAndFlush(Event.create("1일종일", "본문", null, LocalDateTime.of(2026, 8, 1, 0, 0), null, true));

        assertThat(titlesInRange(DateRange.resolve(2026, 8, null, null))).containsExactly("1일종일");
        assertThat(countInRange(DateRange.resolve(2026, 7, null, null))).isZero();
    }

    @Test
    void filter_taggedIds_empty_returns_none_and_excludes_deleted() {
        Event a = repository.saveAndFlush(
                event("A", LocalDateTime.of(2026, 6, 1, 10, 0), LocalDateTime.of(2026, 6, 1, 11, 0)));
        Event deleted = event("D", LocalDateTime.of(2026, 6, 2, 10, 0), LocalDateTime.of(2026, 6, 2, 11, 0));
        deleted.softDelete();
        repository.saveAndFlush(deleted);

        assertThat(repository
                        .findAll(EventSpecifications.filter(null, List.of()), PageRequest.of(0, 10))
                        .getTotalElements())
                .isZero();
        assertThat(repository
                        .findAll(EventSpecifications.filter(null, List.of(a.getId())), PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(1);
        assertThat(repository
                        .findAll(EventSpecifications.filter(null, null), PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(1);
    }
}
