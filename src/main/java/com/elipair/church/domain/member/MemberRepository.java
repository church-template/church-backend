package com.elipair.church.domain.member;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    @EntityGraph(attributePaths = {"position", "roles", "roles.permissions"})
    Optional<Member> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = {"position", "roles", "roles.permissions"})
    Optional<Member> findByUuidAndDeletedAtIsNull(UUID uuid);

    @EntityGraph(attributePaths = {"position", "roles", "roles.permissions"})
    Optional<Member> findByPhoneAndDeletedAtIsNull(String phone); // D4 로그인 재사용

    boolean existsByPhoneAndDeletedAtIsNull(String phone);

    boolean existsByPhoneAndDeletedAtIsNullAndIdNot(String phone, Long id); // 번호변경 자기제외 중복체크

    // 컬렉션(roles)을 페이징 쿼리에서 fetch하면 Hibernate가 in-memory 페이징(HHH000104)을 한다.
    // position만 fetch join하고 roles는 readOnly 트랜잭션 내 DTO 매핑 중 lazy 로딩(카드 size=10 N+1은 교회 규모에서 무해).
    @EntityGraph(attributePaths = {"position"})
    Page<Member> findByDeletedAtIsNull(Pageable pageable);

    long countByRoles_NameAndDeletedAtIsNull(String roleName); // 마지막 활성 SUPER_ADMIN 가드

    boolean existsByRoles_NameAndDeletedAtIsNull(String roleName); // 부트스트랩 멱등 판정(활성 기준)

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Member m SET m.termsAgreed = false WHERE m.deletedAt IS NULL")
    int resetTermsAgreed();

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Member m SET m.privacyAgreed = false WHERE m.deletedAt IS NULL")
    int resetPrivacyAgreed();

    // 작성자 표시(AuthorDisplayService) — soft-deleted 포함 id 일괄 조회(미삭제 필터 없음).
    @Query("select m.id as id, m.name as name, m.deletedAt as deletedAt from Member m where m.id in :ids")
    List<AuthorView> findAuthorViewsByIdIn(@Param("ids") Collection<Long> ids);
}
