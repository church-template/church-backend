package com.elipair.church.domain.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.config.JpaConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class PositionRepositoryTest {

    @Autowired
    private PositionRepository repository;

    @Test
    void findAll_orders_by_sort_order_asc() {
        repository.save(Position.of("장로", 20));
        repository.save(Position.of("목사", 10));

        List<Position> result = repository.findAllByOrderBySortOrderAsc();

        assertThat(result).extracting(Position::getName).containsExactly("목사", "장로");
    }

    @Test
    void existsByName_true_and_false() {
        repository.save(Position.of("권사", 10));

        assertThat(repository.existsByName("권사")).isTrue();
        assertThat(repository.existsByName("없는직분")).isFalse();
    }

    @Test
    void findMaxSortOrder_returns_max_or_empty() {
        assertThat(repository.findMaxSortOrder()).isEqualTo(Optional.empty());

        repository.save(Position.of("목사", 10));
        repository.save(Position.of("장로", 30));

        assertThat(repository.findMaxSortOrder()).contains(30);
    }

    @Test
    void duplicate_name_violates_unique_constraint() {
        repository.saveAndFlush(Position.of("목사", 10));

        assertThatThrownBy(() -> repository.saveAndFlush(Position.of("목사", 20)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditing_populates_created_at() {
        Position saved = repository.saveAndFlush(Position.of("집사", 10));
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
