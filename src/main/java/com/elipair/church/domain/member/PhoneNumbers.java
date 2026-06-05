package com.elipair.church.domain.member;

import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;

/** 전화번호를 숫자만 남겨 정규화한다(스펙 §3 "digits-only normalized"). D4 로그인도 재사용. */
final class PhoneNumbers {

    private PhoneNumbers() {}

    static String normalize(String raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "전화번호는 필수입니다");
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 전화번호입니다");
        }
        return digits;
    }
}
