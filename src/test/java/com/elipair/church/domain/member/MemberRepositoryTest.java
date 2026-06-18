package com.elipair.church.domain.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.role.Role;
import com.elipair.church.domain.role.RoleRepository;
import com.elipair.church.global.config.JpaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RoleRepository roleRepository;

    private Member save(String phone, String name) {
        return memberRepository.save(Member.create(phone, name, "{enc}", null, null, true, true));
    }

    @Test
    void find_and_exists_by_phone_exclude_soft_deleted() {
        Member m = save("01011112222", "활성");
        assertThat(memberRepository.findByPhoneAndDeletedAtIsNull("01011112222"))
                .isPresent();
        assertThat(memberRepository.existsByPhoneAndDeletedAtIsNull("01011112222"))
                .isTrue();

        m.softDelete();
        memberRepository.saveAndFlush(m);
        assertThat(memberRepository.existsByPhoneAndDeletedAtIsNull("01011112222"))
                .isFalse();
    }

    @Test
    void exists_by_phone_excluding_self_allows_keeping_same_number() {
        Member m = save("01033334444", "본인");
        assertThat(memberRepository.existsByPhoneAndDeletedAtIsNullAndIdNot("01033334444", m.getId()))
                .isFalse();
        save("01055556666", "타인");
        assertThat(memberRepository.existsByPhoneAndDeletedAtIsNullAndIdNot("01055556666", m.getId()))
                .isTrue();
    }

    @Test
    void find_by_uuid_excludes_soft_deleted() {
        Member m = save("01077778888", "uuid조회");
        assertThat(memberRepository.findByUuidAndDeletedAtIsNull(m.getUuid())).isPresent();
    }

    @Test
    void counts_and_exists_super_admin_by_active_only() {
        Role superAdmin = roleRepository.save(Role.create("SUPER_ADMIN", 1000, "최고관리자"));
        Member admin = save("01099990000", "관리자");
        admin.grantRole(superAdmin);
        memberRepository.saveAndFlush(admin);

        assertThat(memberRepository.existsByRoles_NameAndDeletedAtIsNull("SUPER_ADMIN"))
                .isTrue();
        assertThat(memberRepository.countByRoles_NameAndDeletedAtIsNull("SUPER_ADMIN"))
                .isEqualTo(1);

        admin.softDelete();
        memberRepository.saveAndFlush(admin);
        assertThat(memberRepository.existsByRoles_NameAndDeletedAtIsNull("SUPER_ADMIN"))
                .isFalse();
        assertThat(memberRepository.countByRoles_NameAndDeletedAtIsNull("SUPER_ADMIN"))
                .isZero();
    }

    @Test
    void bulk_reset_terms_only_on_active_members() {
        save("01000000001", "A");
        save("01000000002", "B");

        int updated = memberRepository.resetTermsAgreed();

        assertThat(updated).isEqualTo(2);
        assertThat(memberRepository.findByPhoneAndDeletedAtIsNull("01000000001"))
                .get()
                .extracting(Member::isTermsAgreed)
                .isEqualTo(false);
    }

    @Test
    void search_by_name_substring_case_insensitive() {
        save("01011112222", "김철수");
        save("01033334444", "이영희");

        Page<Member> result = memberRepository.findAll(MemberSpecifications.filter("철수"), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Member::getName).containsExactly("김철수");
    }

    @Test
    void search_by_phone_with_hyphen_input() {
        save("01012345678", "김철수");
        save("01099998888", "이영희");

        Page<Member> result = memberRepository.findAll(MemberSpecifications.filter("010-1234"), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Member::getName).containsExactly("김철수");
    }

    @Test
    void search_name_only_query_skips_phone_predicate() {
        save("01012345678", "김철수");

        Page<Member> result = memberRepository.findAll(MemberSpecifications.filter("철"), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Member::getName).containsExactly("김철수");
    }

    @Test
    void search_blank_query_returns_all_active() {
        save("01012345678", "김철수");
        save("01099998888", "이영희");

        Page<Member> result = memberRepository.findAll(MemberSpecifications.filter("  "), PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void search_excludes_soft_deleted() {
        Member m = save("01012345678", "김철수");
        m.softDelete();
        memberRepository.saveAndFlush(m);

        Page<Member> result = memberRepository.findAll(MemberSpecifications.filter("김철수"), PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void search_by_name_is_case_insensitive_for_latin() {
        save("01000000000", "John");

        Page<Member> result = memberRepository.findAll(MemberSpecifications.filter("john"), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Member::getName).containsExactly("John");
    }

    @Test
    void search_matches_union_of_name_and_phone() {
        save("01099998888", "Room101"); // 이름에 101 포함
        save("01010100000", "이영희"); // 전화에 101 포함

        Page<Member> result = memberRepository.findAll(MemberSpecifications.filter("101"), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Member::getName).containsExactlyInAnyOrder("Room101", "이영희");
    }
}
