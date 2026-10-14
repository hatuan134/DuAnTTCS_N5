package com.duanttcsn5.library.config;

import com.duanttcsn5.library.service.ReadyPickupNotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ReadyPickupNotificationConfig {
    // Resolve the schedule without looking up the service while Spring is creating it.
    @Bean(name="pickupLastDayCron")
    public String pickupLastDayCron(@Value("${app.pickup-email.last-day-send-time:07:00}") String sendTime) {
        return ReadyPickupNotificationService.lastDayCron(sendTime);
    }

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
    @Bean(name="pickupLastDayScheduler")
    public ThreadPoolTaskScheduler pickupLastDayScheduler() {
        var scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("pickup-last-day-");
        return scheduler;
    }
    @Bean(name="pickupNotificationScheduler")
    public ThreadPoolTaskScheduler pickupNotificationScheduler() {
        var scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("pickup-email-tick-");
        return scheduler;
    }
}
