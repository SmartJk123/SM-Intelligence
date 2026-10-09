package com.smi.identity_service.health;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs the service health monitor on its schedule. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
