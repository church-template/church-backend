package com.elipair.church.domain.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.config.JpaConfig;
import java.util.List;
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
class TagRepositoryTest {

    @Autowired
    private TagRepository repository;

    @Test
    void findAll_orders_by_name_asc() {
        repository.save(Tag.create("선교"));
        repository.save(Tag.create("봉사"));
        repository.save(Tag.create("예배"));

        List<Tag> result = repository.findAllByOrderByNameAsc();

        assertThat(result).extracting(Tag::getName).containsExactly("봉사", "선교", "예배");
    }

    @Test
    void existsByName_true_and_false() {
        repository.save(Tag.create("예배"));

        assertThat(repository.existsByName("예배")).isTrue();
        assertThat(repository.existsByName("없는태그")).isFalse();
    }

    @Test
    void existsByNameAndIdNot_excludes_self() {
        Tag saved = repository.save(Tag.create("예배"));

        assertThat(repository.existsByNameAndIdNot("예배", saved.getId())).isFalse();
        assertThat(repository.existsByNameAndIdNot("예배", saved.getId() + 1)).isTrue();
    }

    @Test
    void duplicate_name_violates_unique_constraint() {
        repository.saveAndFlush(Tag.create("예배"));

        assertThatThrownBy(() -> repository.saveAndFlush(Tag.create("예배")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditing_populates_created_at() {
        Tag saved = repository.saveAndFlush(Tag.create("예배"));
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
