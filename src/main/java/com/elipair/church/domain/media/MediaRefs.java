package com.elipair.church.domain.media;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 본문 마크다운의 media:{id} 추출 — 응답의 서명 주소 맵(mediaUrls)을 만드는 모든 도메인이 같은 규칙을 쓴다. */
public final class MediaRefs {

    // 숫자 경계는 DB 참조 정규식 media:{id}($|[^0-9])와 같다. 18자리 제한: long을 넘는 숫자는 id가 될 수 없다.
    private static final Pattern MEDIA_REF = Pattern.compile("media:(\\d{1,18})(?!\\d)");

    private MediaRefs() {}

    /** null·빈 문자열 → 빈 목록, 중복 제거, 등장 순서 유지. */
    public static List<Long> ids(String markdown) {
        if (markdown == null) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        Matcher m = MEDIA_REF.matcher(markdown);
        while (m.find()) {
            ids.add(Long.valueOf(m.group(1)));
        }
        return List.copyOf(ids);
    }
}
