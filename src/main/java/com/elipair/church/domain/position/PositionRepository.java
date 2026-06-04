package com.elipair.church.domain.position;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findAllByOrderBySortOrderAsc();

    boolean existsByName(String name);

    /** 빈 테이블이면 Optional.empty()(스칼라 null → empty). */
    @Query("select max(p.sortOrder) from Position p")
    Optional<Integer> findMaxSortOrder();
}
