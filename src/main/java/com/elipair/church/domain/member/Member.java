package com.elipair.church.domain.member;

import com.elipair.church.domain.position.Position;
import com.elipair.church.domain.role.Role;
import com.elipair.church.global.common.BaseTimeEntity;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * 회원(스펙 §5.2). id·uuid 불변(정체성), phone/name/email/password 가변.
 * 낙관락·작성자 없이 created_at만 갖는 BaseTimeEntity를 상속하고, soft delete(deleted_at)·updated_at은 직접 선언한다.
 * 생성은 항상 "동의된 상태"로만 가능(create invariant) — 미동의는 사후 resetAgreement로만 발생.
 */
@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    private static final String WITHDRAWN_PHONE = "(탈퇴)";
    private static final String WITHDRAWN_NAME = "(탈퇴한 사용자)";
    private static final String WITHDRAWN_CREDENTIAL = "(withdrawn)"; // 비-BCrypt 센티넬 → matches() 항상 false

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID uuid;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private String password;

    @Column(length = 255)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private Position position;

    @Column(name = "terms_agreed", nullable = false)
    private boolean termsAgreed;

    @Column(name = "privacy_agreed", nullable = false)
    private boolean privacyAgreed;

    @Column(name = "agreed_at")
    private LocalDateTime agreedAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "member_roles",
            joinColumns = @JoinColumn(name = "member_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    private Member(String phone, String name, String password, String email, Position position) {
        this.uuid = UUID.randomUUID();
        this.phone = phone;
        this.name = name;
        this.password = password;
        this.email = email;
        this.position = position;
    }

    /**
     * 회원 생성. 필수 약관 2종이 모두 true가 아니면 거부(생성 invariant). 성립 시 agreedAt=now.
     * password는 호출자가 BCrypt로 인코딩해 넘긴다. D4 signup·부트스트랩이 재사용.
     */
    public static Member create(
            String phone,
            String name,
            String encodedPassword,
            String email,
            Position position,
            boolean termsAgreed,
            boolean privacyAgreed) {
        if (!(termsAgreed && privacyAgreed)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "필수 약관에 모두 동의해야 합니다");
        }
        Member member = new Member(phone, name, encodedPassword, email, position);
        member.termsAgreed = true;
        member.privacyAgreed = true;
        member.agreedAt = LocalDateTime.now();
        return member;
    }

    /** 각 인자 null은 미변경(부분 수정). phone은 호출 전 정규화·중복검사 완료 가정. */
    public void updateProfile(String name, String phone, String email) {
        if (name != null) {
            this.name = name;
        }
        if (phone != null) {
            this.phone = phone;
        }
        if (email != null) {
            this.email = email;
        }
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void changePosition(Position position) {
        this.position = position;
    }

    /** 재동의 제출: 필수 2종 true + agreedAt 갱신. */
    public void agree() {
        this.termsAgreed = true;
        this.privacyAgreed = true;
        this.agreedAt = LocalDateTime.now();
    }

    /** 약관 일괄 리셋의 엔티티 레벨 동작(테스트용·단건). target 플래그만 false, agreedAt 불변. */
    public void resetAgreement(String target) {
        if ("terms".equals(target)) {
            this.termsAgreed = false;
        } else if ("privacy".equals(target)) {
            this.privacyAgreed = false;
        } else {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "알 수 없는 약관 항목입니다: " + target);
        }
    }

    /** 역할 부여(멱등). 새로 추가되면 true, 이미 보유면 false. */
    public boolean grantRole(Role role) {
        return this.roles.add(role);
    }

    /** 역할 회수(멱등). 제거되면 true, 미보유면 false. */
    public boolean revokeRole(Role role) {
        return this.roles.remove(role);
    }

    public boolean hasRole(String roleName) {
        return this.roles.stream().anyMatch(r -> r.getName().equals(roleName));
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /** 자가탈퇴: 소프트삭제 + 개인정보(PII) 스크럽. 표시는 deletedAt 기준 마스킹이라 스크럽값과 무관. */
    public void withdraw() {
        softDelete();
        this.phone = WITHDRAWN_PHONE;
        this.name = WITHDRAWN_NAME;
        this.email = null;
        this.password = WITHDRAWN_CREDENTIAL;
    }
}
