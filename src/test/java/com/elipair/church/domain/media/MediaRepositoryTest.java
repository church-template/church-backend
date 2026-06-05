package com.elipair.church.domain.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.config.JpaConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class MediaRepositoryTest {

    @Autowired
    private MediaRepository repository;

    private Media image(String name) {
        return Media.create(name, "2026/06/" + name, "image/jpeg", 100L, 1L);
    }

    private Media pdf(String name) {
        return Media.create(name, "2026/06/" + name, "application/pdf", 200L, 1L);
    }

    @Test
    void save_populates_created_at() {
        Media saved = repository.saveAndFlush(image("a.jpg"));
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void search_filters_by_mime_prefix() {
        repository.save(image("a.jpg"));
        repository.save(image("b.jpg"));
        repository.save(pdf("c.pdf"));

        Page<Media> images = repository.search("image/%", null, null, PageRequest.of(0, 10));
        Page<Media> pdfs = repository.search("application/pdf", null, null, PageRequest.of(0, 10));

        assertThat(images.getTotalElements()).isEqualTo(2);
        assertThat(pdfs.getTotalElements()).isEqualTo(1);
        assertThat(pdfs.getContent().get(0).getMimeType()).isEqualTo("application/pdf");
    }

    @Test
    void search_null_filters_returns_all() {
        repository.save(image("a.jpg"));
        repository.save(pdf("c.pdf"));

        Page<Media> all = repository.search(null, null, null, PageRequest.of(0, 10));

        assertThat(all.getTotalElements()).isEqualTo(2);
    }

    @Test
    void search_filters_by_date_range() {
        repository.save(image("a.jpg"));
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);

        assertThat(repository
                        .search(null, yesterday, tomorrow, PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(1);
        assertThat(repository
                        .search(null, tomorrow, null, PageRequest.of(0, 10))
                        .getTotalElements())
                .isZero();
        assertThat(repository
                        .search(null, null, yesterday, PageRequest.of(0, 10))
                        .getTotalElements())
                .isZero();
    }

    @Test
    void search_paginates() {
        repository.save(image("a.jpg"));
        repository.save(image("b.jpg"));
        repository.save(image("c.jpg"));

        Page<Media> firstPage = repository.search(null, null, null, PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(2);
    }
}
