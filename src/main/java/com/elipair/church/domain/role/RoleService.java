package com.elipair.church.domain.role;

import com.elipair.church.domain.role.dto.RoleResponse;
import com.elipair.church.global.security.RoleHierarchyValidator;
import java.util.List;
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
}
