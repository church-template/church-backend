package com.elipair.church.domain.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.config.JpaConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class AuthorDisplayServiceTest {

    @Autowired
    private MemberRepository memberRepository;

    private AuthorDisplayService service;

    @BeforeEach
    void init() {
        service = new AuthorDisplayService(memberRepository);
    }

    private Long saveMember(String phone, String name, boolean deleted) {
        Member m = Member.create(phone, name, "{enc}", null, null, true, true);
        if (deleted) {
            m.softDelete();
        }
        return memberRepository.saveAndFlush(m).getId();
    }

    @Test
    void active_member_returns_name() {
        Long id = saveMember("01000000001", "홍길동", false);
        assertThat(service.displayName(id)).isEqualTo("홍길동");
    }

    @Test
    void soft_deleted_member_is_masked() {
        Long id = saveMember("01000000002", "탈퇴자", true);
        assertThat(service.displayName(id)).isEqualTo("(탈퇴한 사용자)");
    }

    @Test
    void null_and_unknown_id_is_unknown() {
        assertThat(service.displayName(null)).isEqualTo("(알 수 없음)");
        assertThat(service.displayName(999999L)).isEqualTo("(알 수 없음)");
    }

    @Test
    void batch_returns_complete_map_for_all_requested_ids() {
        Long active = saveMember("01000000003", "활성자", false);
        Long withdrawn = saveMember("01000000004", "탈퇴자2", true);

        Map<Long, String> names = service.displayNames(List.of(active, withdrawn, 888888L));

        assertThat(names).containsEntry(active, "활성자");
        assertThat(names).containsEntry(withdrawn, "(탈퇴한 사용자)");
        assertThat(names).containsEntry(888888L, "(알 수 없음)");
    }
}
