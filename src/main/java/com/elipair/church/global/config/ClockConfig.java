package com.elipair.church.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "오늘" 판정용 Clock(통독 챌린지 설계 §5). 시간대는 APP_TIMEZONE env(기본 Asia/Seoul) —
 * 멀티처치 템플릿 규율상 env 주입. 테스트는 Clock.fixed로 대체해 날짜를 고정한다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.timezone}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
