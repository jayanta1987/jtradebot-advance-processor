package com.jtradebot.processor.manager;

import com.jtradebot.processor.model.enums.SchedulerNameEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@Slf4j
public class ScheduleManager {

    private final TaskScheduler taskScheduler;
    private final Map<SchedulerNameEnum, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<SchedulerNameEnum, Object> taskDataMap = new ConcurrentHashMap<>(); // Dynamic data map

    public ScheduleManager(ThreadPoolTaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    public boolean isTaskRunning(SchedulerNameEnum taskName) {
        ScheduledFuture<?> scheduledTask = scheduledTasks.get(taskName);
        return scheduledTask != null && !scheduledTask.isCancelled();
    }

    public void startTaskWithFixedRate(SchedulerNameEnum taskName, Runnable task, long period, Object initialData) {
        log.info("Starting task: {} with period: {}", taskName, period);
        ScheduledFuture<?> scheduledTask = scheduledTasks.get(taskName);
        if (scheduledTask == null || scheduledTask.isCancelled()) {
            scheduledTask = taskScheduler.scheduleAtFixedRate(task, period);
            scheduledTasks.put(taskName, scheduledTask);
        }
        if (initialData != null) {
            // Add initial data for the task
            taskDataMap.put(taskName, initialData);
        }

    }

    public void startTaskWithCron(SchedulerNameEnum taskName, Runnable task, String cronExpression) {
        ScheduledFuture<?> scheduledTask = scheduledTasks.get(taskName);
        if (scheduledTask == null || scheduledTask.isCancelled()) {
            scheduledTask = taskScheduler.schedule(task, new CronTrigger(cronExpression));
            scheduledTasks.put(taskName, scheduledTask);
        }
    }

    public void stopTask(SchedulerNameEnum taskName) {
        log.info("Stopping task: {}", taskName);
        ScheduledFuture<?> scheduledTask = scheduledTasks.get(taskName);
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(false);
            scheduledTasks.remove(taskName);
        }
        taskDataMap.remove(taskName); // Remove dynamic data for the task
    }

    public void stopAllTasks() {
        scheduledTasks.forEach((taskName, scheduledTask) -> {
            if (scheduledTask != null && !scheduledTask.isCancelled()) {
                scheduledTask.cancel(false);
            }
        });
        scheduledTasks.clear();
        taskDataMap.clear(); // Clear all dynamic data
    }

    public Object getTaskData(SchedulerNameEnum taskName) {
        return taskDataMap.get(taskName); // Access dynamic data during the task execution
    }

    public void updateTaskData(SchedulerNameEnum taskName, Object newData) {
        taskDataMap.put(taskName, newData); // Update dynamic data if needed during task execution
    }
}