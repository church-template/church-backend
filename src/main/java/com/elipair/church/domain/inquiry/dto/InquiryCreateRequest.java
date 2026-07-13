package com.elipair.church.domain.inquiry.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 문의 등록(POST) 요청. @Size(max)는 V14 컬럼 길이와 일치.
 * content는 최소 10자(이슈 #50). email은 선택 — 미입력이면 null.
 * privacyAgreed는 개인정보 수집·이용 동의로 true가 아니면 400(스펙 §2 회원가입 동의 관례와 동일).
 */
public record InquiryCreateRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(max = 20) String phone,
        @Email @Size(max = 100) String email,
        @NotBlank @Size(min = 10, max = 2000) String content,
        @AssertTrue(message = "개인정보 수집·이용에 동의해야 합니다") Boolean privacyAgreed) {}
