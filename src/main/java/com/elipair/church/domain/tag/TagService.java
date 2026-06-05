package com.elipair.church.domain.tag;

import com.elipair.church.domain.tag.dto.TagCreateRequest;
import com.elipair.church.domain.tag.dto.TagResponse;
import com.elipair.church.domain.tag.dto.TagUpdateRequest;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 태그 풀 CRUD(스펙 §5.11). 중복·정규화는 Position/Role 패턴.
 * 삭제는 비차단: 연결(content_tags)을 먼저 앱 레벨로 정리한 뒤 태그를 물리 삭제한다(미디어의 차단 삭제와 대비).
 */
@Service
@Transactional(readOnly = true)
public class TagService {

    private final TagRepository tagRepository;
    private final ContentTagRepository contentTagRepository;

    public TagService(TagRepository tagRepository, ContentTagRepository contentTagRepository) {
        this.tagRepository = tagRepository;
        this.contentTagRepository = contentTagRepository;
    }

    public List<TagResponse> list() {
        return tagRepository.findAllByOrderByNameAsc().stream()
                .map(TagResponse::from)
                .toList();
    }

    @Transactional
    public TagResponse create(TagCreateRequest request) {
        String name = normalizeName(request.name());
        if (tagRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        return persist(Tag.create(name));
    }

    @Transactional
    public TagResponse update(Long id, TagUpdateRequest request) {
        Tag tag = tagRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (request.name() != null) {
            String name = normalizeName(request.name());
            if (!name.equals(tag.getName()) && tagRepository.existsByName(name)) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
            }
            tag.rename(name);
        }
        return persist(tag);
    }

    @Transactional
    public void delete(Long id) {
        tagRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        contentTagRepository.deleteByTag(id); // 비차단: 연결 먼저 정리(flush), 그 다음 태그 물리 삭제
        tagRepository.deleteById(id);
    }

    // name UNIQUE 경합 백스톱(Position/Role 패턴): 선검사를 빠져나간 동시 생성/수정을 saveAndFlush로 잡는다.
    private TagResponse persist(Tag tag) {
        try {
            return TagResponse.from(tagRepository.saveAndFlush(tag));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    private String normalizeName(String raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "태그 이름은 필수입니다");
        }
        String trimmed = raw.trim();
        if (trimmed.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "태그 이름은 공백일 수 없습니다");
        }
        return trimmed;
    }
}
