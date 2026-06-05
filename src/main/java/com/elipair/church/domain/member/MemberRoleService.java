package com.elipair.church.domain.member;

import com.elipair.church.domain.member.dto.MemberDetailResponse;
import com.elipair.church.domain.role.Role;
import com.elipair.church.domain.role.RoleRepository;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import com.elipair.church.global.security.RoleHierarchyValidator;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 역할 부여/회수(스펙 §4.3). 자기보호·위계·마지막 SUPER_ADMIN 가드는 RoleHierarchyValidator 재사용. */
@Service
@Transactional
public class MemberRoleService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final MemberRepository memberRepository;
    private final RoleRepository roleRepository;
    private final RoleHierarchyValidator hierarchyValidator;

    public MemberRoleService(
            MemberRepository memberRepository,
            RoleRepository roleRepository,
            RoleHierarchyValidator hierarchyValidator) {
        this.memberRepository = memberRepository;
        this.roleRepository = roleRepository;
        this.hierarchyValidator = hierarchyValidator;
    }

    /** 역할 부여(MEMBER 부여 = 교인 승인). 멱등. */
    public MemberDetailResponse grant(UUID targetUuid, Long roleId, long requesterId, int requesterMaxPriority) {
        Member target = findActive(targetUuid);
        Role role = findRole(roleId);
        // 1·2 가드는 항상(보유 여부 무관) 적용.
        hierarchyValidator.validateNotSelf(requesterId, target.getId());
        hierarchyValidator.validateAssignable(requesterMaxPriority, role.getPriority());
        target.grantRole(role); // 이미 보유면 no-op
        return MemberDetailResponse.from(target);
    }

    /** 역할 회수. 멱등. 마지막 활성 SUPER_ADMIN 가드는 실제 보유 회수일 때만. */
    public void revoke(UUID targetUuid, Long roleId, long requesterId, int requesterMaxPriority) {
        Member target = findActive(targetUuid);
        Role role = findRole(roleId);
        hierarchyValidator.validateNotSelf(requesterId, target.getId());
        hierarchyValidator.validateAssignable(requesterMaxPriority, role.getPriority());
        if (target.hasRole(SUPER_ADMIN) && SUPER_ADMIN.equals(role.getName())) {
            long activeSuperAdmins = memberRepository.countByRoles_NameAndDeletedAtIsNull(SUPER_ADMIN);
            hierarchyValidator.validateNotLastSuperAdmin(true, activeSuperAdmins);
        }
        target.revokeRole(role); // 미보유면 no-op
    }

    private Member findActive(UUID uuid) {
        return memberRepository
                .findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Role findRole(Long roleId) {
        return roleRepository.findById(roleId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
