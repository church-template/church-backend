package com.elipair.church.domain.position;

import com.elipair.church.domain.position.dto.PositionCreateRequest;
import com.elipair.church.domain.position.dto.PositionResponse;
import com.elipair.church.domain.position.dto.PositionUpdateRequest;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PositionService {

    private static final int SORT_ORDER_GAP = 10;

    private final PositionRepository repository;

    public PositionService(PositionRepository repository) {
        this.repository = repository;
    }

    public List<PositionResponse> list() {
        return repository.findAllByOrderBySortOrderAsc().stream()
                .map(PositionResponse::from)
                .toList();
    }

    @Transactional
    public PositionResponse create(PositionCreateRequest request) {
        String name = normalizeName(request.name());
        if (repository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        int sortOrder = request.sortOrder() != null
                ? request.sortOrder()
                : repository.findMaxSortOrder().map(max -> max + SORT_ORDER_GAP).orElse(SORT_ORDER_GAP);
        return persist(Position.of(name, sortOrder));
    }

    @Transactional
    public PositionResponse update(Long id, PositionUpdateRequest request) {
        Position position =
                repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        String name = null;
        if (request.name() != null) {
            name = normalizeName(request.name());
            if (!name.equals(position.getName()) && repository.existsByName(name)) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
            }
        }
        position.update(name, request.sortOrder());
        return persist(position);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        repository.deleteById(id);
    }

    // name UNIQUE 경합 백스톱: 선검사를 빠져나간 동시 생성/수정을 saveAndFlush로 즉시 flush해 잡는다.
    // 주의: 제약 위반 시 트랜잭션이 rollback-only로 마킹되므로 persist는 항상 create/update의 마지막 문장이어야 한다
    //       (이후 DB 부수효과를 추가하면 조용히 롤백된다).
    private PositionResponse persist(Position position) {
        try {
            return PositionResponse.from(repository.saveAndFlush(position));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    private String normalizeName(String raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "직분 이름은 필수입니다");
        }
        String trimmed = raw.trim();
        if (trimmed.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "직분 이름은 공백일 수 없습니다");
        }
        return trimmed;
    }
}
