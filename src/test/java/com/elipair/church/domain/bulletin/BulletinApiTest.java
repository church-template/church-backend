package com.elipair.church.domain.bulletin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BulletinApiTest {

    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 10, 11, 12, 13};
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
    private BulletinRepository bulletinRepository;

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
        bulletinRepository.deleteAll();
        mediaRepository.deleteAll();
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private String adminToken() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(adminId, "uuid-admin", "관리자", 1000),
                        null,
                        List.of("BULLETIN_WRITE", "MEDIA_MANAGE"));
    }

    private String token(String... authorities) {
        return "Bearer "
                + provider.issueAccess(new MemberPrincipal(adminId, "uuid-x", "사용자", 100), null, List.of(authorities));
    }

    private long uploadPdfMedia() throws Exception {
        String json = mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "c.pdf", "application/pdf", PDF))
                        .header("Authorization", adminToken()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private long createBulletinWithFile() throws Exception {
        String json = mockMvc.perform(multipart("/api/admin/bulletins")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", PDF))
                        .param("title", "2026-06-01 주보")
                        .param("serviceDate", "2026-06-01")
                        .header("Authorization", adminToken()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    // ---- 공개 조회 (스펙 §5.13) ----

    @Test
    void list_is_public_and_omits_no_body() throws Exception {
        createBulletinWithFile();
        mockMvc.perform(get("/api/bulletins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("2026-06-01 주보"))
                .andExpect(jsonPath("$.content[0].serviceDate").value("2026-06-01"))
                .andExpect(jsonPath("$.content[0].mediaId").exists())
                .andExpect(jsonPath("$.content[0].author").value("관리목사"));
    }

    @Test
    void get_is_public() throws Exception {
        long id = createBulletinWithFile();
        mockMvc.perform(get("/api/bulletins/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.mediaId").exists())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void get_unknown_is_404() throws Exception {
        mockMvc.perform(get("/api/bulletins/999999")).andExpect(status().isNotFound());
    }

    // ---- 인가 ----

    @Test
    void create_without_bulletin_write_is_403() throws Exception {
        mockMvc.perform(multipart("/api/admin/bulletins")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", PDF))
                        .param("title", "주보")
                        .param("serviceDate", "2026-06-01")
                        .header("Authorization", token("MEDIA_MANAGE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ---- 생성 ----

    @Test
    void create_with_file_returns_201_with_author_and_version() throws Exception {
        mockMvc.perform(multipart("/api/admin/bulletins")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", PDF))
                        .param("title", "부활절 주보")
                        .param("serviceDate", "2026-04-05")
                        .header("Authorization", adminToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("부활절 주보"))
                .andExpect(jsonPath("$.serviceDate").value("2026-04-05"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.author").value("관리목사"));
    }

    @Test
    void create_with_existing_mediaId_returns_201() throws Exception {
        long mediaId = uploadPdfMedia();
        mockMvc.perform(multipart("/api/admin/bulletins")
                        .param("title", "기존 PDF 주보")
                        .param("serviceDate", "2026-06-08")
                        .param("mediaId", String.valueOf(mediaId))
                        .header("Authorization", adminToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaId").value((int) mediaId));
    }

    @Test
    void create_with_both_file_and_mediaId_is_400() throws Exception {
        long mediaId = uploadPdfMedia();
        mockMvc.perform(multipart("/api/admin/bulletins")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", PDF))
                        .param("title", "주보")
                        .param("serviceDate", "2026-06-01")
                        .param("mediaId", String.valueOf(mediaId))
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_with_neither_file_nor_mediaId_is_400() throws Exception {
        mockMvc.perform(multipart("/api/admin/bulletins")
                        .param("title", "주보")
                        .param("serviceDate", "2026-06-01")
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_with_blank_title_is_400() throws Exception {
        mockMvc.perform(multipart("/api/admin/bulletins")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", PDF))
                        .param("title", "   ")
                        .param("serviceDate", "2026-06-01")
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_missing_serviceDate_is_400() throws Exception {
        mockMvc.perform(multipart("/api/admin/bulletins")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", PDF))
                        .param("title", "주보")
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_with_non_pdf_file_is_400() throws Exception {
        mockMvc.perform(multipart("/api/admin/bulletins")
                        .file(new MockMultipartFile("file", "p.jpg", "application/pdf", JPEG))
                        .param("title", "주보")
                        .param("serviceDate", "2026-06-01")
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_with_image_mediaId_is_400() throws Exception {
        long imageId = ((Number) JsonPath.read(
                        mockMvc.perform(multipart("/api/admin/media")
                                        .file(new MockMultipartFile("file", "p.jpg", "image/jpeg", JPEG))
                                        .header("Authorization", adminToken()))
                                .andReturn()
                                .getResponse()
                                .getContentAsString(StandardCharsets.UTF_8),
                        "$.id"))
                .longValue();

        mockMvc.perform(multipart("/api/admin/bulletins")
                        .param("title", "주보")
                        .param("serviceDate", "2026-06-01")
                        .param("mediaId", String.valueOf(imageId))
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    // ---- 수정 (PATCH multipart) ----

    @Test
    void patch_metadata_bumps_version_then_stale_is_409() throws Exception {
        long id = createBulletinWithFile();
        mockMvc.perform(multipart("/api/admin/bulletins/" + id)
                        .param("version", "0")
                        .param("title", "수정된 주보")
                        .header("Authorization", adminToken())
                        .with(req -> {
                            req.setMethod("PATCH");
                            return req;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 주보"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(multipart("/api/admin/bulletins/" + id)
                        .param("version", "0")
                        .param("title", "또수정")
                        .header("Authorization", adminToken())
                        .with(req -> {
                            req.setMethod("PATCH");
                            return req;
                        }))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    // ---- 삭제 + 미디어 차단삭제/캐스케이드 (설계 §2.1, §8) ----

    @Test
    void delete_soft_deletes_then_detail_404() throws Exception {
        long id = createBulletinWithFile();
        mockMvc.perform(delete("/api/admin/bulletins/" + id).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/bulletins/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void media_delete_blocked_by_active_bulletin_409() throws Exception {
        long mediaId = uploadPdfMedia();
        mockMvc.perform(multipart("/api/admin/bulletins")
                        .param("title", "주보")
                        .param("serviceDate", "2026-06-01")
                        .param("mediaId", String.valueOf(mediaId))
                        .header("Authorization", adminToken()))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/admin/media/" + mediaId).header("Authorization", adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEDIA_IN_USE"))
                .andExpect(jsonPath("$.references[0].type").value("bulletin"));
    }

    @Test
    void media_deletable_after_bulletin_soft_deleted_and_fk_set_null() throws Exception {
        long mediaId = uploadPdfMedia();
        String json = mockMvc.perform(multipart("/api/admin/bulletins")
                        .param("title", "주보")
                        .param("serviceDate", "2026-06-01")
                        .param("mediaId", String.valueOf(mediaId))
                        .header("Authorization", adminToken()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        long bulletinId = ((Number) JsonPath.read(json, "$.id")).longValue();

        // 주보 soft-delete → 더 이상 차단 참조 아님
        mockMvc.perform(delete("/api/admin/bulletins/" + bulletinId).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        // media 하드삭제 성공(FK 위반 없음 — ON DELETE SET NULL)
        mockMvc.perform(delete("/api/admin/media/" + mediaId).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        // soft-deleted 주보의 media_id가 null로 정리됨(fresh read — L1 stale 아님)
        Bulletin dead = bulletinRepository.findById(bulletinId).orElseThrow();
        assertThat(dead.getMediaId()).isNull();
    }
}
