package com.smartuser.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.smartuser.scheduler.config.SchedulerMonitorProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SchedulerMonitorProperties.class)
public class SchedulerDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerDemoApplication.class, args);
    }
}

