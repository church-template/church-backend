package com.elipair.church.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @Scheduled 활성화(스펙 §9 조회수 주기 플러시). */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
