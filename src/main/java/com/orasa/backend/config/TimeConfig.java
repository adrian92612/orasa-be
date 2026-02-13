package com.orasa.backend.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {
  
  public static final String MANILA_ZONE_ID = "Asia/Manila";
  public static final ZoneId PH_ZONE = ZoneId.of(MANILA_ZONE_ID);

  @Bean
  public Clock phClock() {
    return Clock.system(PH_ZONE);
  }
}
