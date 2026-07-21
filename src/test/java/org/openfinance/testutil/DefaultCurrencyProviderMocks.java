package org.openfinance.testutil;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;
import org.openfinance.service.DefaultCurrencyProvider;

/**
 * Shared Mockito stubbing for {@link DefaultCurrencyProvider} in unit tests.
 *
 * <p>Services now depend on {@link DefaultCurrencyProvider} (the single source of truth for the
 * application default currency). Unit tests using {@code @InjectMocks} therefore receive a mocked
 * provider; these helpers make the mock behave like the real implementation.
 *
 * <p>The historical {@code "USD"} default is preserved for tests so existing assertions remain
 * valid — production code defaults to EUR via {@code application.exchange-rates.base-currency}.
 */
public final class DefaultCurrencyProviderMocks {

    private static final String TEST_DEFAULT = "USD";

    private DefaultCurrencyProviderMocks() {}

    /**
     * Stubs the provider to echo any non-blank currency and otherwise fall back to {@code "USD"}.
     * {@code resolveForUser} always returns {@code "USD"} — use {@link
     * #stub(DefaultCurrencyProvider, UserRepository)} when the test relies on a user's configured
     * currency.
     */
    public static void stub(DefaultCurrencyProvider provider) {
        lenient().when(provider.getDefaultCurrency()).thenReturn(TEST_DEFAULT);
        lenient()
                .when(provider.resolve(any()))
                .thenAnswer(DefaultCurrencyProviderMocks::echoOrDefault);
        lenient().when(provider.resolveForUser(any())).thenReturn(TEST_DEFAULT);
    }

    /**
     * Stubs the provider to echo any non-blank currency (otherwise {@code "USD"}) and to resolve a
     * user's currency via the supplied (mocked) {@link UserRepository}, mirroring the real
     * provider.
     */
    public static void stub(DefaultCurrencyProvider provider, UserRepository userRepository) {
        lenient().when(provider.getDefaultCurrency()).thenReturn(TEST_DEFAULT);
        lenient()
                .when(provider.resolve(any()))
                .thenAnswer(DefaultCurrencyProviderMocks::echoOrDefault);
        lenient()
                .when(provider.resolveForUser(any()))
                .thenAnswer(
                        inv -> {
                            Long uid = inv.getArgument(0);
                            if (uid == null) {
                                return TEST_DEFAULT;
                            }
                            return userRepository
                                    .findById(uid)
                                    .map(User::getBaseCurrency)
                                    .filter(c -> c != null && !c.isBlank())
                                    .orElse(TEST_DEFAULT);
                        });
    }

    private static String echoOrDefault(org.mockito.invocation.InvocationOnMock inv) {
        String currency = inv.getArgument(0);
        return (currency != null && !currency.isBlank()) ? currency : TEST_DEFAULT;
    }
}
