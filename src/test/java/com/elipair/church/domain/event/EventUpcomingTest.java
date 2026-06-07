package com.elipair.church.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EventUpcomingTest {

    @Autowired
    private EventService service;

    @Autowired
    private EventRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    private void save(String title, LocalDateTime start) {
        repository.saveAndFlush(Event.create(title, "본문", "본당", start, start.plusHours(1), false));
    }

    @Test
    void upcoming_excludes_past_orders_ascending_and_limits() {
        LocalDateTime now = LocalDateTime.now();
        save("과거", now.minusDays(1));
        save("내일", now.plusDays(1));
        save("모레", now.plusDays(2));
        save("다음주", now.plusDays(7));

        List<?> result = service.upcoming(2);

        assertThat(result).hasSize(2);
        assertThat(result).extracting("title").containsExactly("내일", "모레");
    }
}
