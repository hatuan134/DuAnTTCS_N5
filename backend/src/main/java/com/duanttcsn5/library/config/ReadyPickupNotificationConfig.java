package com.duanttcsn5.library.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ReadyPickupNotificationConfig {
    @Bean(name="pickupNotificationExecutor")
    public ThreadPoolTaskExecutor pickupNotificationExecutor() {
        var executor=new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);executor.setMaxPoolSize(4);executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("pickup-email-");
        executor.setWaitForTasksToCompleteOnShutdown(true);executor.setAwaitTerminationSeconds(30);
        return executor;
    }
    // Preserve a separate default scheduler for the pre-existing daily/retry jobs.
    @Bean(name="taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        var scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("scheduling-");
        return scheduler;
    }
    @Bean(name="pickupNotificationScheduler")
    public ThreadPoolTaskScheduler pickupNotificationScheduler() {
        var scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("pickup-email-tick-");
        return scheduler;
    }
}
