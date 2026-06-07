package com.elipair.church.domain.gallery;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GalleryApiTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 1, 2, 3, 4, 5, 6, 7};

    @TempDir
    static Path uploadDir;

    @DynamicPropertySource
    static void fileProps(DynamicPropertyRegistry registry) {
        registry.add("file.upload-dir", () -> uploadDir.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private GalleryAlbumRepository albumRepository;

    @Autowired
    private GalleryPhotoRepository photoRepository;

    @Autowired
    private com.elipair.church.domain.media.MediaRepository mediaRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Long adminId;

    @BeforeEach
    void seed() {
        Member admin =
                memberRepository.saveAndFlush(Member.create("01000000000", "관리목사", "{enc}", null, null, true, true));
        adminId = admin.getId();
    }

    @AfterEach
    void cleanup() {
        photoRepository.deleteAll();
        albumRepository.deleteAll();
        mediaRepository.deleteAll();
        memberRepository.deleteAll(memberRepository.findAll());
    }

    /** 관리자 토큰: 앨범 작성·읽기·미디어 삭제까지 한 토큰으로(통합 시나리오 편의). */
    private String adminToken() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(adminId, "uuid-admin", "관리자", 1000),
                        null,
                        List.of("GALLERY_WRITE", "GALLERY_VIEW", "MEDIA_MANAGE"));
    }

    private String token(String... authorities) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(adminId, "uuid-x", "사용자", 100), null, List.of(authorities));
    }

    private long createAlbum(String title, String description) throws Exception {
        String json = mockMvc.perform(post("/api/admin/gallery/albums")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"%s","tagIds":[]}
                                """.formatted(title, description)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private long uploadMedia() throws Exception {
        String json = mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "p.jpg", "image/jpeg", JPEG))
                        .header("Authorization", adminToken()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    // ---- 인가(스펙 §5.12 회원 전용 조회) ----

    @Test
    void list_anonymous_is_401() throws Exception {
        mockMvc.perform(get("/api/gallery/albums"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void list_plain_user_without_gallery_view_is_403() throws Exception {
        mockMvc.perform(get("/api/gallery/albums").header("Authorization", token("SERMON_WRITE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void list_member_with_gallery_view_is_200() throws Exception {
        createAlbum("부활절", "본문");
        mockMvc.perform(get("/api/gallery/albums").header("Authorization", token("GALLERY_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").doesNotExist());
    }

    @Test
    void create_without_gallery_write_is_403() throws Exception {
        mockMvc.perform(post("/api/admin/gallery/albums")
                        .header("Authorization", token("GALLERY_VIEW"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"x","description":"y","tagIds":[]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ---- 앨범 CRUD ----

    @Test
    void create_returns_201_with_author_and_version() throws Exception {
        mockMvc.perform(post("/api/admin/gallery/albums")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"여름수련회","description":"본문 ![](media:1)","tagIds":[]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("여름수련회"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.author").value("관리목사"))
                .andExpect(jsonPath("$.photos").isArray());
    }

    @Test
    void patch_bumps_version_then_stale_is_409() throws Exception {
        long id = createAlbum("원본", "본문");
        mockMvc.perform(patch("/api/admin/gallery/albums/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"수정","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(patch("/api/admin/gallery/albums/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"또수정","version":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void delete_soft_deletes_then_detail_404() throws Exception {
        long id = createAlbum("삭제대상", "본문");
        mockMvc.perform(delete("/api/admin/gallery/albums/" + id).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/gallery/albums/" + id).header("Authorization", token("GALLERY_VIEW")))
                .andExpect(status().isNotFound());
    }

    // ---- 사진 추가/해제 (multipart 혼합) ----

    @Test
    void add_photos_via_existing_media_and_upload_then_thumbnail_is_first() throws Exception {
        long albumId = createAlbum("앨범", "본문");
        long existingMedia = uploadMedia();

        mockMvc.perform(multipart("/api/admin/gallery/albums/" + albumId + "/photos")
                        .file(new MockMultipartFile("files", "new.jpg", "image/jpeg", JPEG))
                        .param("mediaIds", String.valueOf(existingMedia))
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()").value(2))
                .andExpect(jsonPath("$.photos[0].mediaId").value((int) existingMedia))
                .andExpect(jsonPath("$.photos[0].sortOrder").value(0))
                .andExpect(jsonPath("$.photos[1].sortOrder").value(1));

        mockMvc.perform(get("/api/gallery/albums").header("Authorization", token("GALLERY_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].thumbnailMediaId").value((int) existingMedia))
                .andExpect(jsonPath("$.content[0].photoCount").value(2));
    }

    @Test
    void add_photos_rejects_non_image_existing_media_400() throws Exception {
        long albumId = createAlbum("앨범", "본문");
        long pdfId = mediaRepository
                .saveAndFlush(
                        com.elipair.church.domain.media.Media.create("b.pdf", "p/b.pdf", "application/pdf", 1L, adminId))
                .getId();

        mockMvc.perform(multipart("/api/admin/gallery/albums/" + albumId + "/photos")
                        .param("mediaIds", String.valueOf(pdfId))
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void remove_photo_unlinks_but_keeps_media() throws Exception {
        long albumId = createAlbum("앨범", "본문");
        long media = uploadMedia();
        String json = mockMvc.perform(multipart("/api/admin/gallery/albums/" + albumId + "/photos")
                        .param("mediaIds", String.valueOf(media))
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        int photoId = JsonPath.read(json, "$.photos[0].id");

        mockMvc.perform(delete("/api/admin/gallery/photos/" + photoId).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/media/" + media)).andExpect(status().isOk());
    }

    // ---- 미디어 차단삭제 2경로 + FK 안전(설계 Critical) ----

    @Test
    void media_delete_blocked_by_photo_fk_409() throws Exception {
        long albumId = createAlbum("앨범", "본문");
        long media = uploadMedia();
        mockMvc.perform(multipart("/api/admin/gallery/albums/" + albumId + "/photos")
                        .param("mediaIds", String.valueOf(media))
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/media/" + media).header("Authorization", adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEDIA_IN_USE"))
                .andExpect(jsonPath("$.references[0].type").value("gallery_photo"));
    }

    @Test
    void media_delete_blocked_by_album_body_409() throws Exception {
        long media = uploadMedia();
        createAlbum("본문참조앨범", "사진 ![](media:" + media + ") 끝");

        mockMvc.perform(delete("/api/admin/media/" + media).header("Authorization", adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEDIA_IN_USE"))
                .andExpect(jsonPath("$.references[0].type").value("gallery_album"));
    }

    @Test
    void media_deletable_after_album_deleted_no_fk_violation() throws Exception {
        long albumId = createAlbum("앨범", "본문");
        long media = uploadMedia();
        mockMvc.perform(multipart("/api/admin/gallery/albums/" + albumId + "/photos")
                        .param("mediaIds", String.valueOf(media))
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/gallery/albums/" + albumId).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/media/" + media).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
    }

    // ---- 태그 필터 ----

    @Test
    void list_filters_by_tag() throws Exception {
        createAlbum("무태그", "본문");
        mockMvc.perform(get("/api/gallery/albums")
                        .param("tagId", "999999")
                        .header("Authorization", token("GALLERY_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }
}
