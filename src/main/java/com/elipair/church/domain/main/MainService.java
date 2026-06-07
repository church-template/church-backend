package com.elipair.church.domain.main;

import com.elipair.church.domain.event.EventService;
import com.elipair.church.domain.main.dto.MainResponse;
import com.elipair.church.domain.notice.NoticeService;
import com.elipair.church.domain.sermon.SermonService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 메인페이지 통합 조회(스펙 §5.9). 설교·공지·다가오는 일정 카드를 조합한다.
 * Redis 캐싱(@Cacheable). 설교/공지/일정 CUD 시 각 서비스의 @CacheEvict("main")로 무효화된다.
 */
@Service
public class MainService {

    private static final int SERMON_COUNT = 3;
    private static final int NOTICE_COUNT = 3;
    private static final int EVENT_COUNT = 5;

    private final SermonService sermonService;
    private final NoticeService noticeService;
    private final EventService eventService;

    public MainService(SermonService sermonService, NoticeService noticeService, EventService eventService) {
        this.sermonService = sermonService;
        this.noticeService = noticeService;
        this.eventService = eventService;
    }

    @Cacheable("main")
    public MainResponse getMain() {
        return new MainResponse(
                sermonService
                        .list(null, null, null, null, null, null, PageRequest.of(0, SERMON_COUNT))
                        .getContent(),
                noticeService.list(null, null, PageRequest.of(0, NOTICE_COUNT)).getContent(),
                eventService.upcoming(EVENT_COUNT));
    }
}
