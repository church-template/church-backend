package com.elipair.church.domain.visibility.dto;

import com.elipair.church.domain.visibility.ContentDomain;
import com.elipair.church.domain.visibility.VisibilityLevel;
import java.util.Map;

/** 메뉴 5개의 공개 범위. 행이 없는 메뉴는 도메인 기본값으로 채운다. */
public record VisibilityResponse(
        VisibilityLevel notices,
        VisibilityLevel events,
        VisibilityLevel bulletins,
        VisibilityLevel sermons,
        VisibilityLevel gallery) {

    public static VisibilityResponse from(Map<ContentDomain, VisibilityLevel> stored) {
        return new VisibilityResponse(
                level(stored, ContentDomain.NOTICE),
                level(stored, ContentDomain.EVENT),
                level(stored, ContentDomain.BULLETIN),
                level(stored, ContentDomain.SERMON),
                level(stored, ContentDomain.GALLERY));
    }

    private static VisibilityLevel level(Map<ContentDomain, VisibilityLevel> stored, ContentDomain domain) {
        return stored.getOrDefault(domain, domain.defaultLevel());
    }

    public VisibilityLevel levelOf(ContentDomain domain) {
        return switch (domain) {
            case NOTICE -> notices;
            case EVENT -> events;
            case BULLETIN -> bulletins;
            case SERMON -> sermons;
            case GALLERY -> gallery;
        };
    }
}
