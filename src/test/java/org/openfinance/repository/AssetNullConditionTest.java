package org.openfinance.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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
        System.out.println("TEST PASSED!");
    }
}
