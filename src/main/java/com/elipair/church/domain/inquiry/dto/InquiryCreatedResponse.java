package com.elipair.church.domain.inquiry.dto;

/** 문의 등록 응답. 공개 API라 개인정보를 되돌려주지 않고 접수 번호(id)만 알려준다. */
public record InquiryCreatedResponse(Long id) {}
