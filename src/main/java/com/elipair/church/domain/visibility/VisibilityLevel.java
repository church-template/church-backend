package com.elipair.church.domain.visibility;

/** 메뉴 공개 단계. DB에는 문자열로 저장해 나중에 단계를 더할 수 있게 둔다(설계 6.2). */
public enum VisibilityLevel {
    PUBLIC,
    MEMBERS
}
