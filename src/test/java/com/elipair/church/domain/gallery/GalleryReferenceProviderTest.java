package com.elipair.church.domain.gallery;

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
class GalleryReferenceProviderTest {

    @Autowired
    private GalleryAlbumRepository albumRepository;

    @Autowired
    private GalleryPhotoRepository photoRepository;

    private GalleryAlbumReferenceProvider albumProvider;
    private GalleryPhotoReferenceProvider photoProvider;

    @BeforeEach
    void init() {
        albumProvider = new GalleryAlbumReferenceProvider(albumRepository);
        photoProvider = new GalleryPhotoReferenceProvider(photoRepository);
    }

    @Test
    void album_provider_matches_body_boundary_safe() {
        albumRepository.saveAndFlush(GalleryAlbum.create("42참조", "본문 ![](media:42) 끝"));
        albumRepository.saveAndFlush(GalleryAlbum.create("420참조", "본문 ![](media:420) 끝"));

        List<ContentRef> refs = albumProvider.findReferences(42);
        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).type()).isEqualTo("gallery_album");
        assertThat(refs.get(0).title()).isEqualTo("42참조");
    }

    @Test
    void album_provider_excludes_soft_deleted() {
        GalleryAlbum deleted = GalleryAlbum.create("삭제", "![](media:9)");
        deleted.softDelete();
        albumRepository.saveAndFlush(deleted);
        assertThat(albumProvider.findReferences(9)).isEmpty();
    }

    @Test
    void photo_provider_surfaces_owning_album() {
        Long albumId =
                albumRepository.saveAndFlush(GalleryAlbum.create("사진앨범", "본문")).getId();
        photoRepository.saveAndFlush(GalleryPhoto.create(albumId, 42L, 0));

        List<ContentRef> refs = photoProvider.findReferences(42);
        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).type()).isEqualTo("gallery_photo");
        assertThat(refs.get(0).title()).isEqualTo("사진앨범");
    }

    @Test
    void photo_provider_empty_when_album_deleted() {
        GalleryAlbum dead = GalleryAlbum.create("죽은앨범", "본문");
        dead.softDelete();
        Long deadId = albumRepository.saveAndFlush(dead).getId();
        photoRepository.saveAndFlush(GalleryPhoto.create(deadId, 55L, 0));

        assertThat(photoProvider.findReferences(55)).isEmpty();
    }
}
