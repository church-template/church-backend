package com.elipair.church.domain.main;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.event.Event;
import com.elipair.church.domain.event.EventRepository;
import com.elipair.church.domain.notice.Notice;
import com.elipair.church.domain.notice.NoticeRepository;
import com.elipair.church.domain.sermon.Sermon;
import com.elipair.church.domain.sermon.SermonRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MainApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SermonRepository sermonRepository;

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private EventRepository eventRepository;

    @AfterEach
    void cleanup() {
        sermonRepository.deleteAll();
        noticeRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    void main_is_public_and_returns_three_sections() throws Exception {
        sermonRepository.saveAndFlush(
                Sermon.create("설교1", "김목사", "s", "마5", "본문", null, null, LocalDate.of(2026, 6, 1)));
        noticeRepository.saveAndFlush(Notice.create("공지1", "본문", false));
        eventRepository.saveAndFlush(Event.create(
                "다가오는행사",
                "본문",
                "본당",
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(1),
                false));

        mockMvc.perform(get("/api/main"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sermons[0].title").value("설교1"))
                .andExpect(jsonPath("$.sermons[0].content").doesNotExist())
                .andExpect(jsonPath("$.notices[0].title").value("공지1"))
                .andExpect(jsonPath("$.upcomingEvents[0].title").value("다가오는행사"));
    }
}
