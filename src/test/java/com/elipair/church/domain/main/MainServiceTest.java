package com.elipair.church.domain.main;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.event.EventService;
import com.elipair.church.domain.event.dto.EventCardResponse;
import com.elipair.church.domain.main.dto.MainResponse;
import com.elipair.church.domain.notice.NoticeService;
import com.elipair.church.domain.notice.dto.NoticeCardResponse;
import com.elipair.church.domain.sermon.SermonService;
import com.elipair.church.domain.sermon.dto.SermonCardResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class MainServiceTest {

    @Test
    void getMain_collects_sermons_notices_and_upcoming_events() {
        SermonService sermonService = mock(SermonService.class);
        NoticeService noticeService = mock(NoticeService.class);
        EventService eventService = mock(EventService.class);

        SermonCardResponse sermon =
                new SermonCardResponse(1L, "설교", "김목사", "s", "마5", LocalDate.now(), 0L, List.of(), "관리자");
        NoticeCardResponse notice = new NoticeCardResponse(2L, "공지", false, 0L, LocalDateTime.now(), List.of(), "관리자");
        EventCardResponse event =
                new EventCardResponse(3L, "행사", "본당", LocalDateTime.now(), LocalDateTime.now(), false, List.of());

        when(sermonService.list(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sermon)));
        when(noticeService.list(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(notice)));
        when(eventService.upcoming(5)).thenReturn(List.of(event));

        MainService service = new MainService(sermonService, noticeService, eventService);
        MainResponse result = service.getMain();

        assertThat(result.sermons()).containsExactly(sermon);
        assertThat(result.notices()).containsExactly(notice);
        assertThat(result.upcomingEvents()).containsExactly(event);
    }
}
