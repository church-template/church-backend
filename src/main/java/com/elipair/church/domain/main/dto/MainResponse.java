package com.elipair.church.domain.main.dto;

import com.elipair.church.domain.event.dto.EventCardResponse;
import com.elipair.church.domain.notice.dto.NoticeCardResponse;
import com.elipair.church.domain.sermon.dto.SermonCardResponse;
import java.io.Serializable;
import java.util.List;

/** 메인페이지 통합 응답(스펙 §5.9). 카드 메타만(본문 제외). Redis 캐시 직렬화 대상이라 Serializable. */
public record MainResponse(
        List<SermonCardResponse> sermons, List<NoticeCardResponse> notices, List<EventCardResponse> upcomingEvents)
        implements Serializable {}
