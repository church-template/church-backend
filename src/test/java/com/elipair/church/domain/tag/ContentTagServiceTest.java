package com.elipair.church.domain.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.tag.dto.TagResponse;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ContentTagServiceTest {

    @Autowired
    private ContentTagService contentTagService;

    @Autowired
    private TagService tagService;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private ContentTagRepository contentTagRepository;

    @AfterEach
    void cleanup() {
        contentTagRepository.deleteAll();
        tagRepository.deleteAll();
    }

    private Long tag(String name) {
        return tagRepository.save(Tag.create(name)).getId();
    }

    @Test
    void replaceLinks_creates_links_and_getTags_returns_them_sorted() {
        Long worship = tag("예배");
        Long mission = tag("선교");

        contentTagService.replaceLinks(ContentResourceType.SERMON, 100L, List.of(mission, worship));

        assertThat(contentTagService.getTags(ContentResourceType.SERMON, 100L))
                .extracting(TagResponse::name)
                .containsExactly("선교", "예배"); // name ASC
    }

    @Test
    void replaceLinks_replaces_previous_set() {
        Long worship = tag("예배");
        Long mission = tag("선교");
        contentTagService.replaceLinks(ContentResourceType.SERMON, 100L, List.of(worship));

        contentTagService.replaceLinks(ContentResourceType.SERMON, 100L, List.of(mission));

        assertThat(contentTagService.getTags(ContentResourceType.SERMON, 100L))
                .extracting(TagResponse::name)
                .containsExactly("선교");
    }

    @Test
    void replaceLinks_empty_clears_all() {
        Long worship = tag("예배");
        contentTagService.replaceLinks(ContentResourceType.SERMON, 100L, List.of(worship));

        contentTagService.replaceLinks(ContentResourceType.SERMON, 100L, List.of());

        assertThat(contentTagService.getTags(ContentResourceType.SERMON, 100L)).isEmpty();
    }

    @Test
    void replaceLinks_unknown_tag_throws_400() {
        Long worship = tag("예배");

        assertThatThrownBy(() ->
                        contentTagService.replaceLinks(ContentResourceType.SERMON, 100L, List.of(worship, 999999L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void getTagsByResources_batches_by_resource_id() {
        Long worship = tag("예배");
        Long mission = tag("선교");
        contentTagService.replaceLinks(ContentResourceType.SERMON, 1L, List.of(worship));
        contentTagService.replaceLinks(ContentResourceType.SERMON, 2L, List.of(worship, mission));

        Map<Long, List<TagResponse>> map =
                contentTagService.getTagsByResources(ContentResourceType.SERMON, List.of(1L, 2L));

        assertThat(map.get(1L)).extracting(TagResponse::name).containsExactly("예배");
        assertThat(map.get(2L)).extracting(TagResponse::name).containsExactly("선교", "예배");
    }

    @Test
    void resourceIdsWithTag_filters() {
        Long worship = tag("예배");
        contentTagService.replaceLinks(ContentResourceType.SERMON, 10L, List.of(worship));
        contentTagService.replaceLinks(ContentResourceType.NOTICE, 20L, List.of(worship));

        assertThat(contentTagService.resourceIdsWithTag(ContentResourceType.SERMON, worship))
                .containsExactly(10L);
    }

    @Test
    void cleanUp_removes_resource_links() {
        Long worship = tag("예배");
        contentTagService.replaceLinks(ContentResourceType.SERMON, 100L, List.of(worship));

        contentTagService.cleanUp(ContentResourceType.SERMON, 100L);

        assertThat(contentTagService.getTags(ContentResourceType.SERMON, 100L)).isEmpty();
    }

    @Test
    void tagDelete_cascades_links_via_TagService() {
        Long worship = tag("예배");
        contentTagService.replaceLinks(ContentResourceType.SERMON, 100L, List.of(worship));

        tagService.delete(worship);

        assertThat(contentTagRepository.count()).isZero();
    }

    @Test
    void getTagsByResources_returns_empty_list_for_resource_without_tags() {
        Long worship = tag("예배");
        contentTagService.replaceLinks(ContentResourceType.SERMON, 1L, List.of(worship));

        Map<Long, List<TagResponse>> map =
                contentTagService.getTagsByResources(ContentResourceType.SERMON, List.of(1L, 99L));

        assertThat(map.get(1L)).extracting(TagResponse::name).containsExactly("예배");
        assertThat(map.get(99L)).isNotNull().isEmpty(); // 태그 없는 리소스도 키가 존재, null 아님
    }

    @Test
    void replaceLinks_null_tag_element_is_400() {
        Long worship = tag("예배");
        java.util.List<Long> withNull = new java.util.ArrayList<>();
        withNull.add(worship);
        withNull.add(null);

        assertThatThrownBy(() -> contentTagService.replaceLinks(ContentResourceType.SERMON, 100L, withNull))
                .isInstanceOf(com.elipair.church.global.exception.BusinessException.class)
                .extracting(e -> ((com.elipair.church.global.exception.BusinessException) e).getErrorCode())
                .isEqualTo(com.elipair.church.global.exception.ErrorCode.INVALID_INPUT_VALUE);
    }
}
