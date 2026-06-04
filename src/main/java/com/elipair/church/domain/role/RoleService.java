package com.elipair.church.domain.role;

import com.elipair.church.domain.role.dto.RoleCreateRequest;
import com.elipair.church.domain.role.dto.RoleResponse;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import com.elipair.church.global.security.RoleHierarchyValidator;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleHierarchyValidator hierarchyValidator;

    public RoleService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RoleHierarchyValidator hierarchyValidator) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.hierarchyValidator = hierarchyValidator;
    }

    public List<RoleResponse> list() {
        return roleRepository.findAllByOrderByPriorityDesc().stream()
                .map(RoleResponse::from)
                .toList();
    }

    @Transactional
    public RoleResponse create(RoleCreateRequest request, int requesterMaxPriority) {
        String name = normalizeName(request.name());
        hierarchyValidator.validateAssignable(requesterMaxPriority, request.priority());
        if (roleRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        return persist(Role.create(name, request.priority(), normalizeDescription(request.description())));
    }

    // name UNIQUE 경합 백스톱(Position 패턴): 선검사를 빠져나간 동시 생성/수정을 saveAndFlush로 잡는다.
    private RoleResponse persist(Role role) {
        try {
            return RoleResponse.from(roleRepository.saveAndFlush(role));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    private String normalizeName(String raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "역할 이름은 필수입니다");
        }
        String trimmed = raw.trim();
        if (trimmed.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "역할 이름은 공백일 수 없습니다");
        }
        return trimmed;
    }

    private String normalizeDescription(String raw) {
        return raw == null ? null : raw.trim();
    }
}
