// filepath: src/main/java/org/openfinance/config/SpringContextHolder.java

package org.openfinance.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Static holder for the Spring {@link ApplicationContext}.
 *
 * <p>Allows non-Spring-managed objects (such as JPA {@link jakarta.persistence.AttributeConverter}
 * instances created by Hibernate) to look up Spring beans.
 *
 * <p>This is intentionally a narrow bridge — only JPA converters should use it.
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    @SuppressWarnings("java:S2696") // static field set from instance method — intentional
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
        SpringContextHolder.context = applicationContext;
    }

    /**
     * Returns the Spring ApplicationContext, or {@code null} if it has not been set yet (e.g.
     * during early startup or in unit tests).
     */
    public static ApplicationContext getContext() {
        return context;
    }

    /**
     * Retrieves a bean by type from the ApplicationContext.
     *
     * @param clazz the bean class
     * @return the bean instance
     * @throws IllegalStateException if the context is not yet available
     */
    public static <T> T getBean(Class<T> clazz) {
        if (context == null) {
            throw new IllegalStateException(
                    "Spring ApplicationContext is not available yet. "
                            + "Ensure SpringContextHolder is initialized before accessing beans.");
        }
        return context.getBean(clazz);
    }
}
