package org.openfinance.service.history;

import org.openfinance.entity.EntityType;

/** One outer action groups nested service calls; always removed at the transaction boundary. */
public final class HistoryActionContext implements AutoCloseable {
    private static final ThreadLocal<HistoryActionContext> CURRENT = new ThreadLocal<>();
    private final EntityType entity;
    private Long id;
    private String label;

    private HistoryActionContext(EntityType entity) {
        this.entity = entity;
    }

    public static boolean active() {
        return CURRENT.get() != null;
    }

    public static HistoryActionContext open(EntityType entity) {
        HistoryActionContext context = new HistoryActionContext(entity);
        CURRENT.set(context);
        return context;
    }

    public static boolean record(EntityType entity, Long id, String label) {
        HistoryActionContext context = CURRENT.get();
        if (context == null) return false;
        if (entity == context.entity && context.id == null) {
            context.id = id;
            context.label = label;
        }
        return true;
    }

    public Long id() {
        return id;
    }

    public String label() {
        return label;
    }

    @Override
    public void close() {
        CURRENT.remove();
    }
}
