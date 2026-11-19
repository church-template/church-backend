package com.elipair.church.domain.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.department.Department;
import com.elipair.church.domain.department.DepartmentRepository;
import com.elipair.church.domain.gallery.GalleryAlbum;
import com.elipair.church.domain.gallery.GalleryAlbumRepository;
import com.elipair.church.domain.gallery.GalleryPhoto;
import com.elipair.church.domain.gallery.GalleryPhotoRepository;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.domain.notice.Notice;
import com.elipair.church.domain.notice.NoticeRepository;
import com.elipair.church.domain.sermon.Sermon;
import com.elipair.church.domain.sermon.SermonRepository;
import com.elipair.church.domain.visibility.ContentDomain;
import com.elipair.church.domain.visibility.VisibilityLevel;
import com.elipair.church.domain.visibility.VisibilityService;
import com.elipair.church.domain.visibility.dto.VisibilityResponse;
import com.elipair.church.domain.visibility.dto.VisibilityUpdateRequest;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 미디어 공개 판단이 메뉴 설정·글 단위 "교인만"을 따른다(#95, 설계 6.2·6.6-2). 차단 켜짐(운영과 같은 상태)에서 본다.
 * 공개 판단(hasPublicReference)만 설정을 보고, 삭제 차단·참조 목록(findReferences)은 설정과 무관하게 모든 참조를 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "media.access-enforced=true")
class MediaPublicRuleTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 1, 2, 3, 4, 5, 6, 7};

    @TempDir
    static Path uploadDir;

    @DynamicPropertySource
    static void fileProps(DynamicPropertyRegistry registry) {
        registry.add("file.upload-dir", () -> uploadDir.toString());
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private MediaService mediaService;

    @Autowired
    private MediaUrlSigner signer;

    @Autowired
    private VisibilityService visibility;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private SermonRepository sermonRepository;

    @Autowired
    private GalleryAlbumRepository albumRepository;

    @Autowired
    private GalleryPhotoRepository photoRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    private Long uploaderId;

    @BeforeEach
    void seed() {
        resetToMigrationDefaults();
        uploaderId = memberRepository
                .saveAndFlush(Member.create("01000000801", "업로더", "{enc}", null, null, true, true))
                .getId();
    }

    @AfterEach
    void cleanup() {
        resetToMigrationDefaults();
        departmentRepository.deleteAll();
        photoRepository.deleteAll();
        albumRepository.deleteAll();
        noticeRepository.deleteAll();
        sermonRepository.deleteAll();
        mediaRepository.deleteAll();
        memberRepository.deleteAll(memberRepository.findAll());
    }

    /** 기존 교회 값(설교·갤러리 MEMBERS) — 같은 컨텍스트를 쓰는 다른 테스트가 보는 기본 교회 상태. */
    private void resetToMigrationDefaults() {
        visibility.update(new VisibilityUpdateRequest(
                VisibilityLevel.PUBLIC,
                VisibilityLevel.PUBLIC,
                VisibilityLevel.PUBLIC,
                VisibilityLevel.MEMBERS,
                VisibilityLevel.MEMBERS));
    }

    private void set(ContentDomain domain, VisibilityLevel level) {
        VisibilityResponse now = visibility.get();
        Map<ContentDomain, VisibilityLevel> next = new EnumMap<>(ContentDomain.class);
        for (ContentDomain d : ContentDomain.values()) {
            next.put(d, d == domain ? level : now.levelOf(d));
        }
        visibility.update(new VisibilityUpdateRequest(
                next.get(ContentDomain.NOTICE),
                next.get(ContentDomain.EVENT),
                next.get(ContentDomain.BULLETIN),
                next.get(ContentDomain.SERMON),
                next.get(ContentDomain.GALLERY)));
    }

    private String admin() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(uploaderId, "uuid-admin", "관리자", 1000), null, List.of("MEDIA_MANAGE"));
    }

    private long upload() throws Exception {
        String json = mvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG))
                        .header("Authorization", admin()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private void expectPublic(long id) throws Exception {
        assertThat(mediaService.isPublic(id)).isTrue();
        mvc.perform(get("/api/media/" + id))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=86400"))
                .andExpect(content().bytes(JPEG));
    }

    private void expectPrivate(long id) throws Exception {
        assertThat(mediaService.isPublic(id)).isFalse();
        mvc.perform(get("/api/media/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    private GalleryAlbum albumWithPhoto(long mediaId, boolean membersOnly) {
        GalleryAlbum album = GalleryAlbum.create("앨범", "설명");
        album.changeMembersOnly(membersOnly);
        album = albumRepository.saveAndFlush(album);
        photoRepository.saveAndFlush(GalleryPhoto.create(album.getId(), mediaId, 0));
        return album;
    }

    private Sermon sermonWith(long mediaId, boolean membersOnly) {
        Sermon sermon = Sermon.create(
                "설교", "목사", null, null, "본문 ![](media:" + mediaId + ")", null, null, LocalDate.of(2026, 11, 1));
        sermon.changeMembersOnly(membersOnly);
        return sermonRepository.saveAndFlush(sermon);
    }

    private void noticeWith(long mediaId) {
        noticeRepository.saveAndFlush(Notice.create("공지", "본문 ![](media:" + mediaId + ")", false));
    }

    @Test
    void photo_in_plain_album_is_public_only_when_gallery_is_public() throws Exception {
        long id = upload();
        albumWithPhoto(id, false);

        set(ContentDomain.GALLERY, VisibilityLevel.PUBLIC);
        expectPublic(id);
        set(ContentDomain.GALLERY, VisibilityLevel.MEMBERS);
        expectPrivate(id);
    }

    @Test
    void photo_in_members_only_album_is_never_public() throws Exception {
        long id = upload();
        long contrast = upload();
        albumWithPhoto(id, true);
        albumWithPhoto(contrast, false);

        set(ContentDomain.GALLERY, VisibilityLevel.PUBLIC);
        expectPrivate(id);
        expectPublic(contrast); // 대조: 같은 조건의 표시 없는 앨범은 공개
    }

    @Test
    void image_in_album_description_follows_gallery_menu_and_members_only() throws Exception {
        long plain = upload();
        long hidden = upload();
        albumRepository.saveAndFlush(GalleryAlbum.create("앨범", "![](media:" + plain + ")"));
        GalleryAlbum membersOnly = GalleryAlbum.create("교인 앨범", "![](media:" + hidden + ")");
        membersOnly.changeMembersOnly(true);
        albumRepository.saveAndFlush(membersOnly);

        set(ContentDomain.GALLERY, VisibilityLevel.PUBLIC);
        expectPublic(plain);
        expectPrivate(hidden);
        set(ContentDomain.GALLERY, VisibilityLevel.MEMBERS);
        expectPrivate(plain);
    }

    @Test
    void image_in_notice_becomes_private_when_notice_menu_is_members() throws Exception {
        long id = upload();
        noticeWith(id);

        expectPublic(id);
        set(ContentDomain.NOTICE, VisibilityLevel.MEMBERS);
        expectPrivate(id);
    }

    @Test
    void image_in_members_only_sermon_is_private_and_plain_sermon_public_when_menu_public() throws Exception {
        long plain = upload();
        long hidden = upload();
        sermonWith(plain, false);
        sermonWith(hidden, true);

        // 기존 교회 기본값(설교 MEMBERS)에서는 둘 다 비공개 — 지금 동작 그대로
        expectPrivate(plain);
        expectPrivate(hidden);

        set(ContentDomain.SERMON, VisibilityLevel.PUBLIC);
        expectPublic(plain);
        expectPrivate(hidden);
    }

    @Test
    void department_media_is_always_public() throws Exception {
        long id = upload();
        Department dept = Department.create("청년부", "설명", null, null, 0);
        dept.changeHeroMedia(id);
        departmentRepository.saveAndFlush(dept);

        // 부서 소개는 메뉴 설정 대상이 아니다 — 5개 메뉴를 모두 MEMBERS로 닫아도 공개
        for (ContentDomain domain : ContentDomain.values()) {
            set(domain, VisibilityLevel.MEMBERS);
        }
        expectPublic(id);
    }

    @Test
    void media_used_by_both_public_notice_and_members_only_album_is_public() throws Exception {
        long id = upload();
        albumWithPhoto(id, true);
        expectPrivate(id); // 대조: 교인 전용 앨범만 쓰면 비공개

        noticeWith(id);
        expectPublic(id);
    }

    @Test
    void media_used_only_by_members_only_content_cannot_be_deleted() throws Exception {
        long id = upload();
        set(ContentDomain.SERMON, VisibilityLevel.PUBLIC);
        set(ContentDomain.GALLERY, VisibilityLevel.PUBLIC);
        sermonWith(id, true);
        albumWithPhoto(id, true);
        expectPrivate(id);

        mvc.perform(delete("/api/admin/media/" + id).header("Authorization", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEDIA_IN_USE"));
        assertThat(mediaRepository.findById(id)).isPresent();

        // 메뉴까지 MEMBERS로 닫아도(공개 근거 0) 삭제는 여전히 막힌다
        set(ContentDomain.SERMON, VisibilityLevel.MEMBERS);
        set(ContentDomain.GALLERY, VisibilityLevel.MEMBERS);
        mvc.perform(delete("/api/admin/media/" + id).header("Authorization", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEDIA_IN_USE"));
    }

    @Test
    void unused_media_can_be_deleted() throws Exception {
        long id = upload(); // 대조: 참조가 없으면 지워진다

        mvc.perform(delete("/api/admin/media/" + id).header("Authorization", admin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void references_listing_still_shows_members_only_content() throws Exception {
        long id = upload();
        Sermon sermon = sermonWith(id, true);
        GalleryAlbum album = albumWithPhoto(id, true);

        mvc.perform(get("/api/admin/media/" + id + "/references").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inUse").value(true))
                .andExpect(jsonPath("$.references.length()").value(2))
                .andExpect(jsonPath("$.references[?(@.type=='sermon')].id")
                        .value(sermon.getId().intValue()))
                .andExpect(jsonPath("$.references[?(@.type=='gallery_photo')].id")
                        .value(album.getId().intValue()));
    }

    @Test
    void signed_url_opens_private_media() throws Exception {
        long id = upload();
        albumWithPhoto(id, true);
        expectPrivate(id);

        mvc.perform(get(signer.signedPath(id)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(JPEG));
    }
}
