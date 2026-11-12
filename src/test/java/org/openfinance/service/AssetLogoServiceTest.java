package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.CacheConfig;
import org.openfinance.config.LogoFetchProperties;
import org.openfinance.dto.SymbolSearchResult;
import org.openfinance.entity.AssetType;
import org.openfinance.provider.MarketDataProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dynamic asset logos")
class AssetLogoServiceTest {
    private static final String URL = "https://s.yimg.com/lb/brands/new-company.png";
    private static final String LOGO = "data:image/png;base64,cHJvdmlkZXI=";

    @Mock private MarketDataProvider marketDataProvider;
    @Mock private LogoFetchService logoFetchService;
    private final LogoFetchProperties properties = new LogoFetchProperties();
    private AssetLogoService service;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        service = new AssetLogoServiceImpl(marketDataProvider, logoFetchService, properties);
    }

    @ParameterizedTest
    @CsvSource({
        "STOCK, NEWCO.PA, NEWCO.PA, EQUITY",
        "CRYPTO, NEAR, NEAR-USD, CRYPTOCURRENCY",
        "CRYPTO, NEAR-EUR, NEAR-USD, CRYPTOCURRENCY",
        "ETF, NEWETF.DE, NEWETF.DE, ETF",
        "MUTUAL_FUND, NEWFUND, NEWFUND, MUTUALFUND"
    })
    void resolvesProviderMetadataWithoutASymbolCatalog(
            AssetType type, String symbol, String lookupSymbol, String quoteType) {
        when(marketDataProvider.searchSymbol(lookupSymbol))
                .thenReturn(List.of(result(lookupSymbol, quoteType)));
        when(logoFetchService.fetchMarketLogo(URL)).thenReturn(Optional.of(LOGO));

        assertThat(service.resolveLogo(type, " " + symbol.toLowerCase(Locale.ROOT) + " ", "Asset"))
                .isEqualTo(LOGO);
    }

    @Test
    void skipsFuzzyHitsOtherExchangesAndOtherInstrumentTypes() {
        when(marketDataProvider.searchSymbol("NEWCO.PA"))
                .thenReturn(List.of(result("NEWCO", "EQUITY"), result("NEWCO.PA", "ETF")));
        assertThat(service.resolveLogo(AssetType.STOCK, "NEWCO.PA", "Asset")).isNull();
        verifyNoInteractions(logoFetchService);
    }

    @Test
    void reusesBrandResolverForAnUnlistedCompany() {
        when(logoFetchService.fetchLogo("Local company")).thenReturn(Optional.of(LOGO));
        assertThat(service.resolveLogo(AssetType.STOCK, null, "Local company")).isEqualTo(LOGO);
        verifyNoInteractions(marketDataProvider);
    }

    @Test
    void fallsBackToTheExistingBrandResolverWhenTheProviderHasNoCompanyLogo() {
        when(marketDataProvider.searchSymbol("BNP.PA"))
                .thenReturn(
                        List.of(
                                SymbolSearchResult.builder()
                                        .symbol("BNP.PA")
                                        .type("EQUITY")
                                        .build()));
        when(logoFetchService.fetchMarketLogo(null)).thenReturn(Optional.empty());
        when(logoFetchService.fetchLogo("BNP Paribas")).thenReturn(Optional.of(LOGO));
        assertThat(service.resolveLogo(AssetType.STOCK, "BNP.PA", "BNP Paribas")).isEqualTo(LOGO);
    }

    @Test
    void neverCallsProvidersWhenLogoFetchingIsDisabled() {
        properties.setEnabled(false);
        assertThat(service.resolveLogo(AssetType.STOCK, "NEWCO", "Company")).isNull();
        assertThat(service.resolveLogo(AssetType.STOCK, null, "Company")).isNull();
        verifyNoInteractions(marketDataProvider, logoFetchService);
    }

    @Test
    void leavesPhysicalAssetsToTheirExistingCoverImages() {
        assertThat(service.resolveLogo(AssetType.VEHICLE, null, "Car")).isNull();
        verifyNoInteractions(marketDataProvider, logoFetchService);
    }

    @Test
    void providerFailureDoesNotFailAssetRendering() {
        when(marketDataProvider.searchSymbol("NEWCO"))
                .thenThrow(new IllegalStateException("Provider temporarily unavailable"));
        assertThat(service.resolveLogo(AssetType.STOCK, "NEWCO", "Company")).isNull();
        verifyNoInteractions(logoFetchService);
    }

    @Test
    void cachesBothResolvedAndMissingLogos() {
        when(marketDataProvider.searchSymbol("NEWCO"))
                .thenReturn(List.of(result("NEWCO", "EQUITY")));
        when(logoFetchService.fetchMarketLogo(URL)).thenReturn(Optional.of(LOGO));
        when(marketDataProvider.searchSymbol("MISSING")).thenReturn(List.of());
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            context.register(CacheConfig.class);
            context.registerBean(MarketDataProvider.class, () -> marketDataProvider);
            context.registerBean(LogoFetchService.class, () -> logoFetchService);
            context.registerBean(LogoFetchProperties.class, () -> properties);
            context.registerBean(AssetLogoServiceImpl.class);
            context.refresh();
            AssetLogoService cached = context.getBean(AssetLogoService.class);
            for (int i = 0; i < 2; i++) {
                assertThat(cached.resolveLogo(AssetType.STOCK, "NEWCO", "Company")).isEqualTo(LOGO);
                assertThat(cached.resolveLogo(AssetType.STOCK, "MISSING", "Missing")).isNull();
            }
        }
        verify(marketDataProvider, times(1)).searchSymbol("NEWCO");
        verify(marketDataProvider, times(1)).searchSymbol("MISSING");
        verify(logoFetchService, times(1)).fetchMarketLogo(URL);
    }

    private SymbolSearchResult result(String symbol, String type) {
        return SymbolSearchResult.builder().symbol(symbol).type(type).logoUrl(URL).build();
    }
}
