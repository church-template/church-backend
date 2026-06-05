package com.elipair.church.domain.tag;

import com.elipair.church.domain.tag.dto.TagResponse;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 콘텐츠 ↔ 태그 연결의 재사용 컴포넌트(스펙 §5.11). 콘텐츠 도메인(sermon/notice/event/gallery)이
 * 다른 패키지에서 호출하므로 모든 메서드는 public.
 *
 * <p>참조 무결성 분담: 리소스 존재는 호출자(콘텐츠 도메인)가 리소스를 save/load해 손에 쥔 상태로 보장한다.
 * 이 컴포넌트는 태그 존재만 검증한다. (소비자 의무는 각 콘텐츠 이슈 D7~D11의 수용조건으로 명문화 — 설계 §5.)
 */
@Service
@Transactional(readOnly = true)
public class ContentTagService {

    private final TagRepository tagRepository;
    private final ContentTagRepository contentTagRepository;

    public ContentTagService(TagRepository tagRepository, ContentTagRepository contentTagRepository) {
        this.tagRepository = tagRepository;
        this.contentTagRepository = contentTagRepository;
    }

    /** 콘텐츠 생성·수정 시 호출. tagIds 전량 교체(dedup, null/빈 리스트면 전부 해제). */
    @Transactional
    public void replaceLinks(ContentResourceType type, Long resourceId, List<Long> tagIds) {
        List<Long> distinct =
                tagIds == null ? List.of() : tagIds.stream().distinct().toList();
        if (tagRepository.findAllById(distinct).size() != distinct.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "존재하지 않는 태그가 포함되어 있습니다");
        }
        contentTagRepository.deleteByResource(type, resourceId);
        if (!distinct.isEmpty()) {
            contentTagRepository.saveAll(distinct.stream()
                    .map(tagId -> new ContentTag(new ContentTagId(tagId, type, resourceId)))
                    .toList());
        }
    }

    /** 콘텐츠 상세 응답용 — 한 리소스의 태그(name ASC). */
    public List<TagResponse> getTags(ContentResourceType type, Long resourceId) {
        return contentTagRepository.findTagsByResource(type, resourceId);
    }

    /** 콘텐츠 목록 응답용 — N+1 회피 배치 조회(행을 resourceId로 그룹핑). */
    public Map<Long, List<TagResponse>> getTagsByResources(ContentResourceType type, Collection<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Map.of();
        }
        return contentTagRepository.findTagRowsByResources(type, resourceIds).stream()
                .collect(Collectors.groupingBy(
                        ResourceTagRow::getResourceId,
                        Collectors.mapping(
                                row -> new TagResponse(row.getTagId(), row.getTagName()), Collectors.toList())));
    }

    /** 콘텐츠 soft-delete 시 연결 정리. */
    @Transactional
    public void cleanUp(ContentResourceType type, Long resourceId) {
        contentTagRepository.deleteByResource(type, resourceId);
    }

    /** ?tagId= 필터 보조 — 해당 태그를 가진 리소스 id 목록. */
    public List<Long> resourceIdsWithTag(ContentResourceType type, Long tagId) {
        return contentTagRepository.findResourceIdsByTag(tagId, type);
    }
}
