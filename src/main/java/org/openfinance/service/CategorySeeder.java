package org.openfinance.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.openfinance.entity.Category;
import org.openfinance.entity.CategoryType;
import org.openfinance.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for seeding default categories for new users based on ISO 18245 standard.
 *
 * <p>Creates a comprehensive set of income and expense categories with MCC (Merchant Category
 * Codes) when a user registers. These system categories follow the ISO 18245 standard for financial
 * transaction categorization.
 *
 * <p><strong>ISO 18245 Categories Include:</strong>
 *
 * <ul>
 *   <li><strong>INCOME:</strong> Salary, Freelance, Investments, Rental Income, Government
 *       Benefits, Retirement, Gifts, Other Income (10+ categories with MCC codes)
 *   <li><strong>EXPENSE:</strong> Groceries, Dining, Transportation, Healthcare, Shopping,
 *       Entertainment, Utilities, Insurance, Education, Travel, Auto, Home, Personal Care,
 *       Subscriptions (40+ categories)
 * </ul>
 *
 * <p><strong>Integration:</strong> This service is called automatically from {@link
 * UserService#registerUser(org.openfinance.dto.UserRegistrationRequest)} after user creation. It is
 * NOT a CommandLineRunner to avoid seeding before users exist.
 *
 * <p>Requirement REQ-2.10.1: System-provided default categories
 *
 * @see Category
 * @see CategoryRepository
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
public class CategorySeeder {

    private static final Logger log = LoggerFactory.getLogger(CategorySeeder.class);

    private final CategoryRepository categoryRepository;

    /**
     * Seeds default ISO 18245 categories for a new user.
     *
     * <p><strong>Process:</strong>
     *
     * <ol>
     *   <li>Check if user already has categories (idempotent operation)
     *   <li>Create hierarchical INCOME categories with MCC codes
     *   <li>Create hierarchical EXPENSE categories with MCC codes
     *   <li>Mark all as system categories (isSystem = true)
     *   <li>Batch save all categories
     * </ol>
     *
     * <p><strong>Category Names are NOT encrypted:</strong> Default system categories are created
     * with plain text names. When users create custom categories, those will be encrypted by the
     * CategoryService.
     *
     * <p><strong>Idempotency:</strong> This method can be called multiple times safely. If
     * categories already exist for the user, no action is taken.
     *
     * @param userId the ID of the newly registered user
     * @return number of categories created (0 if already seeded)
     * @throws IllegalArgumentException if userId is null
     */
    @Transactional
    public int seedDefaultCategories(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        log.info("Seeding ISO 18245 default categories for user ID: {}", userId);

        // Check if categories already exist (idempotent operation)
        long existingCount = categoryRepository.countByUserId(userId);
        if (existingCount > 0) {
            log.info("User {} already has {} categories, skipping seed", userId, existingCount);
            return 0;
        }

        List<Category> categories = new ArrayList<>();

        // Create INCOME categories
        categories.addAll(createIncomeCategories(userId));

        // Create EXPENSE categories
        categories.addAll(createExpenseCategories(userId));

        // Batch save all categories
        List<Category> savedCategories = categoryRepository.saveAll(categories);

        log.info(
                "Successfully seeded {} ISO 18245 default categories for user ID: {}",
                savedCategories.size(),
                userId);

        return savedCategories.size();
    }

    /** Creates ISO 18245 INCOME categories with MCC codes. */
    private List<Category> createIncomeCategories(Long userId) {
        List<Category> incomeCategories = new ArrayList<>();

        // Employment Income (MCC 6010-6011)
        Category employmentIncome =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Employment Income",
                                CategoryType.INCOME,
                                "💼",
                                "#10B981",
                                "6010",
                                null,
                                "category.employment.income"));
        incomeCategories.add(employmentIncome);

        // Salary subcategories
        incomeCategories.add(
                buildCategory(
                        userId,
                        "Salary",
                        CategoryType.INCOME,
                        "💰",
                        "#10B981",
                        "6010",
                        employmentIncome.getId(),
                        "category.salary"));
        incomeCategories.add(
                buildCategory(
                        userId,
                        "Bonus",
                        CategoryType.INCOME,
                        "🎁",
                        "#10B981",
                        "6010",
                        employmentIncome.getId(),
                        "category.bonus"));
        incomeCategories.add(
                buildCategory(
                        userId,
                        "Commission",
                        CategoryType.INCOME,
                        "💵",
                        "#10B981",
                        "6010",
                        employmentIncome.getId(),
                        "category.commission"));

        // Self Employment (MCC 6810)
        Category selfEmployment =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Self Employment",
                                CategoryType.INCOME,
                                "🏢",
                                "#3B82F6",
                                "6810",
                                null,
                                "category.self.employment"));
        incomeCategories.add(selfEmployment);
        incomeCategories.add(
                buildCategory(
                        userId,
                        "Freelance Work",
                        CategoryType.INCOME,
                        "💻",
                        "#3B82F6",
                        "6810",
                        selfEmployment.getId(),
                        "category.freelance.work"));
        incomeCategories.add(
                buildCategory(
                        userId,
                        "Consulting",
                        CategoryType.INCOME,
                        "📊",
                        "#3B82F6",
                        "6810",
                        selfEmployment.getId(),
                        "category.consulting"));

        // Investments (MCC 6760 - Debt Collection, 6211 - Securities)
        Category investments =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Investments",
                                CategoryType.INCOME,
                                "📈",
                                "#8B5CF6",
                                "6211",
                                null,
                                "category.investments"));
        incomeCategories.add(investments);
        incomeCategories.add(
                buildCategory(
                        userId,
                        "Dividends",
                        CategoryType.INCOME,
                        "💹",
                        "#8B5CF6",
                        "6211",
                        investments.getId(),
                        "category.dividends"));
        incomeCategories.add(
                buildCategory(
                        userId,
                        "Interest Income",
                        CategoryType.INCOME,
                        "🏦",
                        "#8B5CF6",
                        "6211",
                        investments.getId(),
                        "category.interest.income"));
        incomeCategories.add(
                buildCategory(
                        userId,
                        "Capital Gains",
                        CategoryType.INCOME,
                        "📊",
                        "#8B5CF6",
                        "6211",
                        investments.getId(),
                        "category.capital.gains"));

        // Rental Income (MCC 6513 - Real Estate Agents)
        Category rentalIncome =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Rental Income",
                                CategoryType.INCOME,
                                "🏠",
                                "#14B8A6",
                                "6513",
                                null,
                                "category.rental.income"));
        incomeCategories.add(rentalIncome);
        incomeCategories.add(
                buildCategory(
                        userId,
                        "Residential Rent",
                        CategoryType.INCOME,
                        "🏡",
                        "#14B8A6",
                        "6513",
                        rentalIncome.getId(),
                        "category.residential.rent"));
        incomeCategories.add(
                buildCategory(
                        userId,
                        "Commercial Rent",
                        CategoryType.INCOME,
                        "🏢",
                        "#14B8A6",
                        "6513",
                        rentalIncome.getId(),
                        "category.commercial.rent"));

        // Government Benefits (MCC 9016 - Tax)
        incomeCategories.add(
                buildCategory(
                        userId,
                        "Government Benefits",
                        CategoryType.INCOME,
                        "🏛️",
                        "#6366F1",
                        "9016",
                        null,
                        "category.government.benefits"));
        incomeCategories.add(
                buildCategory(
                        userId,
                        "Pension",
                        CategoryType.INCOME,
                        "👴",
                        "#6366F1",
                        "9016",
                        null,
                        "category.pension"));
        incomeCategories.add(
                buildCategory(
                        userId,
                        "Social Security",
                        CategoryType.INCOME,
                        "🛡️",
                        "#6366F1",
                        "9016",
                        null,
                        "category.social.security"));
        incomeCategories.add(
                buildCategory(
                        userId,
                        "Unemployment",
                        CategoryType.INCOME,
                        "🔍",
                        "#6366F1",
                        "9016",
                        null,
                        "category.unemployment"));

        // Retirement Income
        incomeCategories.add(
                buildCategory(
                        userId,
                        "Retirement Income",
                        CategoryType.INCOME,
                        "🏖️",
                        "#EC4899",
                        null,
                        null,
                        "category.retirement.income"));
        incomeCategories.add(
                buildCategory(
                        userId,
                        "401k Withdrawal",
                        CategoryType.INCOME,
                        "💼",
                        "#EC4899",
                        null,
                        null,
                        "category.401k.withdrawal"));
        incomeCategories.add(
                buildCategory(
                        userId,
                        "IRA Withdrawal",
                        CategoryType.INCOME,
                        "📋",
                        "#EC4899",
                        null,
                        null,
                        "category.ira.withdrawal"));

        // Gifts and Donations (MCC 5937 - Antique Shops)
        Category gifts =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Gifts",
                                CategoryType.INCOME,
                                "🎁",
                                "#F59E0B",
                                "5937",
                                null,
                                "category.gifts"));
        incomeCategories.add(gifts);
        incomeCategories.add(
                buildCategory(
                        userId,
                        "Inheritance",
                        CategoryType.INCOME,
                        "📜",
                        "#F59E0B",
                        "5937",
                        gifts.getId(),
                        "category.inheritance"));

        // Other Income
        incomeCategories.add(
                buildCategory(
                        userId,
                        "Other Income",
                        CategoryType.INCOME,
                        "💵",
                        "#6B7280",
                        null,
                        null,
                        "category.other.income"));

        log.debug("Created {} INCOME categories for user {}", incomeCategories.size(), userId);
        return incomeCategories;
    }

    /** Creates ISO 18245 EXPENSE categories with MCC codes in hierarchical structure. */
    private List<Category> createExpenseCategories(Long userId) {
        List<Category> expenseCategories = new ArrayList<>();

        // ========== FOOD AND DINING ==========
        // Groceries (MCC 5411)
        Category groceries =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Groceries",
                                CategoryType.EXPENSE,
                                "🛒",
                                "#10B981",
                                "5411",
                                null,
                                "category.groceries"));
        expenseCategories.add(groceries);
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Supermarkets",
                        CategoryType.EXPENSE,
                        "🏪",
                        "#10B981",
                        "5411",
                        groceries.getId(),
                        "category.supermarkets"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Convenience Stores",
                        CategoryType.EXPENSE,
                        "🏪",
                        "#10B981",
                        "5411",
                        groceries.getId(),
                        "category.convenience.stores"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Organic Foods",
                        CategoryType.EXPENSE,
                        "🥬",
                        "#10B981",
                        "5411",
                        groceries.getId(),
                        "category.organic.foods"));

        // Restaurants (MCC 5812, 5814)
        Category diningOut =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Dining Out",
                                CategoryType.EXPENSE,
                                "🍽️",
                                "#F97316",
                                "5812",
                                null,
                                "category.dining.out"));
        expenseCategories.add(diningOut);
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Fast Food",
                        CategoryType.EXPENSE,
                        "🍔",
                        "#F97316",
                        "5814",
                        diningOut.getId(),
                        "category.fast.food"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Casual Dining",
                        CategoryType.EXPENSE,
                        "🍴",
                        "#F97316",
                        "5812",
                        diningOut.getId(),
                        "category.casual.dining"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Fine Dining",
                        CategoryType.EXPENSE,
                        "🥂",
                        "#F97316",
                        "5812",
                        diningOut.getId(),
                        "category.fine.dining"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Coffee Shops",
                        CategoryType.EXPENSE,
                        "☕",
                        "#F97316",
                        "5814",
                        diningOut.getId(),
                        "category.coffee.shops"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Bars and Nightlife",
                        CategoryType.EXPENSE,
                        "🍺",
                        "#F97316",
                        "5813",
                        diningOut.getId(),
                        "category.bars.and.nightlife"));

        // ========== TRANSPORTATION ==========
        // Auto Expenses (MCC 5541 - Service Stations)
        Category autoExpenses =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Auto Expenses",
                                CategoryType.EXPENSE,
                                "🚗",
                                "#3B82F6",
                                "5541",
                                null,
                                "category.auto.expenses"));
        expenseCategories.add(autoExpenses);
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Gas/Fuel",
                        CategoryType.EXPENSE,
                        "⛽",
                        "#3B82F6",
                        "5541",
                        autoExpenses.getId(),
                        "category.gas.fuel"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Auto Maintenance",
                        CategoryType.EXPENSE,
                        "🔧",
                        "#3B82F6",
                        "7538",
                        autoExpenses.getId(),
                        "category.auto.maintenance"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Auto Insurance",
                        CategoryType.EXPENSE,
                        "🛡️",
                        "#3B82F6",
                        "6300",
                        autoExpenses.getId(),
                        "category.auto.insurance"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Parking",
                        CategoryType.EXPENSE,
                        "🅿️",
                        "#3B82F6",
                        "7542",
                        autoExpenses.getId(),
                        "category.parking"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Tolls",
                        CategoryType.EXPENSE,
                        "💳",
                        "#3B82F6",
                        "7542",
                        autoExpenses.getId(),
                        "category.tolls"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Car Payment",
                        CategoryType.EXPENSE,
                        "📑",
                        "#3B82F6",
                        null,
                        autoExpenses.getId(),
                        "category.car.payment"));

        // Public Transit (MCC 4111 - Transit)
        Category publicTransit =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Public Transit",
                                CategoryType.EXPENSE,
                                "🚇",
                                "#06B6D4",
                                "4111",
                                null,
                                "category.public.transit"));
        expenseCategories.add(publicTransit);
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Bus/Metro",
                        CategoryType.EXPENSE,
                        "🚌",
                        "#06B6D4",
                        "4111",
                        publicTransit.getId(),
                        "category.bus.metro"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Taxi/Rideshare",
                        CategoryType.EXPENSE,
                        "🚕",
                        "#06B6D4",
                        "4121",
                        publicTransit.getId(),
                        "category.taxi.rideshare"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Train",
                        CategoryType.EXPENSE,
                        "🚆",
                        "#06B6D4",
                        "4112",
                        publicTransit.getId(),
                        "category.train"));

        // Airlines (MCC 4511)
        Category airTravel =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Air Travel",
                                CategoryType.EXPENSE,
                                "✈️",
                                "#14B8A6",
                                "4511",
                                null,
                                "category.air.travel"));
        expenseCategories.add(airTravel);
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Airlines",
                        CategoryType.EXPENSE,
                        "🛫",
                        "#14B8A6",
                        "4511",
                        airTravel.getId(),
                        "category.airlines"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Hotels",
                        CategoryType.EXPENSE,
                        "🏨",
                        "#14B8A6",
                        "7011",
                        airTravel.getId(),
                        "category.hotels"));

        // ========== SHOPPING ==========
        // General Merchandise (MCC 5310)
        Category shopping =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Shopping",
                                CategoryType.EXPENSE,
                                "🛍️",
                                "#8B5CF6",
                                "5310",
                                null,
                                "category.shopping"));
        expenseCategories.add(shopping);
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Department Stores",
                        CategoryType.EXPENSE,
                        "🏬",
                        "#8B5CF6",
                        "5310",
                        shopping.getId(),
                        "category.department.stores"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Clothing",
                        CategoryType.EXPENSE,
                        "👕",
                        "#8B5CF6",
                        "5651",
                        shopping.getId(),
                        "category.clothing"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Electronics",
                        CategoryType.EXPENSE,
                        "📱",
                        "#8B5CF6",
                        "5732",
                        shopping.getId(),
                        "category.electronics"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Home Goods",
                        CategoryType.EXPENSE,
                        "🏠",
                        "#8B5CF6",
                        "5712",
                        shopping.getId(),
                        "category.home.goods"));

        // ========== ENTERTAINMENT ==========
        // Entertainment (MCC 7832 - Motion Pictures)
        Category entertainment =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Entertainment",
                                CategoryType.EXPENSE,
                                "🎬",
                                "#EC4899",
                                "7832",
                                null,
                                "category.entertainment"));
        expenseCategories.add(entertainment);
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Movies",
                        CategoryType.EXPENSE,
                        "🎥",
                        "#EC4899",
                        "7832",
                        entertainment.getId(),
                        "category.movies"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Music",
                        CategoryType.EXPENSE,
                        "🎵",
                        "#EC4899",
                        "5733",
                        entertainment.getId(),
                        "category.music"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Gaming",
                        CategoryType.EXPENSE,
                        "🎮",
                        "#EC4899",
                        "5816",
                        entertainment.getId(),
                        "category.gaming"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Events",
                        CategoryType.EXPENSE,
                        "🎭",
                        "#EC4899",
                        "7922",
                        entertainment.getId(),
                        "category.events"));

        // Subscriptions (MCC 5968 - Continuity/Subscription)
        Category subscriptions =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Subscriptions",
                                CategoryType.EXPENSE,
                                "📺",
                                "#F43F5E",
                                "5968",
                                null,
                                "category.subscriptions"));
        expenseCategories.add(subscriptions);
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Streaming Services",
                        CategoryType.EXPENSE,
                        "📡",
                        "#F43F5E",
                        "5968",
                        subscriptions.getId(),
                        "category.streaming.services"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Software",
                        CategoryType.EXPENSE,
                        "💻",
                        "#F43F5E",
                        "5812",
                        subscriptions.getId(),
                        "category.software"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Memberships",
                        CategoryType.EXPENSE,
                        "💳",
                        "#F43F5E",
                        "5968",
                        subscriptions.getId(),
                        "category.memberships"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Digital Subscriptions",
                        CategoryType.EXPENSE,
                        "🖥️",
                        "#F43F5E",
                        "5968",
                        subscriptions.getId(),
                        "category.digital.subscriptions"));

        // ========== HOUSING ==========
        // Housing (MCC 7011 - Hotels/Lodging used conceptually for rent)
        Category housing =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Housing",
                                CategoryType.EXPENSE,
                                "🏠",
                                "#EF4444",
                                "7011",
                                null,
                                "category.housing"));
        expenseCategories.add(housing);
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Rent",
                        CategoryType.EXPENSE,
                        "🏢",
                        "#EF4444",
                        "6513",
                        housing.getId(),
                        "category.rent"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Mortgage",
                        CategoryType.EXPENSE,
                        "🏦",
                        "#EF4444",
                        null,
                        housing.getId(),
                        "category.mortgage"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Property Tax",
                        CategoryType.EXPENSE,
                        "📋",
                        "#EF4444",
                        "9311",
                        housing.getId(),
                        "category.property.tax"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Loan Repayment",
                        CategoryType.EXPENSE,
                        "💳",
                        "#EF4444",
                        null,
                        housing.getId(),
                        "category.loan.repayment"));

        // Utilities (MCC 4900)
        Category utilities =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Utilities",
                                CategoryType.EXPENSE,
                                "💡",
                                "#F59E0B",
                                "4900",
                                null,
                                "category.utilities"));
        expenseCategories.add(utilities);
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Electricity",
                        CategoryType.EXPENSE,
                        "⚡",
                        "#F59E0B",
                        "4900",
                        utilities.getId(),
                        "category.electricity"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Water",
                        CategoryType.EXPENSE,
                        "💧",
                        "#F59E0B",
                        "4900",
                        utilities.getId(),
                        "category.water"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Gas/Heating",
                        CategoryType.EXPENSE,
                        "🔥",
                        "#F59E0B",
                        "4900",
                        utilities.getId(),
                        "category.gas.heating"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Internet",
                        CategoryType.EXPENSE,
                        "📶",
                        "#F59E0B",
                        "4816",
                        utilities.getId(),
                        "category.internet"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Phone",
                        CategoryType.EXPENSE,
                        "📞",
                        "#F59E0B",
                        "4814",
                        utilities.getId(),
                        "category.phone"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Cable TV",
                        CategoryType.EXPENSE,
                        "📺",
                        "#F59E0B",
                        "4899",
                        utilities.getId(),
                        "category.cable.tv"));

        // ========== HEALTHCARE ==========
        // Healthcare (MCC 8011 - Doctors)
        Category healthcare =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Healthcare",
                                CategoryType.EXPENSE,
                                "⚕️",
                                "#06B6D4",
                                "8011",
                                null,
                                "category.healthcare"));
        expenseCategories.add(healthcare);
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Doctor Visits",
                        CategoryType.EXPENSE,
                        "🩺",
                        "#06B6D4",
                        "8011",
                        healthcare.getId(),
                        "category.doctor.visits"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Dental",
                        CategoryType.EXPENSE,
                        "🦷",
                        "#06B6D4",
                        "8021",
                        healthcare.getId(),
                        "category.dental"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Vision",
                        CategoryType.EXPENSE,
                        "👁️",
                        "#06B6D4",
                        "8043",
                        healthcare.getId(),
                        "category.vision"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Prescriptions",
                        CategoryType.EXPENSE,
                        "💊",
                        "#06B6D4",
                        "5912",
                        healthcare.getId(),
                        "category.prescriptions"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Health Insurance",
                        CategoryType.EXPENSE,
                        "🛡️",
                        "#06B6D4",
                        "6324",
                        healthcare.getId(),
                        "category.health.insurance"));

        // ========== INSURANCE ==========
        // Insurance (MCC 6300)
        Category insurance =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Insurance",
                                CategoryType.EXPENSE,
                                "🛡️",
                                "#6366F1",
                                "6300",
                                null,
                                "category.insurance"));
        expenseCategories.add(insurance);
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Life Insurance",
                        CategoryType.EXPENSE,
                        "👼",
                        "#6366F1",
                        "6300",
                        insurance.getId(),
                        "category.life.insurance"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Home Insurance",
                        CategoryType.EXPENSE,
                        "🏠",
                        "#6366F1",
                        "6300",
                        insurance.getId(),
                        "category.home.insurance"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Disability Insurance",
                        CategoryType.EXPENSE,
                        "🦓",
                        "#6366F1",
                        "6300",
                        insurance.getId(),
                        "category.disability.insurance"));

        // ========== EDUCATION ==========
        // Education (MCC 8220 - Colleges)
        Category education =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Education",
                                CategoryType.EXPENSE,
                                "📚",
                                "#A855F7",
                                "8220",
                                null,
                                "category.education"));
        expenseCategories.add(education);
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Tuition",
                        CategoryType.EXPENSE,
                        "🎓",
                        "#A855F7",
                        "8220",
                        education.getId(),
                        "category.tuition"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Books/Supplies",
                        CategoryType.EXPENSE,
                        "📖",
                        "#A855F7",
                        "5942",
                        education.getId(),
                        "category.books.supplies"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Online Courses",
                        CategoryType.EXPENSE,
                        "💻",
                        "#A855F7",
                        "8244",
                        education.getId(),
                        "category.online.courses"));

        // ========== PERSONAL CARE ==========
        // Personal Care (MCC 7230 - Beauty Shops)
        Category personalCare =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Personal Care",
                                CategoryType.EXPENSE,
                                "💅",
                                "#F472B6",
                                "7230",
                                null,
                                "category.personal.care"));
        expenseCategories.add(personalCare);
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Haircut/Salon",
                        CategoryType.EXPENSE,
                        "💇",
                        "#F472B6",
                        "7230",
                        personalCare.getId(),
                        "category.haircut.salon"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Spa",
                        CategoryType.EXPENSE,
                        "🧖",
                        "#F472B6",
                        "7298",
                        personalCare.getId(),
                        "category.spa"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Gym/Fitness",
                        CategoryType.EXPENSE,
                        "🏋️",
                        "#F472B6",
                        "7297",
                        personalCare.getId(),
                        "category.gym.fitness"));

        // ========== PETS ==========
        // Pet Care (MCC 5995 - Pet Shops)
        Category pets =
                categoryRepository.save(
                        buildCategory(
                                userId,
                                "Pets",
                                CategoryType.EXPENSE,
                                "🐾",
                                "#84CC16",
                                "5995",
                                null,
                                "category.pets"));
        expenseCategories.add(pets);
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Pet Food",
                        CategoryType.EXPENSE,
                        "🦴",
                        "#84CC16",
                        "5995",
                        pets.getId(),
                        "category.pet.food"));
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Veterinary",
                        CategoryType.EXPENSE,
                        "🏥",
                        "#84CC16",
                        "0742",
                        pets.getId(),
                        "category.veterinary"));

        // ========== CHARITY ==========
        // Charitable Donations (MCC 8398 - Charitable Organizations)
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Charity",
                        CategoryType.EXPENSE,
                        "❤️",
                        "#EF4444",
                        "8398",
                        null,
                        "category.charity"));

        // ========== TAXES ==========
        // Taxes (MCC 9311 - Tax Payments)
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Taxes",
                        CategoryType.EXPENSE,
                        "📋",
                        "#6B7280",
                        "9311",
                        null,
                        "category.taxes"));

        // ========== OTHER ==========
        // Miscellaneous
        expenseCategories.add(
                buildCategory(
                        userId,
                        "Other Expenses",
                        CategoryType.EXPENSE,
                        "💵",
                        "#6B7280",
                        null,
                        null,
                        "category.other.expenses"));

        log.debug("Created {} EXPENSE categories for user {}", expenseCategories.size(), userId);
        return expenseCategories;
    }

    /**
     * Builds a Category entity with the specified attributes.
     *
     * <p>All seeded categories are marked as system categories (isSystem = true). System categories
     * cannot be deleted but can be customized by users.
     *
     * @param userId the user ID
     * @param name category name (NOT encrypted for system categories)
     * @param type INCOME or EXPENSE
     * @param icon emoji icon for UI display
     * @param color hex color code for UI display
     * @param mccCode ISO 18245 Merchant Category Code (optional)
     * @param parentId parent category ID for hierarchical structure (optional)
     * @param nameKey i18n message key for localized name (e.g., "category.employment.income")
     * @return Category entity ready to be persisted
     */
    private Category buildCategory(
            Long userId,
            String name,
            CategoryType type,
            String icon,
            String color,
            String mccCode,
            Long parentId,
            String nameKey) {
        return Category.builder()
                .userId(userId)
                .name(name)
                .type(type)
                .icon(icon)
                .color(color)
                .mccCode(mccCode)
                .parentId(parentId)
                .isSystem(true) // System-provided category
                .nameKey(nameKey)
                .build();
    }
}
