package com.elipair.church.domain.visibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.bulletin.Bulletin;
import com.elipair.church.domain.bulletin.BulletinRepository;
import com.elipair.church.domain.event.Event;
import com.elipair.church.domain.event.EventRepository;
import com.elipair.church.domain.media.MediaRepository;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.domain.notice.Notice;
import com.elipair.church.domain.notice.NoticeRepository;
import com.elipair.church.domain.sermon.Sermon;
import com.elipair.church.domain.sermon.SermonRepository;
import com.elipair.church.domain.visibility.dto.VisibilityUpdateRequest;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import com.elipair.testfixture.TwoChurches;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 설교·공지·일정·주보 응답의 서명 주소(#95, 설계 6.2·6.6-7). 메뉴를 교인만으로 닫으면 서명 없는 파일 주소는 404라
 * 응답이 서명 주소를 실어야 한다. 프론트는 mediaUrls에 없는 이미지를 지우므로 본문의 모든 id에 키가 있어야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TwoChurches.class})
@TestPropertySource(properties = "media.access-enforced=true")
class SignedUrlResponseApiTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 1, 2, 3, 4, 5, 6, 7};
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 10, 11, 12, 13};

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
    private VisibilityService visibility;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SermonRepository sermonRepository;

    @Autowired
    private BulletinRepository bulletinRepository;

    @Autowired
    private TwoChurches churches;

    @Autowired
    private JdbcClient jdbc;

    private Long uploaderId;

    @BeforeEach
    void seed() {
        resetToMigrationDefaults();
        uploaderId = memberRepository
                .saveAndFlush(Member.create("01000000901", "업로더", "{enc}", null, null, true, true))
                .getId();
    }

    @AfterEach
    void cleanup() {
        resetToMigrationDefaults();
        noticeRepository.deleteAll();
        eventRepository.deleteAll();
        sermonRepository.deleteAll();
        bulletinRepository.deleteAll();
        mediaRepository.deleteAll();
        memberRepository.deleteAll(memberRepository.findAll());
        jdbc.sql("delete from media where church_id = ?").param(churches.bId()).update();
        jdbc.sql("delete from members where church_id = ?")
                .param(churches.bId())
                .update();
    }

    private void resetToMigrationDefaults() {
        visibility.update(new VisibilityUpdateRequest(
                VisibilityLevel.PUBLIC,
                VisibilityLevel.PUBLIC,
                VisibilityLevel.PUBLIC,
                VisibilityLevel.MEMBERS,
                VisibilityLevel.MEMBERS));
    }

    /** 5개 메뉴 조회 권한을 모두 가진 교인. 메뉴 설정과 무관하게 응답 모양만 본다. */
    private String member() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(uploaderId, "uuid-m", "교인", 100),
                        null,
                        List.of("NOTICE_VIEW", "EVENT_VIEW", "BULLETIN_VIEW", "SERMON_VIEW", "GALLERY_VIEW"));
    }

    private long upload(byte[] bytes, String filename, String contentType) throws Exception {
        String admin = "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(uploaderId, "uuid-admin", "관리자", 1000), null, List.of("MEDIA_MANAGE"));
        String json = mvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", filename, contentType, bytes))
                        .header("Authorization", admin))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private String getJson(String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", member()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private static String body(long... ids) {
        StringBuilder sb = new StringBuilder("본문");
        for (long id : ids) {
            sb.append(" ![](media:").append(id).append(")");
        }
        return sb.toString();
    }

    private static void assertSigned(Map<String, String> urls, long... ids) {
        assertThat(urls).hasSize(ids.length);
        for (long id : ids) {
            assertThat(urls.get(String.valueOf(id)))
                    .startsWith("/api/media/" + id + "?exp=")
                    .contains("&sig=");
        }
    }

    @Test
    void sermon_detail_carries_signed_url_for_every_media_in_content() throws Exception {
        long a = upload(JPEG, "a.jpg", "image/jpeg");
        long b = upload(JPEG, "b.jpg", "image/jpeg");
        Sermon sermon = sermonRepository.saveAndFlush(
                Sermon.create("설교", "목사", null, null, body(a, b, a), null, null, LocalDate.of(2026, 11, 1)));

        Map<String, String> urls = JsonPath.read(getJson("/api/sermons/" + sermon.getId()), "$.mediaUrls");

        assertSigned(urls, a, b);
    }

    @Test
    void notice_detail_and_event_detail_carry_media_urls() throws Exception {
        long a = upload(JPEG, "a.jpg", "image/jpeg");
        Notice notice = noticeRepository.saveAndFlush(Notice.create("공지", body(a), false));
        Event event = eventRepository.saveAndFlush(
                Event.create("일정", body(a), null, LocalDateTime.of(2026, 11, 1, 10, 0), null, false));

        assertSigned(JsonPath.read(getJson("/api/notices/" + notice.getId()), "$.mediaUrls"), a);
        assertSigned(JsonPath.read(getJson("/api/events/" + event.getId()), "$.mediaUrls"), a);
    }

    @Test
    void bulletin_card_and_detail_carry_signed_pdf_url() throws Exception {
        long pdf = upload(PDF, "b.pdf", "application/pdf");
        Bulletin bulletin = bulletinRepository.saveAndFlush(Bulletin.create("주보", LocalDate.of(2026, 11, 1), pdf));
        visibility.update(new VisibilityUpdateRequest(
                VisibilityLevel.PUBLIC,
                VisibilityLevel.PUBLIC,
                VisibilityLevel.MEMBERS,
                VisibilityLevel.MEMBERS,
                VisibilityLevel.MEMBERS));

        String detailUrl = JsonPath.read(getJson("/api/bulletins/" + bulletin.getId()), "$.url");
        List<String> cardUrls = JsonPath.read(getJson("/api/bulletins"), "$.content[*].url");

        assertThat(detailUrl).startsWith("/api/media/" + pdf + "?exp=").contains("&sig=");
        assertThat(cardUrls).hasSize(1);
        assertThat(cardUrls.get(0)).startsWith("/api/media/" + pdf + "?exp=").contains("&sig=");
        // 주보 메뉴가 교인만이면 서명 없는 주소는 닫히고(대조) 응답의 주소로는 열린다
        mvc.perform(get("/api/media/" + pdf)).andExpect(status().isNotFound());
        mvc.perform(get(URI.create(detailUrl)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF));
        mvc.perform(get(URI.create(cardUrls.get(0)))).andExpect(status().isOk());
    }

    @Test
    void signed_url_from_members_only_notice_opens_the_file() throws Exception {
        long a = upload(JPEG, "a.jpg", "image/jpeg");
        Notice notice = noticeRepository.saveAndFlush(Notice.create("공지", body(a), false));
        visibility.update(new VisibilityUpdateRequest(
                VisibilityLevel.MEMBERS,
                VisibilityLevel.PUBLIC,
                VisibilityLevel.PUBLIC,
                VisibilityLevel.MEMBERS,
                VisibilityLevel.MEMBERS));

        Map<String, String> urls = JsonPath.read(getJson("/api/notices/" + notice.getId()), "$.mediaUrls");

        mvc.perform(get("/api/media/" + a)).andExpect(status().isNotFound());
        mvc.perform(get(URI.create(urls.get(String.valueOf(a)))))
                .andExpect(status().isOk())
                .andExpect(content().bytes(JPEG));
    }

    @Test
    void content_without_media_gives_empty_map_not_null() throws Exception {
        Notice notice = noticeRepository.saveAndFlush(Notice.create("공지", "그림 없는 본문", false));
        Event event = eventRepository.saveAndFlush(
                Event.create("일정", null, null, LocalDateTime.of(2026, 11, 1, 10, 0), null, false));
        Sermon sermon = sermonRepository.saveAndFlush(
                Sermon.create("설교", "목사", null, null, null, null, null, LocalDate.of(2026, 11, 1)));

        for (String path : List.of(
                "/api/notices/" + notice.getId(), "/api/events/" + event.getId(), "/api/sermons/" + sermon.getId())) {
            Map<String, String> urls = JsonPath.read(getJson(path), "$.mediaUrls");
            assertThat(urls).as(path).isNotNull().isEmpty();
        }
    }

    @Test
    void media_id_of_another_church_is_not_signed() throws Exception {
        long mine = upload(JPEG, "a.jpg", "image/jpeg");
        long memberB = jdbc.sql("insert into members(uuid, phone, name, password, created_at, church_id)"
                        + " values (gen_random_uuid(), '01055550901', 'B교인', 'p', now(), ?) returning id")
                .param(churches.bId())
                .query(Long.class)
                .single();
        long other = jdbc.sql("insert into media(filename, stored_path, mime_type, size, uploaded_by, created_at,"
                        + " church_id) values ('x.jpg', 'iso/signed/x.jpg', 'image/jpeg', 1, ?, now(), ?) returning id")
                .params(memberB, churches.bId())
                .query(Long.class)
                .single();
        Notice notice = noticeRepository.saveAndFlush(Notice.create("공지", body(mine, other), false));

        Map<String, String> urls = JsonPath.read(getJson("/api/notices/" + notice.getId()), "$.mediaUrls");

        assertThat(urls).containsOnlyKeys(String.valueOf(mine));
    }
}
