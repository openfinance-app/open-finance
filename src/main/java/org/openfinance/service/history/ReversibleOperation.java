package org.openfinance.service.history;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.OperationType;

/** Declares a user action whose complete persisted changes are captured in its transaction. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReversibleOperation {
    EntityType entity();

    OperationType operation();

    int userArgument();

    int idArgument() default -1;

    boolean requiresNew() default false;
}
