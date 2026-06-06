package com.elipair.church.domain.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.common.ContentRef;
import com.elipair.church.global.config.JpaConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class NoticeReferenceProviderTest {

    @Autowired
    private NoticeRepository repository;

    private NoticeReferenceProvider provider;

    @BeforeEach
    void init() {
        provider = new NoticeReferenceProvider(repository);
    }

    private Notice withBody(String title, String body) {
        return Notice.create(title, body, false);
    }

    @Test
    void matches_exact_id_not_prefix_collision() {
        repository.saveAndFlush(withBody("42참조", "본문 ![](media:42) 끝"));
        repository.saveAndFlush(withBody("420참조", "본문 ![](media:420) 끝"));

        List<ContentRef> refs = provider.findReferences(42);

        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).type()).isEqualTo("notice");
        assertThat(refs.get(0).title()).isEqualTo("42참조");
    }

    @Test
    void matches_when_id_at_end_of_body() {
        repository.saveAndFlush(withBody("끝참조", "마지막 이미지 media:7"));

        assertThat(provider.findReferences(7)).hasSize(1);
    }

    @Test
    void excludes_soft_deleted() {
        Notice deleted = withBody("삭제", "![](media:9)");
        deleted.softDelete();
        repository.saveAndFlush(deleted);

        assertThat(provider.findReferences(9)).isEmpty();
    }

    @Test
    void no_reference_returns_empty() {
        repository.saveAndFlush(withBody("무관", "그림 없음"));

        assertThat(provider.findReferences(1)).isEmpty();
    }
}
