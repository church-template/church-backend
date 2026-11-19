package com.elipair.church.domain.gallery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.department.DepartmentRepository;
import com.elipair.church.domain.media.MediaRepository;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.domain.visibility.VisibilityLevel;
import com.elipair.church.domain.visibility.VisibilityService;
import com.elipair.church.domain.visibility.dto.VisibilityUpdateRequest;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 앨범 하나의 "교인만"과 부서 연결 제한(#95, 설계 5.1·6.2·6.6-8). 갤러리 메뉴를 PUBLIC으로 열고 시작한다.
 * 부서 소개는 연결 앨범 사진을 누구에게나 보여 주므로, 부서에 연결된 앨범과 교인만 앨범은 양쪽에서 서로를 막는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GalleryVisibilityApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private VisibilityService visibility;

    @Autowired
    private GalleryAlbumRepository albumRepository;

    @Autowired
    private GalleryPhotoRepository photoRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcClient jdbc;

    private Long memberId;
    private long plain;
    private long membersOnly;

    @BeforeEach
    void seed() {
        setGalleryMenu(VisibilityLevel.PUBLIC);
        memberId = memberRepository
                .saveAndFlush(Member.create("01000000096", "관리목사", "{enc}", null, null, true, true))
                .getId();
        plain = insertAlbum("공개앨범", false);
        membersOnly = insertAlbum("교인앨범", true);
    }

    @AfterEach
    void cleanup() {
        setGalleryMenu(VisibilityLevel.MEMBERS); // 기존 교회 기본값으로 되돌린다
        departmentRepository.deleteAllInBatch();
        photoRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        mediaRepository.deleteAllInBatch();
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private void setGalleryMenu(VisibilityLevel gallery) {
        visibility.update(new VisibilityUpdateRequest(
                VisibilityLevel.PUBLIC,
                VisibilityLevel.PUBLIC,
                VisibilityLevel.PUBLIC,
                VisibilityLevel.MEMBERS,
                gallery));
    }

    private long insertAlbum(String title, boolean flag) {
        return jdbc.sql("insert into gallery_albums(title, created_at, church_id, members_only)"
                        + " values (?, now(), 1, ?) returning id")
                .param(title)
                .param(flag)
                .query(Long.class)
                .single();
    }

    private long insertDepartment(long albumId, boolean deleted) {
        return jdbc.sql("insert into departments(name, album_id, created_at, church_id, deleted_at)"
                        + " values ('부서', ?, now(), 1, " + (deleted ? "now()" : "null") + ") returning id")
                .param(albumId)
                .query(Long.class)
                .single();
    }

    private void addPhoto(long albumId) {
        long mediaId = jdbc.sql("insert into media(filename, stored_path, mime_type, size, uploaded_by, created_at,"
                        + " church_id) values ('p.jpg', 'gv/' || gen_random_uuid(), 'image/jpeg', 1, ?, now(), 1)"
                        + " returning id")
                .param(memberId)
                .query(Long.class)
                .single();
        jdbc.sql("insert into gallery_photos(album_id, media_id, sort_order, created_at, church_id)"
                        + " values (?, ?, 1, now(), 1)")
                .param(albumId)
                .param(mediaId)
                .update();
    }

    private String token(String... permissions) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(memberId, "uuid-" + memberId, "관리자", 1000), null, List.of(permissions));
    }

    private String viewer() {
        return token("GALLERY_VIEW");
    }

    private String writer() {
        return token("GALLERY_WRITE", "GALLERY_VIEW");
    }

    private ResultActions patchAlbum(long id, String json) throws Exception {
        return mvc.perform(patch("/api/admin/gallery/albums/" + id)
                .header("Authorization", writer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void anonymous_list_omits_members_only_albums() throws Exception {
        mvc.perform(get("/api/gallery/albums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("공개앨범"));
    }

    @Test
    void logged_in_without_gallery_view_list_omits_members_only_albums() throws Exception {
        mvc.perform(get("/api/gallery/albums").header("Authorization", token("MEDIA_MANAGE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("공개앨범"));
    }

    @Test
    void flag_survives_menu_round_trip() throws Exception {
        setGalleryMenu(VisibilityLevel.MEMBERS);
        setGalleryMenu(VisibilityLevel.PUBLIC);
        mvc.perform(get("/api/gallery/albums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get("/api/gallery/albums/" + membersOnly).header("Authorization", viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersOnly").value(true));
    }

    @Test
    void member_list_includes_members_only_albums_with_flag() throws Exception {
        mvc.perform(get("/api/gallery/albums").header("Authorization", viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.membersOnly == true)]", hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.membersOnly == true)].title").value("교인앨범"));
    }

    @Test
    void anonymous_detail_of_members_only_album_is_401() throws Exception {
        mvc.perform(get("/api/gallery/albums/" + membersOnly))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
        mvc.perform(get("/api/gallery/albums/" + membersOnly).header("Authorization", token("SERMON_VIEW")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        // 대조: 교인은 연다
        mvc.perform(get("/api/gallery/albums/" + membersOnly).header("Authorization", viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersOnly").value(true));
    }

    @Test
    void anonymous_detail_of_plain_album_is_200_with_signed_photo_urls() throws Exception {
        addPhoto(plain);
        mvc.perform(get("/api/gallery/albums/" + plain))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersOnly").value(false))
                .andExpect(jsonPath("$.departmentLinked").value(false))
                .andExpect(jsonPath("$.photos", hasSize(1)))
                .andExpect(jsonPath("$.photos[0].url", containsString("sig=")));
    }

    @Test
    void members_only_filter_is_ignored_without_gallery_write() throws Exception {
        mvc.perform(get("/api/gallery/albums").param("membersOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("공개앨범"));
        mvc.perform(get("/api/gallery/albums").param("membersOnly", "true").header("Authorization", viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void members_only_filter_works_for_gallery_write() throws Exception {
        mvc.perform(get("/api/gallery/albums").param("membersOnly", "true").header("Authorization", writer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("교인앨범"));
    }

    @Test
    void album_linked_to_department_cannot_become_members_only() throws Exception {
        insertDepartment(plain, false);
        patchAlbum(plain, "{\"membersOnly\":true,\"version\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.detail").value("부서 소개에 쓰이는 앨범은 교인 전용으로 바꿀 수 없습니다"));
        // 대조: 연결된 앨범도 다른 수정은 된다
        patchAlbum(plain, "{\"title\":\"제목만\",\"version\":0}").andExpect(status().isOk());
    }

    @Test
    void members_only_album_cannot_be_linked_to_department() throws Exception {
        String dept = "{\"name\":\"청년부\",\"albumId\":%d}";
        mvc.perform(post("/api/admin/departments")
                        .header("Authorization", token("DEPT_WRITE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dept.formatted(membersOnly)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.detail").value("교인 전용 앨범은 부서에 연결할 수 없습니다"));
        // 대조: 표시 없는 앨범은 연결된다
        mvc.perform(post("/api/admin/departments")
                        .header("Authorization", token("DEPT_WRITE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dept.formatted(plain)))
                .andExpect(status().isCreated());
    }

    @Test
    void unlinking_department_then_setting_members_only_succeeds() throws Exception {
        long dept = insertDepartment(plain, false);
        patchAlbum(plain, "{\"membersOnly\":true,\"version\":0}").andExpect(status().isBadRequest());

        jdbc.sql("update departments set album_id = null where id = ?")
                .param(dept)
                .update();
        patchAlbum(plain, "{\"membersOnly\":true,\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersOnly").value(true))
                .andExpect(jsonPath("$.departmentLinked").value(false));
    }

    @Test
    void card_reports_department_linked_true_only_for_linked_album() throws Exception {
        insertDepartment(plain, false);
        mvc.perform(get("/api/gallery/albums").header("Authorization", viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + plain + ")].departmentLinked")
                        .value(true))
                .andExpect(jsonPath("$.content[?(@.id == " + membersOnly + ")].departmentLinked")
                        .value(false));
        mvc.perform(get("/api/gallery/albums/" + plain))
                .andExpect(jsonPath("$.departmentLinked").value(true));
    }

    @Test
    void album_of_soft_deleted_department_counts_as_not_linked() throws Exception {
        insertDepartment(plain, true);
        mvc.perform(get("/api/gallery/albums/" + plain))
                .andExpect(jsonPath("$.departmentLinked").value(false));
        patchAlbum(plain, "{\"membersOnly\":true,\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersOnly").value(true));
    }

    @Test
    void create_without_field_defaults_to_false_and_patch_null_keeps_value() throws Exception {
        String created = body(mvc.perform(post("/api/admin/gallery/albums")
                        .header("Authorization", writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"새앨범\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.membersOnly").value(false)));
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        patchAlbum(id, "{\"membersOnly\":true,\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersOnly").value(true));
        patchAlbum(id, "{\"title\":\"제목만\",\"version\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersOnly").value(true));
        // 생성 때부터 교인만으로 만들 수 있다
        mvc.perform(post("/api/admin/gallery/albums")
                        .header("Authorization", writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"교인새앨범\",\"membersOnly\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.membersOnly").value(true))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void card_carries_detail_version_and_patch_with_it_toggles_members_only() throws Exception {
        patchAlbum(plain, "{\"title\":\"공개앨범2\",\"version\":0}").andExpect(status().isOk());

        String list = body(mvc.perform(get("/api/gallery/albums").header("Authorization", writer())));
        List<Integer> cardVersions = JsonPath.read(list, "$.content[?(@.id == " + plain + ")].version");
        int cardVersion = cardVersions.getFirst();
        int detailVersion = JsonPath.read(
                body(mvc.perform(get("/api/gallery/albums/" + plain).header("Authorization", writer()))), "$.version");
        assertThat(cardVersion).isEqualTo(1).isEqualTo(detailVersion);

        patchAlbum(plain, "{\"membersOnly\":true,\"version\":" + cardVersion + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersOnly").value(true));
        patchAlbum(plain, "{\"membersOnly\":false,\"version\":" + cardVersion + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }
}
