package com.elipair.church.domain.event;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    Optional<Event> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 본문(description)이 media:{id}를 참조하는 미삭제 일정(id·title). PG 정규식 ~ 로 경계 안전 매칭.
     * pattern 예: "media:42($|[^0-9])" — 42가 media:420/421에 매칭되지 않는다.
     */
    @Query(
            value = "select id as id, title as title from events where deleted_at is null and description ~ :pattern",
            nativeQuery = true)
    List<EventRefRow> findReferencesByMedia(@Param("pattern") String pattern);
}
