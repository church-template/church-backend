package com.elipair.church.domain.department;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
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
}
