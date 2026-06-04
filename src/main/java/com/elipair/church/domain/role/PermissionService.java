package com.elipair.church.domain.role;

import com.elipair.church.domain.role.dto.PermissionResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionRepository repository;

    public PermissionService(PermissionRepository repository) {
        this.repository = repository;
    }

    public List<PermissionResponse> list() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(PermissionResponse::from)
                .toList();
    }
}
