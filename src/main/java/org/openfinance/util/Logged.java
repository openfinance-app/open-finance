package org.openfinance.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enable explicit logging of method execution. Methods annotated with @Logged will
 * have their entry, exit, and exceptions logged automatically.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @Logged
 * public void importantMethod() {
 *     // method implementation
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Logged {}
