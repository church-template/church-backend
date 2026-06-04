package com.elipair.church.global.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class RoleHierarchyValidatorTest {

    private final RoleHierarchyValidator validator = new RoleHierarchyValidator();

    @Test
    void assignable_rejects_equal_or_higher_priority() {
        assertThatThrownBy(() -> validator.validateAssignable(900, 900))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        assertThatThrownBy(() -> validator.validateAssignable(900, 1000)).isInstanceOf(BusinessException.class);
    }

    @Test
    void assignable_allows_strictly_lower_priority() {
        assertThatCode(() -> validator.validateAssignable(900, 899)).doesNotThrowAnyException();
    }

    @Test
    void mutable_rejects_system_role_regardless_of_priority() {
        assertThatThrownBy(() -> validator.validateMutable(1000, 100, true)).isInstanceOf(BusinessException.class);
    }

    @Test
    void mutable_allows_non_system_lower_priority() {
        assertThatCode(() -> validator.validateMutable(1000, 100, false)).doesNotThrowAnyException();
    }

    @Test
    void rejects_changing_own_role() {
        assertThatThrownBy(() -> validator.validateNotSelf(7L, 7L)).isInstanceOf(BusinessException.class);
        assertThatCode(() -> validator.validateNotSelf(7L, 8L)).doesNotThrowAnyException();
    }

    @Test
    void protects_last_super_admin() {
        assertThatThrownBy(() -> validator.validateNotLastSuperAdmin(true, 1)).isInstanceOf(BusinessException.class);
        assertThatCode(() -> validator.validateNotLastSuperAdmin(true, 2)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateNotLastSuperAdmin(false, 1)).doesNotThrowAnyException();
    }
}
