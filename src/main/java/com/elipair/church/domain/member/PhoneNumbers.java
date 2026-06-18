package com.elipair.church.domain.member;

import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;

/** 전화번호를 숫자만 남겨 정규화한다(스펙 §3 "digits-only normalized"). D4 로그인도 재사용. */
public final class PhoneNumbers {

    private PhoneNumbers() {}

    /** 숫자만 추출(비예외). 숫자가 없거나 null이면 빈 문자열. 검색 q→전화 부분일치에 사용. */
    public static String extractDigits(String raw) {
        return raw == null ? "" : raw.replaceAll("\\D", "");
    }

    public static String normalize(String raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "전화번호는 필수입니다");
        }
        String digits = extractDigits(raw);
        if (digits.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 전화번호입니다");
        }
        return digits;
    }
}
