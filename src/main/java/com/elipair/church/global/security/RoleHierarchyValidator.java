package com.elipair.church.global.security;

import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * priority 기반 위계 검증(스펙 §4.3). DB 의존 없는 순수 컴포넌트 — 카운트·플래그는 호출자(role·member 도메인)가 주입한다.
 * 위반 시 BusinessException(ACCESS_DENIED). 호출자는 D3·D4에서 등장한다.
 */
@Component
public class RoleHierarchyValidator {

    /** 대상 역할 priority가 요청자 maxPriority보다 strictly 낮아야 한다(escalation 차단). */
    public void validateAssignable(int requesterMaxPriority, int targetPriority) {
        if (targetPriority >= requesterMaxPriority) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "대상 역할의 priority가 요청자 권한 이상입니다");
        }
    }

    /** 역할 수정/삭제/권한변경: is_system 보호 + priority 가드. */
    public void validateMutable(int requesterMaxPriority, int targetPriority, boolean targetIsSystem) {
        if (targetIsSystem) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "시스템 역할은 수정·삭제할 수 없습니다");
        }
        validateAssignable(requesterMaxPriority, targetPriority);
    }

    /** 자기 자신의 역할은 부여/회수할 수 없다. */
    public void validateNotSelf(long requesterMemberId, long targetMemberId) {
        if (requesterMemberId == targetMemberId) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "자신의 역할은 변경할 수 없습니다");
        }
    }

    /** 마지막 SUPER_ADMIN 회수·강등·삭제 금지. */
    public void validateNotLastSuperAdmin(boolean targetIsSuperAdmin, long superAdminCount) {
        if (targetIsSuperAdmin && superAdminCount <= 1) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "마지막 SUPER_ADMIN은 회수·강등·삭제할 수 없습니다");
        }
    }
}
