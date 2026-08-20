package org.openfinance.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public class AssetNullConditionTest {

    @Autowired private AssetRepository assetRepository;

    @Autowired private UserRepository userRepository;

    @Test
    public void testSaveAssetWithNullCondition() {
        User user =
                User.builder()
                        .username("testuser")
                        .email("test@example.com")
                        .passwordHash("hashedpassword")
                        .masterPasswordSalt("salt")
                        .baseCurrency("USD")
                        .build();
        user = userRepository.save(user);

        Asset asset =
                Asset.builder()
                        .userId(user.getId())
                        .name("Test Asset")
                        .type(AssetType.STOCK)
                        .quantity(BigDecimal.ONE)
                        .purchasePrice(BigDecimal.TEN)
                        .currentPrice(BigDecimal.TEN)
                        .currency("USD")
                        .purchaseDate(LocalDate.now())
                        .build();

        assetRepository.save(asset);
        assetRepository.flush();
    }

    @Test
    public void testSaveAssetWithJpyCurrency() {
        User user =
                User.builder()
                        .username("jpyuser")
                        .email("jpy@example.com")
                        .passwordHash("hashedpassword")
                        .masterPasswordSalt("salt")
                        .baseCurrency("JPY")
                        .build();
        user = userRepository.save(user);

        Asset asset =
                Asset.builder()
                        .userId(user.getId())
                        .name("JPY Asset")
                        .type(AssetType.STOCK)
                        .quantity(BigDecimal.ONE)
                        .purchasePrice(new BigDecimal("10000"))
                        .currentPrice(new BigDecimal("10500"))
                        .currency("JPY")
                        .purchaseDate(LocalDate.now())
                        .build();

        assetRepository.save(asset);
        assetRepository.flush();

        var saved = assetRepository.findById(asset.getId());
        assert saved.isPresent();
        assert "JPY".equals(saved.get().getCurrency());
        assert saved.get().getPurchasePrice().scale() == 0;
    }
}
