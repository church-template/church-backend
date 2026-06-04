package com.elipair.church.domain.role;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByNameAsc();

    List<Permission> findByNameIn(Collection<String> names);
}
