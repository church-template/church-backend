package com.elipair.church.domain.visibility;

/** 공개 범위를 교회별로 정하는 메뉴 5개(#95). 메뉴가 MEMBERS일 때 "교인" = 해당 조회 권한 보유자(설계 5.3). */
public enum ContentDomain {
    NOTICE("NOTICE_VIEW"),
    EVENT("EVENT_VIEW"),
    BULLETIN("BULLETIN_VIEW"),
    SERMON("SERMON_VIEW"),
    GALLERY("GALLERY_VIEW");

    private final String viewPermission;

    ContentDomain(String viewPermission) {
        this.viewPermission = viewPermission;
    }

    public String viewPermission() {
        return viewPermission;
    }

    /** 설정 행이 없을 때의 값 = 새 교회 기본값(갤러리만 교인만). 행 누락으로 서버가 죽지 않게 한다(설계 5.2). */
    public VisibilityLevel defaultLevel() {
        return this == GALLERY ? VisibilityLevel.MEMBERS : VisibilityLevel.PUBLIC;
    }
}
