package org.openfinance.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.scheduler.DatabaseMaintenanceScheduler;
import org.openfinance.util.QueryPlanAnalyzer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

@DisplayName("SQLite maintenance is registered only for SQLite databases")
class DatabaseMaintenanceConfigurationTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(MaintenanceConfiguration.class)
                    .withBean(DataSource.class, () -> mock(DataSource.class))
                    .withBean(TaskScheduler.class, () -> mock(TaskScheduler.class));

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:sqlite::memory:", "jdbc:sqlite:/tmp/owner's finance.db"})
    void registersAllThreeMaintenanceTasksForSqlite(String url) {
        contextRunner
                .withPropertyValues("spring.datasource.url=" + url)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(DatabaseMaintenanceScheduler.class);
                            assertThat(
                                            context.getBean(
                                                            ScheduledAnnotationBeanPostProcessor
                                                                    .class)
                                                    .getScheduledTasks())
                                    .hasSize(3);
                            verifyNoInteractions(context.getBean(DataSource.class));
                        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:postgresql://localhost/finance", "jdbc:h2:mem:finance", ""})
    void neverRegistersMaintenanceTasksForOtherDatabases(String url) {
        contextRunner
                .withPropertyValues("spring.datasource.url=" + url)
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(DatabaseMaintenanceScheduler.class);
                            assertThat(
                                            context.getBean(
                                                            ScheduledAnnotationBeanPostProcessor
                                                                    .class)
                                                    .getScheduledTasks())
                                    .isEmpty();
                            verifyNoInteractions(context.getBean(DataSource.class));
                        });
    }

    @Test
    void doesNotAssumeSqliteWhenNoDatabaseUrlIsConfigured() {
        contextRunner.run(
                context -> {
                    assertThat(context).doesNotHaveBean(DatabaseMaintenanceScheduler.class);
                    verifyNoInteractions(context.getBean(DataSource.class));
                });
    }

    @Test
    void postgresStartupDoesNotRunSqliteHealthChecks() {
        new ApplicationContextRunner()
                .withUserConfiguration(DatabaseInitializer.class)
                .withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost/finance")
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(QueryPlanAnalyzer.class, () -> mock(QueryPlanAnalyzer.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(DatabaseInitializer.class);
                            verifyNoInteractions(
                                    context.getBean(DataSource.class),
                                    context.getBean(QueryPlanAnalyzer.class));
                        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableScheduling
    @Import(DatabaseMaintenanceScheduler.class)
    static class MaintenanceConfiguration {}
}
