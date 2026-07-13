package com.elipair.church.domain.inquiry.dto;

import jakarta.validation.constraints.NotNull;

/** 문의 완료 체크/해제. true=완료 처리, false=완료 취소(미처리로 되돌림). */
public record InquiryCompleteRequest(@NotNull Boolean completed) {}
