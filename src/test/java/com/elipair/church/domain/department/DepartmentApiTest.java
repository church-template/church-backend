package com.elipair.church.domain.department;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.gallery.GalleryAlbum;
import com.elipair.church.domain.gallery.GalleryAlbumRepository;
import com.elipair.church.domain.gallery.GalleryPhoto;
import com.elipair.church.domain.gallery.GalleryPhotoRepository;
import com.elipair.church.domain.media.Media;
import com.elipair.church.domain.media.MediaRepository;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DepartmentApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private GalleryAlbumRepository albumRepository;

    @Autowired
    private GalleryPhotoRepository photoRepository;

    private Long authorId;

    @BeforeEach
    void seedAuthor() {
        Member author =
                memberRepository.saveAndFlush(Member.create("01000000000", "관리목사", "{enc}", null, null, true, true));
        authorId = author.getId();
    }

    @AfterEach
    void cleanup() {
        // 자기참조 parent_id FK: 단건 delete는 부모-자식 순서 위반 → 단일 statement bulk delete로 회피
        departmentRepository.deleteAllInBatch();
        photoRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        mediaRepository.deleteAllInBatch();
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private String token(Long memberId, String permission) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(memberId, "uuid-" + memberId, "관리자", 1000), null, List.of(permission));
    }

    private String adminToken() {
        return token(authorId, "DEPT_WRITE");
    }

    private String body(String name, Long parentId, Integer sortOrder) {
        String p = parentId == null ? "null" : parentId.toString();
        String s = sortOrder == null ? "null" : sortOrder.toString();
        return """
                {"name":"%s","description":"본문 ![](media:42)","leader":"김목사","parentId":%s,"sortOrder":%s}
                """.formatted(name, p, s);
    }

    private long createDept(String name, Long parentId, Integer sortOrder) throws Exception {
        String json = mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, parentId, sortOrder)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void create_as_dept_write_returns_201_without_author() throws Exception {
        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("예배부", null, 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("예배부"))
                .andExpect(jsonPath("$.leader").value("김목사"))
                .andExpect(jsonPath("$.parentId").doesNotExist())
                .andExpect(jsonPath("$.sortOrder").value(10))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.author").doesNotExist());
    }

    @Test
    void create_anonymous_is_401() throws Exception {
        mockMvc.perform(post("/api/admin/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", null, 10)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void create_without_permission_is_403() throws Exception {
        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", token(authorId, "MEDIA_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", null, 10)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void create_blank_name_is_400() throws Exception {
        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", null, 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_negative_sort_order_is_400() throws Exception {
        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("음수", null, -1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_without_sort_order_appends_max_plus_10() throws Exception {
        // 첫 건 → 10, 둘째 건 → 20(max+10).
        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("first", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(10));

        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("second", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(20));
    }

    @Test
    void create_under_parent_sets_parent_id() throws Exception {
        long parent = createDept("상위", null, 10);

        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("자식", parent, 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value((int) parent));
    }

    @Test
    void create_with_nonexistent_parent_is_400() throws Exception {
        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("고아", 999999L, 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.detail").value("존재하지 않는 상위 부서입니다"));
    }

    @Test
    void create_under_soft_deleted_parent_is_400() throws Exception {
        // FK상 행은 존재하나 deleted_at이 차 있는 부모 밑으로 생성하면 거부(findByIdAndDeletedAtIsNull 가드).
        long parent = createDept("삭제될상위", null, 10);
        mockMvc.perform(delete("/api/admin/departments/" + parent).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("자식", parent, 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.detail").value("존재하지 않는 상위 부서입니다"));
    }

    @Test
    void public_list_is_flat_array_ordered_and_omits_description() throws Exception {
        createDept("나중", null, 20);
        createDept("먼저", null, 10);

        // 최상위 JSON 배열(Page 봉투 아님), sort_order ASC, 카드에 description 없음.
        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("먼저"))
                .andExpect(jsonPath("$[1].name").value("나중"))
                .andExpect(jsonPath("$[0].description").doesNotExist())
                .andExpect(jsonPath("$.page").doesNotExist());
    }

    @Test
    void detail_returns_description() throws Exception {
        long id = createDept("상세부서", null, 10);

        mockMvc.perform(get("/api/departments/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("본문 ![](media:42)"))
                .andExpect(jsonPath("$.author").doesNotExist());
    }

    @Test
    void detail_unknown_is_404() throws Exception {
        mockMvc.perform(get("/api/departments/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void put_full_update_changes_fields_and_bumps_version() throws Exception {
        long id = createDept("원본", null, 10);
        String update = """
                {"name":"수정부서","description":"수정","leader":"이목사","parentId":null,"sortOrder":30,"version":0}
                """;

        mockMvc.perform(put("/api/admin/departments/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("수정부서"))
                .andExpect(jsonPath("$.sortOrder").value(30))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void put_with_stale_version_is_409() throws Exception {
        long id = createDept("원본", null, 10);
        String v0 = """
                {"name":"A","description":"c","leader":"l","parentId":null,"sortOrder":10,"version":0}
                """;
        mockMvc.perform(put("/api/admin/departments/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v0))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/departments/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void patch_with_stale_version_is_409() throws Exception {
        long id = createDept("원본", null, 10);
        mockMvc.perform(patch("/api/admin/departments/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"1차","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        // version이 1로 올라가 0은 stale → 409
        mockMvc.perform(patch("/api/admin/departments/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"2차","version":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void patch_changes_parent() throws Exception {
        long parent = createDept("상위", null, 10);
        long child = createDept("이동대상", null, 20);

        mockMvc.perform(patch("/api/admin/departments/" + child)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId":%d,"version":0}
                                """.formatted(parent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value((int) parent))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void patch_self_reference_is_400() throws Exception {
        long id = createDept("자기참조", null, 10);

        mockMvc.perform(patch("/api/admin/departments/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId":%d,"version":0}
                                """.formatted(id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.detail").value("자기 자신을 상위 부서로 지정할 수 없습니다"));
    }

    @Test
    void patch_descendant_as_parent_is_cycle_400() throws Exception {
        long a = createDept("A", null, 10); // 루트
        long b = createDept("B", a, 10); // b.parent = a

        // a.parent = b 로 바꾸면 b는 a의 후손 → 사이클.
        mockMvc.perform(patch("/api/admin/departments/" + a)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId":%d,"version":0}
                                """.formatted(b)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.detail").value("하위 부서를 상위 부서로 지정할 수 없습니다"));
    }

    @Test
    void delete_with_children_is_409() throws Exception {
        long parent = createDept("상위", null, 10);
        createDept("자식", parent, 10);

        mockMvc.perform(delete("/api/admin/departments/" + parent).header("Authorization", adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DEPARTMENT_HAS_CHILDREN"));
    }

    @Test
    void delete_without_children_then_detail_404() throws Exception {
        long id = createDept("삭제대상", null, 10);

        mockMvc.perform(delete("/api/admin/departments/" + id).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/departments/" + id)).andExpect(status().isNotFound());
    }

    // ---- 대표 사진·앨범 (#92) ----

    private long seedMedia(String mime) {
        return mediaRepository
                .save(Media.create("f", "k/" + System.nanoTime() + mime.hashCode(), mime, 10L, authorId))
                .getId();
    }

    private long seedAlbum() {
        return albumRepository.save(GalleryAlbum.create("앨범", null)).getId();
    }

    private void addPhoto(long albumId, long mediaId, int sortOrder) {
        photoRepository.save(GalleryPhoto.create(albumId, mediaId, sortOrder));
    }

    private long createWith(String extra) throws Exception {
        String json = mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"부서\"" + extra + "}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions send(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder method, String json)
            throws Exception {
        return mockMvc.perform(method.header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void 대표_사진과_앨범을_생성시_연결한다() throws Exception {
        long media = seedMedia("image/jpeg");
        long album = seedAlbum();

        long id = createWith(",\"heroMediaId\":" + media + ",\"albumId\":" + album);

        mockMvc.perform(get("/api/departments/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heroMediaId").value((int) media))
                .andExpect(jsonPath("$.albumId").value((int) album));
    }

    @Test
    void 새_칸을_보내지_않은_PUT은_연결을_유지한다() throws Exception {
        long media = seedMedia("image/jpeg");
        long album = seedAlbum();
        long id = createWith(",\"heroMediaId\":" + media + ",\"albumId\":" + album);

        send(put("/api/admin/departments/" + id), "{\"name\":\"새이름\",\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새이름"))
                .andExpect(jsonPath("$.heroMediaId").value((int) media))
                .andExpect(jsonPath("$.albumId").value((int) album));
    }

    @Test
    void null을_보낸_PUT과_PATCH는_연결을_비운다() throws Exception {
        long media = seedMedia("image/jpeg");
        long album = seedAlbum();
        long id = createWith(",\"heroMediaId\":" + media + ",\"albumId\":" + album);

        send(put("/api/admin/departments/" + id), "{\"name\":\"부서\",\"version\":0,\"heroMediaId\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heroMediaId").doesNotExist())
                .andExpect(jsonPath("$.albumId").value((int) album));

        send(patch("/api/admin/departments/" + id), "{\"albumId\":null,\"version\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.albumId").doesNotExist())
                .andExpect(jsonPath("$.photos").isEmpty());
    }

    @Test
    void PATCH로_대표_사진만_바꾼다() throws Exception {
        long first = seedMedia("image/jpeg");
        long second = seedMedia("image/png");
        long album = seedAlbum();
        long id = createWith(",\"heroMediaId\":" + first + ",\"albumId\":" + album);

        send(patch("/api/admin/departments/" + id), "{\"heroMediaId\":" + second + ",\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heroMediaId").value((int) second))
                .andExpect(jsonPath("$.albumId").value((int) album));
    }

    @Test
    void 이미지가_아닌_대표_사진은_400() throws Exception {
        long pdf = seedMedia("application/pdf");

        send(post("/api/admin/departments"), "{\"name\":\"부서\",\"heroMediaId\":" + pdf + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 없는_미디어_id의_대표_사진은_400() throws Exception {
        send(post("/api/admin/departments"), "{\"name\":\"부서\",\"heroMediaId\":999999}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 숫자가_아닌_heroMediaId는_400() throws Exception {
        long id = createWith("");

        send(patch("/api/admin/departments/" + id), "{\"heroMediaId\":\"abc\",\"version\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 없거나_삭제된_앨범_연결은_400() throws Exception {
        send(post("/api/admin/departments"), "{\"name\":\"부서\",\"albumId\":999999}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));

        GalleryAlbum deleted = GalleryAlbum.create("삭제", null);
        deleted.softDelete();
        long deletedId = albumRepository.save(deleted).getId();
        send(post("/api/admin/departments"), "{\"name\":\"부서\",\"albumId\":" + deletedId + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 공개_상세는_앨범_사진을_앨범_순서로_준다() throws Exception {
        long album = seedAlbum();
        long m0 = seedMedia("image/jpeg");
        long m1 = seedMedia("image/jpeg");
        long m2 = seedMedia("image/jpeg");
        addPhoto(album, m2, 2);
        addPhoto(album, m0, 0);
        addPhoto(album, m1, 1);
        long id = createWith(",\"albumId\":" + album);

        mockMvc.perform(get("/api/departments/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos[0]").value((int) m0))
                .andExpect(jsonPath("$.photos[1]").value((int) m1))
                .andExpect(jsonPath("$.photos[2]").value((int) m2))
                .andExpect(jsonPath("$.photos.length()").value(3));
    }

    @Test
    void 공개_목록에_대표_사진_id가_있다() throws Exception {
        long media = seedMedia("image/jpeg");
        createWith(",\"heroMediaId\":" + media);

        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].heroMediaId").value((int) media));
    }

    @Test
    void 연결_앨범이_삭제되면_albumId_null_photos_빈배열() throws Exception {
        long album = seedAlbum();
        addPhoto(album, seedMedia("image/jpeg"), 0);
        long id = createWith(",\"albumId\":" + album);
        GalleryAlbum a = albumRepository.findById(album).orElseThrow();
        a.softDelete();
        albumRepository.save(a);

        mockMvc.perform(get("/api/departments/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.albumId").doesNotExist())
                .andExpect(jsonPath("$.photos").isEmpty());
    }

    @Test
    void 회원전용_앨범_경로는_여전히_익명_차단() throws Exception {
        mockMvc.perform(get("/api/gallery/albums/" + seedAlbum())).andExpect(status().isUnauthorized());
    }
}
