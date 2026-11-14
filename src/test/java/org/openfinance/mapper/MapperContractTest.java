package org.openfinance.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.openfinance.dto.AccountRequest;
import org.openfinance.dto.AccountResponse;
import org.openfinance.dto.BudgetRequest;
import org.openfinance.dto.BudgetResponse;
import org.openfinance.dto.CategoryRequest;
import org.openfinance.dto.CategoryResponse;
import org.openfinance.dto.RealEstatePropertyRequest;
import org.openfinance.dto.RealEstatePropertyResponse;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.dto.TransactionResponse;
import org.openfinance.dto.UserResponse;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.Budget;
import org.openfinance.entity.BudgetPeriod;
import org.openfinance.entity.Category;
import org.openfinance.entity.PropertyType;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;

/**
 * Contract tests for the MapStruct mappers that had no dedicated suite. The focus is the
 * security/integrity contract, not copy fidelity alone: create must not accept identity fields,
 * update must not overwrite server-owned fields (balances, system flags, soft-delete, FX data),
 * nulls in an update must not wipe stored values, and encrypted fields must not leak through
 * response mappings.
 */
@DisplayName("Mapper contracts (mass-assignment and encryption boundaries)")
class MapperContractTest {

    private final AccountMapper accountMapper = Mappers.getMapper(AccountMapper.class);
    private final CategoryMapper categoryMapper = Mappers.getMapper(CategoryMapper.class);
    private final BudgetMapper budgetMapper = Mappers.getMapper(BudgetMapper.class);
    private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);
    private final TransactionMapper transactionMapper = Mappers.getMapper(TransactionMapper.class);
    private final RealEstateMapper realEstateMapper = Mappers.getMapper(RealEstateMapper.class);

    @Nested
    @DisplayName("AccountMapper")
    class AccountMapping {

        @Test
        @DisplayName(
                "Create seeds balance and openingBalance from initialBalance, active by default")
        void createSeedsBalances() {
            AccountRequest request =
                    AccountRequest.builder()
                            .name("Checking")
                            .type(AccountType.CHECKING)
                            .currency("EUR")
                            .initialBalance(new BigDecimal("1500.25"))
                            .accountNumber("FR76 1234")
                            .openingDate(LocalDate.of(2025, 3, 1))
                            .build();

            Account entity = accountMapper.toEntity(request);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getUserId()).isNull();
            assertThat(entity.getBalance()).isEqualByComparingTo("1500.25");
            assertThat(entity.getOpeningBalance()).isEqualByComparingTo("1500.25");
            assertThat(entity.getIsActive()).isTrue();
            assertThat(entity.getIsInterestEnabled()).isFalse(); // default when omitted
            assertThat(entity.getAccountNumber()).isEqualTo("FR76 1234");
            assertThat(entity.getOpeningDate()).isEqualTo(LocalDate.of(2025, 3, 1));
            assertThat(entity.getCreatedAt()).isNull();
        }

        @Test
        @DisplayName("Update cannot change balances or active flag and ignores nulls")
        void updatePreservesServerOwnedFields() {
            Account existing =
                    Account.builder()
                            .id(5L)
                            .userId(9L)
                            .name("Old name")
                            .balance(new BigDecimal("999"))
                            .openingBalance(new BigDecimal("100"))
                            .isActive(false)
                            .description("keep me")
                            .build();
            AccountRequest request =
                    AccountRequest.builder()
                            .name("New name")
                            .initialBalance(new BigDecimal("1000000"))
                            .build();

            accountMapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getName()).isEqualTo("New name");
            assertThat(existing.getBalance()).isEqualByComparingTo("999");
            assertThat(existing.getOpeningBalance()).isEqualByComparingTo("100");
            assertThat(existing.getIsActive()).isFalse();
            assertThat(existing.getDescription()).isEqualTo("keep me");
            assertThat(existing.getId()).isEqualTo(5L);
            assertThat(existing.getUserId()).isEqualTo(9L);
        }

        @Test
        @DisplayName("Response carries the stored balance and identity fields")
        void responseMapping() {
            Account account =
                    Account.builder()
                            .id(3L)
                            .name("Savings")
                            .type(AccountType.SAVINGS)
                            .currency("USD")
                            .balance(new BigDecimal("42.10"))
                            .isActive(true)
                            .build();
            AccountResponse response = accountMapper.toResponse(account);
            assertThat(response.getId()).isEqualTo(3L);
            assertThat(response.getBalance()).isEqualByComparingTo("42.10");
            assertThat(response.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("Null inputs map to null / no-op instead of throwing")
        void nullInputs() {
            assertThat(accountMapper.toEntity(null)).isNull();
            assertThat(accountMapper.toResponse(null)).isNull();
            Account existing = Account.builder().name("x").build();
            accountMapper.updateEntityFromRequest(null, existing);
            assertThat(existing.getName()).isEqualTo("x");
        }
    }

    @Nested
    @DisplayName("CategoryMapper")
    class CategoryMapping {

        @Test
        @DisplayName("Create always produces a non-system category without nameKey")
        void createIsNeverSystem() {
            CategoryRequest request =
                    CategoryRequest.builder()
                            .name("Groceries")
                            .parentId(2L)
                            .icon("cart")
                            .color("#0f0")
                            .build();
            Category entity = categoryMapper.toEntity(request);
            assertThat(entity.getId()).isNull();
            assertThat(entity.getUserId()).isNull();
            assertThat(entity.getIsSystem()).isFalse();
            assertThat(entity.getNameKey()).isNull();
            assertThat(entity.getParentId()).isEqualTo(2L);
            assertThat(entity.getParent()).isNull();
        }

        @Test
        @DisplayName("Update cannot strip the system flag or nameKey and ignores nulls")
        void updatePreservesSystemFlag() {
            Category system =
                    Category.builder()
                            .id(1L)
                            .userId(9L)
                            .name("Salary")
                            .isSystem(true)
                            .nameKey("category.income.salary")
                            .color("#00f")
                            .build();
            categoryMapper.updateEntityFromRequest(
                    CategoryRequest.builder().icon("cash").build(), system);
            assertThat(system.getIcon()).isEqualTo("cash");
            assertThat(system.getName()).isEqualTo("Salary");
            assertThat(system.getIsSystem()).isTrue();
            assertThat(system.getNameKey()).isEqualTo("category.income.salary");
            assertThat(system.getColor()).isEqualTo("#00f");
        }

        @Test
        @DisplayName("Response leaves parentName and subcategoryCount for the service layer")
        void responseLeavesDenormalizedFields() {
            Category category =
                    Category.builder().id(4L).name("Rent").parentId(2L).isSystem(false).build();
            CategoryResponse response = categoryMapper.toResponse(category);
            assertThat(response.getParentId()).isEqualTo(2L);
            assertThat(response.getParentName()).isNull();
            assertThat(response.getSubcategoryCount()).isNull();
            assertThat(categoryMapper.toResponse(null)).isNull();
            assertThat(categoryMapper.toEntity(null)).isNull();
        }
    }

    @Nested
    @DisplayName("BudgetMapper")
    class BudgetMapping {

        @Test
        @DisplayName(
                "Create converts the amount to its exact plain string for the encrypted column")
        void createConvertsAmountExactly() {
            BudgetRequest request =
                    BudgetRequest.builder()
                            .categoryId(7L)
                            .amount(new BigDecimal("100.00"))
                            .currency("EUR")
                            .period(BudgetPeriod.MONTHLY)
                            .startDate(LocalDate.of(2026, 1, 1))
                            .rollover(true)
                            .build();
            Budget entity = budgetMapper.toEntity(request);
            assertThat(entity.getId()).isNull();
            assertThat(entity.getUserId()).isNull();
            assertThat(entity.getAmount()).isEqualTo("100.00"); // scale preserved, not "1E+2"
            assertThat(entity.getCategory()).isNull();
            assertThat(entity.getCategoryId()).isEqualTo(7L);
        }

        @Test
        @DisplayName("Null amount does not NPE on create or wipe the stored amount on update")
        void nullAmountIsSafe() {
            assertThat(budgetMapper.toEntity(BudgetRequest.builder().build()).getAmount()).isNull();

            Budget existing = Budget.builder().id(1L).amount("250.00").currency("EUR").build();
            budgetMapper.updateEntityFromRequest(
                    BudgetRequest.builder().notes("revised").build(), existing);
            assertThat(existing.getAmount()).isEqualTo("250.00");
            assertThat(existing.getNotes()).isEqualTo("revised");
        }

        @Test
        @DisplayName(
                "Response never carries the (encrypted) amount or category name from the mapper")
        void responseHidesEncryptedAmount() {
            Budget budget =
                    Budget.builder()
                            .id(2L)
                            .categoryId(7L)
                            .amount("ciphertext-blob")
                            .currency("EUR")
                            .period(BudgetPeriod.MONTHLY)
                            .build();
            BudgetResponse response = budgetMapper.toResponse(budget);
            assertThat(response.getAmount()).isNull();
            assertThat(response.getCategoryName()).isNull();
            assertThat(response.getCategoryId()).isEqualTo(7L);
            assertThat(budgetMapper.toResponse(null)).isNull();
            assertThat(budgetMapper.toEntity(null)).isNull();
        }
    }

    @Nested
    @DisplayName("UserMapper")
    class UserMapping {

        @Test
        @DisplayName("Response contains only non-sensitive profile fields")
        void responseExcludesSecrets() {
            User user =
                    User.builder()
                            .id(11L)
                            .username("alice")
                            .email("alice@example.com")
                            .passwordHash("$2a$12$hash")
                            .masterPasswordSalt("salt-base64")
                            .baseCurrency("EUR")
                            .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                            .build();

            UserResponse response = userMapper.toResponse(user);

            assertThat(response.getId()).isEqualTo(11L);
            assertThat(response.getUsername()).isEqualTo("alice");
            assertThat(response.getEmail()).isEqualTo("alice@example.com");
            assertThat(response.getBaseCurrency()).isEqualTo("EUR");
            // Structural guarantee: the DTO cannot leak credential material because no such
            // fields exist on it. Fails if anyone ever adds them to UserResponse.
            assertThat(
                            Arrays.stream(UserResponse.class.getDeclaredFields())
                                    .map(f -> f.getName().toLowerCase()))
                    .noneMatch(
                            name ->
                                    name.contains("password")
                                            || name.contains("salt")
                                            || name.contains("secret")
                                            || name.contains("token"));
            assertThat(userMapper.toResponse(null)).isNull();
        }
    }

    @Nested
    @DisplayName("TransactionMapper")
    class TransactionMapping {

        @Test
        @DisplayName("Create starts un-deleted and leaves FX/transfer fields to the service")
        void createLeavesFxFieldsToService() {
            TransactionRequest request =
                    TransactionRequest.builder()
                            .accountId(1L)
                            .type(TransactionType.EXPENSE)
                            .amount(new BigDecimal("-25.50"))
                            .currency("EUR")
                            .originalAmount(new BigDecimal("30"))
                            .originalCurrency("USD")
                            .conversionRate(new BigDecimal("0.85"))
                            .categoryId(4L)
                            .date(LocalDate.of(2026, 2, 1))
                            .description("Groceries")
                            .build();

            Transaction entity = transactionMapper.toEntity(request);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getUserId()).isNull();
            assertThat(entity.getIsDeleted()).isFalse();
            assertThat(entity.getAmount()).isEqualByComparingTo("-25.50");
            assertThat(entity.getCategoryId()).isEqualTo(4L);
            // FX bookkeeping is service-owned; the mapper must not copy it from the request.
            assertThat(entity.getOriginalAmount()).isNull();
            assertThat(entity.getOriginalCurrency()).isNull();
            assertThat(entity.getConversionRate()).isNull();
            assertThat(entity.getTransferId()).isNull();
            assertThat(entity.getPayeeId()).isNull();
        }

        @Test
        @DisplayName("Update cannot resurrect a deleted transaction or rewrite its FX fields")
        void updatePreservesDeletionAndFx() {
            Transaction existing =
                    Transaction.builder()
                            .id(8L)
                            .userId(3L)
                            .description("old")
                            .amount(new BigDecimal("10"))
                            .isDeleted(true)
                            .originalAmount(new BigDecimal("12"))
                            .transferId("transfer-1")
                            .build();
            TransactionRequest request =
                    TransactionRequest.builder()
                            .description("new")
                            .originalAmount(new BigDecimal("999"))
                            .build();

            transactionMapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getDescription()).isEqualTo("new");
            assertThat(existing.getIsDeleted()).isTrue();
            assertThat(existing.getOriginalAmount()).isEqualByComparingTo("12");
            assertThat(existing.getTransferId()).isEqualTo("transfer-1");
            // Null amount in the request must not wipe the stored amount.
            assertThat(existing.getAmount()).isEqualByComparingTo("10");
        }

        @Test
        @DisplayName("Response maps ids and leaves denormalized names for the service")
        void responseMapping() {
            Transaction tx =
                    Transaction.builder()
                            .id(6L)
                            .accountId(1L)
                            .amount(new BigDecimal("-9.99"))
                            .currency("EUR")
                            .payeeId(21L)
                            .currencyId(22L)
                            .categoryId(4L)
                            .build();
            TransactionResponse response = transactionMapper.toResponse(tx);
            assertThat(response.getPayeeId()).isEqualTo(21L);
            assertThat(response.getCurrencyId()).isEqualTo(22L);
            assertThat(response.getAccountName()).isNull();
            assertThat(response.getCategoryName()).isNull();
            assertThat(response.getCategoryIcon()).isNull();
            assertThat(transactionMapper.toEntity(null)).isNull();
            assertThat(transactionMapper.toResponse(null)).isNull();
        }
    }

    @Nested
    @DisplayName("RealEstateMapper")
    class RealEstateMapping {

        @Test
        @DisplayName("Create converts money to exact strings and carries plain location fields")
        void createConvertsMoney() {
            RealEstatePropertyRequest request =
                    RealEstatePropertyRequest.builder()
                            .name("Flat")
                            .propertyType(PropertyType.RESIDENTIAL)
                            .address("1 Street")
                            .purchasePrice(new BigDecimal("250000.50"))
                            .currentValue(new BigDecimal("260000"))
                            .purchaseDate(LocalDate.of(2024, 6, 1))
                            .currency("EUR")
                            .mortgageId(15L)
                            .rentalIncome(new BigDecimal("950.00"))
                            .latitude(new BigDecimal("48.8566"))
                            .longitude(new BigDecimal("2.3522"))
                            .build();

            RealEstateProperty entity = realEstateMapper.toEntity(request);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getUserId()).isNull();
            assertThat(entity.getPurchasePrice()).isEqualTo("250000.50");
            assertThat(entity.getCurrentValue()).isEqualTo("260000");
            assertThat(entity.getRentalIncome()).isEqualTo("950.00");
            assertThat(entity.getMortgageId()).isEqualTo(15L);
            assertThat(entity.getLatitude()).isEqualByComparingTo("48.8566");
            assertThat(entity.getCreatedAt()).isNull();
        }

        @Test
        @DisplayName("Response withholds every encrypted field for the service to decrypt")
        void responseWithholdsEncryptedFields() {
            RealEstateProperty property =
                    RealEstateProperty.builder()
                            .id(9L)
                            .userId(3L)
                            .name("cipher-name")
                            .address("cipher-address")
                            .propertyType(PropertyType.RESIDENTIAL)
                            .purchasePrice("cipher-price")
                            .currentValue("cipher-value")
                            .rentalIncome("cipher-rent")
                            .notes("cipher-notes")
                            .documents("cipher-docs")
                            .purchaseDate(LocalDate.of(2024, 6, 1))
                            .currency("EUR")
                            .mortgageId(15L)
                            .latitude(new BigDecimal("48.8566"))
                            .build();

            RealEstatePropertyResponse response = realEstateMapper.toResponse(property);

            assertThat(response.getId()).isEqualTo(9L);
            assertThat(response.getName()).isNull();
            assertThat(response.getAddress()).isNull();
            assertThat(response.getPurchasePrice()).isNull();
            assertThat(response.getCurrentValue()).isNull();
            assertThat(response.getRentalIncome()).isNull();
            assertThat(response.getNotes()).isNull();
            assertThat(response.getDocuments()).isNull();
            assertThat(response.getAppreciation()).isNull();
            assertThat(response.getMortgageBalance()).isNull();
            // Plain (unencrypted) fields still flow through.
            assertThat(response.getCurrency()).isEqualTo("EUR");
            assertThat(response.getMortgageId()).isEqualTo(15L);
            assertThat(response.getLatitude()).isEqualByComparingTo("48.8566");
        }

        @Test
        @DisplayName("Update ignores nulls so a partial request cannot blank stored values")
        void updateIgnoresNulls() {
            RealEstateProperty existing =
                    RealEstateProperty.builder()
                            .id(9L)
                            .userId(3L)
                            .name("Flat")
                            .purchasePrice("250000")
                            .currency("EUR")
                            .build();
            realEstateMapper.updateEntityFromRequest(
                    RealEstatePropertyRequest.builder()
                            .currentValue(new BigDecimal("270000"))
                            .build(),
                    existing);
            assertThat(existing.getName()).isEqualTo("Flat");
            assertThat(existing.getPurchasePrice()).isEqualTo("250000");
            assertThat(existing.getCurrentValue()).isEqualTo("270000");
            assertThat(existing.getUserId()).isEqualTo(3L);
            assertThat(realEstateMapper.toEntity(null)).isNull();
            assertThat(realEstateMapper.toResponse(null)).isNull();
        }
    }
}
