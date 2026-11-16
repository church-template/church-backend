package com.elipair.church.domain.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.bulletin.Bulletin;
import com.elipair.church.domain.bulletin.BulletinRepository;
import com.elipair.church.domain.gallery.GalleryAlbum;
import com.elipair.church.domain.gallery.GalleryAlbumRepository;
import com.elipair.church.domain.gallery.GalleryPhoto;
import com.elipair.church.domain.gallery.GalleryPhotoRepository;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.domain.notice.Notice;
import com.elipair.church.domain.notice.NoticeRepository;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import com.elipair.testfixture.TwoChurches;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
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

/** 차단 켜짐(media.access-enforced=true) 상태의 공개 판단·서명 주소 서빙. 보안 경계라 응답 계약을 전부 고정한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TwoChurches.class})
@TestPropertySource(properties = "media.access-enforced=true")
class MediaAccessEnforcedApiTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 1, 2, 3, 4, 5, 6, 7};
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 10, 11, 12, 13};

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
    private MediaRepository mediaRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private BulletinRepository bulletinRepository;

    @Autowired
    private GalleryAlbumRepository albumRepository;

    @Autowired
    private GalleryPhotoRepository photoRepository;

    @Autowired
    private MediaUrlSigner signer;

    @Autowired
    private TwoChurches churches;

    private Long uploaderId;

    @BeforeEach
    void seedUploader() {
        uploaderId = memberRepository
                .saveAndFlush(Member.create("01000000001", "업로더", "{enc}", null, null, true, true))
                .getId();
    }

    @AfterEach
    void cleanup() {
        photoRepository.deleteAll();
        albumRepository.deleteAll();
        noticeRepository.deleteAll();
        bulletinRepository.deleteAll();
        mediaRepository.deleteAll();
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private long upload(byte[] bytes, String filename, String contentType) throws Exception {
        String token = "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(uploaderId, "uuid-admin", "관리자", 1000), null, List.of("MEDIA_MANAGE"));
        String json = mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", filename, contentType, bytes))
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private Notice noticeWith(long mediaId) {
        return noticeRepository.saveAndFlush(Notice.create("공지", "본문 ![](media:" + mediaId + ")", false));
    }

    private void inGallery(long mediaId) {
        GalleryAlbum album = albumRepository.saveAndFlush(GalleryAlbum.create("앨범", "설명"));
        photoRepository.saveAndFlush(GalleryPhoto.create(album.getId(), mediaId, 0));
    }

    @Test
    void 공지에_쓰인_미디어는_익명으로_200_public_캐시() throws Exception {
        long id = upload(JPEG, "a.jpg", "image/jpeg");
        noticeWith(id);

        mockMvc.perform(get("/api/media/" + id))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=86400"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(JPEG));
    }

    @Test
    void 주보_PDF는_익명으로_200() throws Exception {
        long id = upload(PDF, "b.pdf", "application/pdf");
        bulletinRepository.saveAndFlush(Bulletin.create("주보", LocalDate.of(2026, 6, 1), id));

        mockMvc.perform(get("/api/media/" + id)).andExpect(status().isOk());
    }

    @Test
    void 갤러리에만_있는_미디어는_차단시_익명_404() throws Exception {
        long id = upload(JPEG, "a.jpg", "image/jpeg");
        inGallery(id);

        mockMvc.perform(get("/api/media/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 갤러리와_공지에_모두_쓰이면_공개() throws Exception {
        long id = upload(JPEG, "a.jpg", "image/jpeg");
        inGallery(id);
        noticeWith(id);

        mockMvc.perform(get("/api/media/" + id)).andExpect(status().isOk());
    }

    @Test
    void 공지에서_빼면_다시_404() throws Exception {
        long id = upload(JPEG, "a.jpg", "image/jpeg");
        Notice notice = noticeWith(id);
        mockMvc.perform(get("/api/media/" + id)).andExpect(status().isOk());

        notice.softDelete();
        noticeRepository.saveAndFlush(notice);

        mockMvc.perform(get("/api/media/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void 서명_주소면_비공개도_200_private_캐시() throws Exception {
        long id = upload(JPEG, "a.jpg", "image/jpeg");
        inGallery(id);

        String cc = mockMvc.perform(get(signer.signedPath(id)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(JPEG))
                .andReturn()
                .getResponse()
                .getHeader("Cache-Control");
        assertThat(cc).matches("private, max-age=(3600|3599)");
    }

    @Test
    void 서명이_틀리거나_만료면_404() throws Exception {
        long id = upload(JPEG, "a.jpg", "image/jpeg");
        inGallery(id);
        String path = signer.signedPath(id);
        long exp = Long.parseLong(path.replaceAll(".*exp=(\\d+)&.*", "$1"));
        String sig = path.replaceAll(".*sig=", "");

        mockMvc.perform(get("/api/media/" + id)
                        .param("exp", String.valueOf(exp))
                        .param("sig", "AAAA"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/media/" + id)
                        .param("exp", String.valueOf(exp + 1))
                        .param("sig", sig))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/media/" + id).param("exp", "1").param("sig", sig))
                .andExpect(status().isNotFound());
        // 하나만 있어도 404
        mockMvc.perform(get("/api/media/" + id).param("exp", String.valueOf(exp)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/media/" + id).param("sig", sig)).andExpect(status().isNotFound());
    }

    @Test
    void 틀린_서명이면_공개_미디어도_404() throws Exception {
        long id = upload(JPEG, "a.jpg", "image/jpeg");
        noticeWith(id);

        mockMvc.perform(get("/api/media/" + id).param("exp", "1").param("sig", "AAAA"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 다른_교회_접두사로_같은_서명_주소면_404() throws Exception {
        long id = upload(JPEG, "a.jpg", "image/jpeg");
        inGallery(id);

        mockMvc.perform(get(churches.bPrefix() + signer.signedPath(id))).andExpect(status().isNotFound());
    }

    @Test
    void 아무데도_안쓰인_미디어는_차단시_404() throws Exception {
        long id = upload(JPEG, "a.jpg", "image/jpeg");

        mockMvc.perform(get("/api/media/" + id)).andExpect(status().isNotFound());
    }
}
