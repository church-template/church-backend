package com.elipair.church.domain.member.dto;

import com.elipair.church.domain.member.Member;
import java.time.LocalDateTime;

public record AgreementResponse(boolean termsAgreed, boolean privacyAgreed, LocalDateTime agreedAt) {

    public static AgreementResponse from(Member m) {
        return new AgreementResponse(m.isTermsAgreed(), m.isPrivacyAgreed(), m.getAgreedAt());
    }
}
