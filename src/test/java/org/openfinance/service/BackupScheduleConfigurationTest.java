package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.repository.BackupRepository;
import org.openfinance.repository.UserRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.support.CronExpression;

@DisplayName("Automatic backups default to daily and respect schedule overrides")
class BackupScheduleConfigurationTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(BackupConfiguration.class)
                    .withBean(BackupRepository.class, () -> mock(BackupRepository.class))
                    .withBean(UserRepository.class, () -> mock(UserRepository.class))
                    .withBean(UserBackupArchive.class, () -> mock(UserBackupArchive.class))
                    .withBean(TaskScheduler.class, () -> mock(TaskScheduler.class));

    @Test
    void schedulesBackupsAtTwoEveryDayIncludingWeekdays() {
        contextRunner.run(
                context -> {
                    CronExpression cron =
                            cronFor(context.getBean(ScheduledAnnotationBeanPostProcessor.class));
                    LocalDateTime mondayAtTwo = LocalDateTime.of(2026, 11, 16, 2, 0);
                    assertThat(cron.next(mondayAtTwo.minusMinutes(1))).isEqualTo(mondayAtTwo);
                    assertThat(cron.next(mondayAtTwo)).isEqualTo(mondayAtTwo.plusDays(1));
                    verifyNoInteractions(
                            context.getBean(UserRepository.class),
                            context.getBean(UserBackupArchive.class));
                });
    }

    @Test
    void preservesCustomBackupSchedules() {
        contextRunner
                .withPropertyValues("app.backup.schedule.cron=0 30 4 * * MON")
                .run(
                        context -> {
                            CronExpression cron =
                                    cronFor(
                                            context.getBean(
                                                    ScheduledAnnotationBeanPostProcessor.class));
                            LocalDateTime monday = LocalDateTime.of(2026, 11, 16, 4, 30);
                            assertThat(cron.next(monday.minusMinutes(1))).isEqualTo(monday);
                            assertThat(cron.next(monday)).isEqualTo(monday.plusWeeks(1));
                        });
    }

    @Test
    void acceptsAnExplicitlyDisabledCron() {
        contextRunner
                .withPropertyValues("app.backup.schedule.cron=-")
                .run(
                        context ->
                                assertThat(
                                                context.getBean(
                                                                ScheduledAnnotationBeanPostProcessor
                                                                        .class)
                                                        .getScheduledTasks())
                                        .isEmpty());
    }

    private CronExpression cronFor(ScheduledAnnotationBeanPostProcessor processor) {
        assertThat(processor.getScheduledTasks()).hasSize(1);
        CronTask task = (CronTask) processor.getScheduledTasks().iterator().next().getTask();
        return CronExpression.parse(task.getExpression());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableScheduling
    @Import(BackupService.class)
    static class BackupConfiguration {}
}
