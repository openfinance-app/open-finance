package org.openfinance.util;

import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryPlanAnalyzerTest {

    @Mock private EntityManager entityManager;

    @InjectMocks private QueryPlanAnalyzer analyzer;

    @Test
    @DisplayName("should not fail when analyzeQuery is called")
    void shouldNotFailWhenAnalyzeQueryIsCalled() {
        // Since we can't easily mock the static Logger's isDebugEnabled(),
        // we just verify that calling the method doesn't throw an exception.
        // If debug is disabled, it returns immediately.
        // If debug is enabled, it tries to use the entityManager.

        analyzer.analyzeQuery("test-label", "SELECT * FROM test");

        // No assertion needed other than no exception thrown
    }

    @Test
    @DisplayName("should not fail when analyzeCriticalQueries is called")
    void shouldNotFailWhenAnalyzeCriticalQueriesIsCalled() {
        analyzer.analyzeCriticalQueries();
        // No assertion needed other than no exception thrown
    }
}
