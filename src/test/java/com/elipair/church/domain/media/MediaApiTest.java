package com.elipair.church.domain.media;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.global.common.ContentRef;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MediaApiTest.StubProviderConfig.class})
class MediaApiTest {

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
    private MediaRepository repository;

    @Autowired
    private MemberRepository memberRepository;

    private Long uploaderId;

    @BeforeEach
    void seedUploader() {
        // media.uploaded_by FK → members(id): 업로더 회원을 먼저 만들어 그 id를 토큰 principal에 쓴다.
        Member uploader =
                memberRepository.saveAndFlush(Member.create("01000000000", "업로더", "{enc}", null, null, true, true));
        uploaderId = uploader.getId();
    }

    @AfterEach
    void cleanup() {
        StubProviderConfig.referenced.clear();
        repository.deleteAll(); // media를 members보다 먼저(uploaded_by FK)
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private String admin() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(uploaderId, "uuid-admin", "관리자", 1000), null, List.of("MEDIA_MANAGE"));
    }

    private String otherPermission() {
        return "Bearer "
                + provider.issueAccess(new MemberPrincipal(2L, "uuid-user", "사용자", 100), null, List.of("SERMON_WRITE"));
    }

    private long upload(byte[] bytes, String filename, String contentType) throws Exception {
        String json = mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", filename, contentType, bytes))
                        .header("Authorization", admin()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void upload_as_media_manage_returns_201_with_sniffed_mime() throws Exception {
        mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "photo.jpg", "application/octet-stream", JPEG))
                        .header("Authorization", admin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.filename").value("photo.jpg"))
                .andExpect(jsonPath("$.mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.uploadedBy").value(uploaderId.intValue()));
    }

    @Test
    void upload_anonymous_is_401() throws Exception {
        mockMvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file", "p.jpg", "image/jpeg", JPEG)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void upload_without_permission_is_403() throws Exception {
        mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "p.jpg", "image/jpeg", JPEG))
                        .header("Authorization", otherPermission()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void upload_unsupported_bytes_is_400() throws Exception {
        byte[] notMedia = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
        mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "evil.exe", "image/jpeg", notMedia))
                        .header("Authorization", admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void list_paginates_and_filters_by_type() throws Exception {
        upload(JPEG, "a.jpg", "image/jpeg");
        upload(JPEG, "b.jpg", "image/jpeg");
        upload(PDF, "c.pdf", "application/pdf");

        mockMvc.perform(get("/api/admin/media").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.totalElements").value(3));

        mockMvc.perform(get("/api/admin/media").param("type", "pdf").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].mimeType").value("application/pdf"));
    }

    @Test
    void list_unknown_type_is_400() throws Exception {
        mockMvc.perform(get("/api/admin/media").param("type", "video").header("Authorization", admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void get_single_and_unknown_404() throws Exception {
        long id = upload(JPEG, "a.jpg", "image/jpeg");

        mockMvc.perform(get("/api/admin/media/" + id).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id));

        mockMvc.perform(get("/api/admin/media/999999").header("Authorization", admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void public_serving_returns_bytes_with_nosniff() throws Exception {
        long id = upload(JPEG, "a.jpg", "image/jpeg");

        mockMvc.perform(get("/api/media/" + id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(JPEG));
    }

    @Test
    void public_serving_unknown_id_is_404() throws Exception {
        mockMvc.perform(get("/api/media/999999")).andExpect(status().isNotFound());
    }

    @Test
    void references_empty_when_not_referenced() throws Exception {
        long id = upload(JPEG, "a.jpg", "image/jpeg");

        mockMvc.perform(get("/api/admin/media/" + id + "/references").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inUse").value(false))
                .andExpect(jsonPath("$.references").isEmpty());
    }

    @Test
    void delete_without_references_is_204_and_removes_file() throws Exception {
        long id = upload(JPEG, "a.jpg", "image/jpeg");

        mockMvc.perform(delete("/api/admin/media/" + id).header("Authorization", admin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/media/" + id).header("Authorization", admin()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/media/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void delete_with_references_is_409_with_reference_list() throws Exception {
        long id = upload(JPEG, "a.jpg", "image/jpeg");
        StubProviderConfig.referenced.add(id); // stub provider가 이 id를 참조됨으로 보고

        mockMvc.perform(delete("/api/admin/media/" + id).header("Authorization", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEDIA_IN_USE"))
                .andExpect(jsonPath("$.references").isArray())
                .andExpect(jsonPath("$.references[0].type").value("notice"));

        // 차단됐으므로 여전히 존재해야 한다
        mockMvc.perform(get("/api/admin/media/" + id).header("Authorization", admin()))
                .andExpect(status().isOk());
    }

    /** 차단형 삭제·참조 추적 검증용 stub. referenced에 든 미디어 id만 참조됨으로 보고한다(스펙 §5.10 SPI). */
    @TestConfiguration
    static class StubProviderConfig {
        static final Set<Long> referenced = ConcurrentHashMap.newKeySet();

        @Bean
        MediaReferenceProvider stubProvider() {
            return mediaId ->
                    referenced.contains(mediaId) ? List.of(new ContentRef("notice", 99L, "참조 공지")) : List.of();
        }
    }
}
