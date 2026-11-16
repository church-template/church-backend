package com.elipair.church.domain.department;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.gallery.GalleryAlbum;
import com.elipair.church.domain.gallery.GalleryAlbumRepository;
import com.elipair.church.domain.gallery.GalleryPhoto;
import com.elipair.church.domain.gallery.GalleryPhotoRepository;
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
class DepartmentReferenceProviderTest {

    @Autowired
    private DepartmentRepository repository;

    @Autowired
    private GalleryAlbumRepository albumRepository;

    @Autowired
    private GalleryPhotoRepository photoRepository;

    private DepartmentReferenceProvider provider;

    @BeforeEach
    void init() {
        provider = new DepartmentReferenceProvider(repository);
    }

    private Department withBody(String name, String body) {
        return Department.create(name, body, "김목사", null, 10);
    }

    @Test
    void matches_exact_id_not_prefix_collision() {
        repository.saveAndFlush(withBody("42참조", "본문 ![](media:42) 끝"));
        repository.saveAndFlush(withBody("420참조", "본문 ![](media:420) 끝"));

        List<ContentRef> refs = provider.findReferences(42);

        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).type()).isEqualTo("department");
        assertThat(refs.get(0).title()).isEqualTo("42참조"); // title = name
    }

    @Test
    void matches_when_id_at_end_of_body() {
        repository.saveAndFlush(withBody("끝참조", "마지막 이미지 media:7"));

        assertThat(provider.findReferences(7)).hasSize(1);
    }

    @Test
    void excludes_soft_deleted() {
        Department deleted = withBody("삭제", "![](media:9)");
        deleted.softDelete();
        repository.saveAndFlush(deleted);

        assertThat(provider.findReferences(9)).isEmpty();
    }

    @Test
    void no_reference_returns_empty() {
        repository.saveAndFlush(withBody("무관", "그림 없음"));

        assertThat(provider.findReferences(1)).isEmpty();
    }

    private Department saved(String name, String body, Long heroMediaId, Long albumId) {
        Department d = withBody(name, body);
        d.changeHeroMedia(heroMediaId);
        d.changeAlbum(albumId);
        return repository.saveAndFlush(d);
    }

    private Long albumWithPhoto(long mediaId, boolean deleted) {
        GalleryAlbum album = GalleryAlbum.create("앨범", "본문");
        if (deleted) {
            album.softDelete();
        }
        Long albumId = albumRepository.saveAndFlush(album).getId();
        photoRepository.saveAndFlush(GalleryPhoto.create(albumId, mediaId, 10));
        return albumId;
    }

    @Test
    void 대표_사진으로_쓰면_부서_참조로_잡힌다() {
        Department d = saved("대표", "그림 없음", 5L, null);

        assertThat(provider.findReferences(5)).containsExactly(new ContentRef("department", d.getId(), "대표"));
    }

    @Test
    void 연결_앨범의_사진도_부서_참조로_잡힌다() {
        Department d = saved("앨범부서", "그림 없음", null, albumWithPhoto(6L, false));

        assertThat(provider.findReferences(6)).containsExactly(new ContentRef("department", d.getId(), "앨범부서"));
    }

    @Test
    void 본문과_대표_사진이_겹쳐도_한_건이다() {
        saved("겹침", "![](media:8)", 8L, null);

        assertThat(provider.findReferences(8)).hasSize(1);
    }

    @Test
    void 삭제된_앨범의_사진은_부서_참조가_아니다() {
        saved("삭제앨범", "그림 없음", null, albumWithPhoto(9L, true));

        assertThat(provider.findReferences(9)).isEmpty();
    }
}
