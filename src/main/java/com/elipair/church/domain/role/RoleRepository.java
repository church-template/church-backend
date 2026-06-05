package com.elipair.church.domain.role;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    /** priority 내림차순 + permissions 즉시 로딩(목록 N+1 회피). */
    @EntityGraph(attributePaths = "permissions")
    List<Role> findAllByOrderByPriorityDesc();

    boolean existsByName(String name);

    Optional<Role> findByName(String name);
}
