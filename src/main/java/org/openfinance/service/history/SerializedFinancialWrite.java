package org.openfinance.service.history;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Serializes non-reversible dependency changes with reversals of their parent records. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SerializedFinancialWrite {
    int userArgument();

    boolean suppressHistory() default false;
}
