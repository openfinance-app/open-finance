package org.openfinance.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Registers SQLite-only infrastructure without opening a database connection. */
public final class SqliteDatabaseCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String databaseUrl = context.getEnvironment().getProperty("spring.datasource.url");
        return databaseUrl != null && databaseUrl.startsWith("jdbc:sqlite:");
    }
}
