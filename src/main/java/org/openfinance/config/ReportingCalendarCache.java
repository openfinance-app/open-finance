package org.openfinance.config;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.openfinance.util.ReportingClock;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;

/** Calendar-sensitive entries expire logically at local midnight, independently of their TTL. */
final class ReportingCalendarCache implements Cache {
    private final CaffeineCache delegate;

    ReportingCalendarCache(CaffeineCache delegate) {
        this.delegate = delegate;
    }

    private record Key(Object logicalKey, LocalDate date, ZoneId zone) {}

    private Key key(Object logicalKey) {
        return new Key(logicalKey, ReportingClock.today(), ReportingClock.zone());
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        return delegate.get(key(key));
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        return delegate.get(key(key), type);
    }

    @Override
    public <T> T get(Object key, Callable<T> loader) {
        return delegate.get(key(key), loader);
    }

    @Override
    public void put(Object key, Object value) {
        delegate.put(key(key), value);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        return delegate.putIfAbsent(key(key), value);
    }

    @Override
    public void evict(Object key) {
        evictIfPresent(key);
    }

    @Override
    public boolean evictIfPresent(Object key) {
        // Existing domain invalidations use logical keys. Invalidate every calendar variant so
        // switching devices or changing timezone cannot resurrect an older balance.
        return delegate.getNativeCache()
                .asMap()
                .keySet()
                .removeIf(
                        candidate ->
                                candidate instanceof Key entry
                                        && Objects.equals(entry.logicalKey(), key));
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public boolean invalidate() {
        return delegate.invalidate();
    }
}
