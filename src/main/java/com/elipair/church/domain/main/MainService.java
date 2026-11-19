package com.elipair.church.domain.main;

import com.elipair.church.domain.event.EventService;
import com.elipair.church.domain.main.dto.MainResponse;
import com.elipair.church.domain.notice.NoticeService;
import com.elipair.church.domain.sermon.SermonService;
import com.elipair.church.domain.visibility.ContentDomain;
import com.elipair.church.domain.visibility.VisibilityService;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 메인페이지 통합 조회(스펙 §5.9). 설교·공지·다가오는 일정 카드를 조합한다.
 * Redis 캐싱(@Cacheable). 설교/공지/일정 CUD·공개 범위 저장 시 각 서비스의 @CacheEvict("main")로 무효화된다.
 * 캐시 키가 교회 id뿐이라 보는 사람과 무관하게 "방문자가 볼 수 있는 것"만 담는다(설계 5.4·6.6-1, #95):
 * 글 단위 교인 전용 설교는 빼고, 메뉴가 교인만인 공지·일정은 비운다. 설교 메뉴가 교인만이어도 카드는 낸다(#53의 결정).
 */
@Service
public class MainService {

    private static final int SERMON_COUNT = 3;
    private static final int NOTICE_COUNT = 3;
    private static final int EVENT_COUNT = 5;

    private final SermonService sermonService;
    private final NoticeService noticeService;
    private final EventService eventService;
    private final VisibilityService visibility;

    public MainService(
            SermonService sermonService,
            NoticeService noticeService,
            EventService eventService,
            VisibilityService visibility) {
        this.sermonService = sermonService;
        this.noticeService = noticeService;
        this.eventService = eventService;
        this.visibility = visibility;
    }

    @Cacheable("main")
    public MainResponse getMain() {
        return new MainResponse(
                sermonService
                        .list(null, null, null, null, null, null, false, null, PageRequest.of(0, SERMON_COUNT))
                        .getContent(),
                visibility.isPublic(ContentDomain.NOTICE)
                        ? noticeService
                                .list(null, null, PageRequest.of(0, NOTICE_COUNT))
                                .getContent()
                        : List.of(),
                visibility.isPublic(ContentDomain.EVENT) ? eventService.upcoming(EVENT_COUNT) : List.of());
    }
}
