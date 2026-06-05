package com.elipair.church.domain.tag;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.tag.dto.TagResponse;
import com.elipair.church.global.config.JpaConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class ContentTagRepositoryTest {

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private ContentTagRepository contentTagRepository;

    private ContentTag link(Long tagId, ContentResourceType type, Long resourceId) {
        return new ContentTag(new ContentTagId(tagId, type, resourceId));
    }

    @Test
    void findTagsByResource_joins_tags_and_orders_by_name() {
        Tag worship = tagRepository.save(Tag.create("예배"));
        Tag mission = tagRepository.save(Tag.create("선교"));
        contentTagRepository.save(link(worship.getId(), ContentResourceType.SERMON, 100L));
        contentTagRepository.save(link(mission.getId(), ContentResourceType.SERMON, 100L));
        contentTagRepository.save(link(worship.getId(), ContentResourceType.NOTICE, 100L)); // 다른 타입은 제외돼야

        List<TagResponse> result = contentTagRepository.findTagsByResource(ContentResourceType.SERMON, 100L);

        assertThat(result).extracting(TagResponse::name).containsExactly("선교", "예배");
    }

    @Test
    void findTagRowsByResources_batches_across_resources() {
        Tag worship = tagRepository.save(Tag.create("예배"));
        contentTagRepository.save(link(worship.getId(), ContentResourceType.SERMON, 1L));
        contentTagRepository.save(link(worship.getId(), ContentResourceType.SERMON, 2L));

        List<ResourceTagRow> rows =
                contentTagRepository.findTagRowsByResources(ContentResourceType.SERMON, List.of(1L, 2L));

        assertThat(rows).extracting(ResourceTagRow::getResourceId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(rows).allSatisfy(r -> assertThat(r.getTagName()).isEqualTo("예배"));
    }

    @Test
    void findResourceIdsByTag_filters_by_tag_and_type() {
        Tag worship = tagRepository.save(Tag.create("예배"));
        contentTagRepository.save(link(worship.getId(), ContentResourceType.SERMON, 10L));
        contentTagRepository.save(link(worship.getId(), ContentResourceType.SERMON, 11L));
        contentTagRepository.save(link(worship.getId(), ContentResourceType.NOTICE, 99L)); // 다른 타입 제외

        List<Long> ids = contentTagRepository.findResourceIdsByTag(worship.getId(), ContentResourceType.SERMON);

        assertThat(ids).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void deleteByResource_removes_only_that_resource() {
        Tag worship = tagRepository.save(Tag.create("예배"));
        contentTagRepository.save(link(worship.getId(), ContentResourceType.SERMON, 1L));
        contentTagRepository.save(link(worship.getId(), ContentResourceType.SERMON, 2L));

        contentTagRepository.deleteByResource(ContentResourceType.SERMON, 1L);

        assertThat(contentTagRepository.findResourceIdsByTag(worship.getId(), ContentResourceType.SERMON))
                .containsExactly(2L);
    }

    @Test
    void deleteByTag_removes_all_links_of_tag() {
        Tag worship = tagRepository.save(Tag.create("예배"));
        contentTagRepository.save(link(worship.getId(), ContentResourceType.SERMON, 1L));
        contentTagRepository.save(link(worship.getId(), ContentResourceType.EVENT, 2L));

        contentTagRepository.deleteByTag(worship.getId());

        assertThat(contentTagRepository.count()).isZero();
    }
}
