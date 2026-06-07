package com.elipair.church.domain.bulletin;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.common.ContentRef;
import com.elipair.church.global.config.JpaConfig;
import java.time.LocalDate;
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
class BulletinReferenceProviderTest {

    @Autowired
    private BulletinRepository repository;

    private BulletinReferenceProvider provider;

    @BeforeEach
    void init() {
        provider = new BulletinReferenceProvider(repository);
    }

    @Test
    void provider_surfaces_active_bulletin_referencing_media() {
        repository.saveAndFlush(Bulletin.create("2026-06-01 주보", LocalDate.of(2026, 6, 1), 42L));

        List<ContentRef> refs = provider.findReferences(42);

        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).type()).isEqualTo("bulletin");
        assertThat(refs.get(0).title()).isEqualTo("2026-06-01 주보");
    }

    @Test
    void provider_excludes_soft_deleted_bulletin() {
        Bulletin dead = Bulletin.create("삭제된 주보", LocalDate.of(2026, 5, 1), 9L);
        dead.softDelete();
        repository.saveAndFlush(dead);

        assertThat(provider.findReferences(9)).isEmpty();
    }

    @Test
    void provider_empty_when_no_match() {
        repository.saveAndFlush(Bulletin.create("다른 미디어", LocalDate.of(2026, 4, 1), 1L));

        assertThat(provider.findReferences(999)).isEmpty();
    }
}
