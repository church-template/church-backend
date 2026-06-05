package com.elipair.church.domain.tag;

import com.elipair.church.domain.tag.dto.TagResponse;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 다형 연결 조회/삭제. @EmbeddedId 파생 메서드명 함정과 tags 조인 필요 때문에 전부 명시 @Query.
 * 조인은 매핑 연관이 아니라 tag_id로 잇는 암시 조인(from ContentTag ct, Tag t where t.id = ct.id.tagId).
 */
public interface ContentTagRepository extends JpaRepository<ContentTag, ContentTagId> {

    // 태그 삭제 시 연결 정리. flush로 DELETE를 즉시 반영, clear로 L1 stale 방지.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ContentTag ct where ct.id.tagId = :tagId")
    void deleteByTag(@Param("tagId") Long tagId);

    // 콘텐츠 cleanUp / replaceLinks 교체 전 비우기.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ContentTag ct where ct.id.resourceType = :type and ct.id.resourceId = :rid")
    void deleteByResource(@Param("type") ContentResourceType type, @Param("rid") Long resourceId);

    // 상세: 한 리소스의 태그 — tags 조인해 (id, name) 투영.
    @Query("select new com.elipair.church.domain.tag.dto.TagResponse(t.id, t.name) "
            + "from ContentTag ct, Tag t "
            + "where t.id = ct.id.tagId and ct.id.resourceType = :type and ct.id.resourceId = :rid "
            + "order by t.name asc")
    List<TagResponse> findTagsByResource(@Param("type") ContentResourceType type, @Param("rid") Long resourceId);

    // 목록 배치: (resourceId, tagId, name) 행 — 서비스가 resourceId로 그룹핑.
    @Query("select ct.id.resourceId as resourceId, t.id as tagId, t.name as tagName "
            + "from ContentTag ct, Tag t "
            + "where t.id = ct.id.tagId and ct.id.resourceType = :type and ct.id.resourceId in :rids "
            + "order by t.name asc")
    List<ResourceTagRow> findTagRowsByResources(
            @Param("type") ContentResourceType type, @Param("rids") Collection<Long> resourceIds);

    // ?tagId= 필터 보조.
    @Query("select ct.id.resourceId from ContentTag ct " + "where ct.id.tagId = :tagId and ct.id.resourceType = :type")
    List<Long> findResourceIdsByTag(@Param("tagId") Long tagId, @Param("type") ContentResourceType type);
}
