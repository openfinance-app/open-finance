package org.openfinance.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.Category;
import org.openfinance.entity.Payee;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.PayeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for seeding default payees with their associated categories.
 *
 * <p>Creates a standard set of common merchants, service providers, and utilities that users
 * frequently encounter in their transactions. These system payees help with transaction
 * categorization and duplicate detection.
 *
 * <p>Each payee is associated with a default category from the CategorySeeder.
 *
 * <p><strong>Integration:</strong> This service is NOT a CommandLineRunner to avoid seeding before
 * users exist. It should be called after user registration and category seeding via {@link
 * #seedDefaultPayees(Long)}.
 *
 * <p>Requirements: Payee Management Feature
 *
 * @see Payee
 * @see PayeeRepository
 * @see CategorySeeder
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayeeSeeder {

    private final PayeeRepository payeeRepository;
    private final CategoryRepository categoryRepository;
    private final CategorySeeder categorySeeder;

    /** Default SVG icon as fallback logo. */
    private static final String DEFAULT_LOGO =
            "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSIjMzMzMzMzIiBzdHJva2Utd2lkdGg9IjIiPjxjaXJjbGUgY3g9IjEyIiBjeT0iMTAiIHI9IjEwIi8+PHBhdGggZD0iTTEwIDZIMThWOEgxMFY2eiIvPjxwYXRoIGQ9Ik04IDExSDE2djExSDhWMTF6Ii8+PHBhdGggZD0iTTEwIDExSDE0djExSDEweiIvPjwvc3ZnPg==";

    /**
     * Map of payee names to their local logo paths. Logos are stored in /logos/payees/ directory.
     */
    private static final Map<String, String> LOGO_PATHS = new HashMap<>();

    static {
        // Shopping
        LOGO_PATHS.put("Amazon", "/logos/payees/amazon.png");
        LOGO_PATHS.put("Amazon Prime", "/logos/payees/amazon-prime.png");
        LOGO_PATHS.put("eBay", "/logos/payees/ebay.png");
        LOGO_PATHS.put("AliExpress", "/logos/payees/aliexpress.png");
        LOGO_PATHS.put("Cdiscount", "/logos/payees/cdiscount.png");
        LOGO_PATHS.put("Vinted", "/logos/payees/vinted.png");
        LOGO_PATHS.put("Zalando", "/logos/payees/zalando.png");
        LOGO_PATHS.put("ASOS", "/logos/payees/asos.png");
        LOGO_PATHS.put("Vente-Privée", "/logos/payees/vente-privee.png");
        LOGO_PATHS.put("Rakuten", "/logos/payees/rakuten.png");

        // Entertainment
        LOGO_PATHS.put("Netflix", "/logos/payees/netflix.png");
        LOGO_PATHS.put("Disney+", "/logos/payees/disney-plus.png");
        LOGO_PATHS.put("Spotify", "/logos/payees/spotify.png");
        LOGO_PATHS.put("Apple Music", "/logos/payees/apple-music.png");
        LOGO_PATHS.put("Amazon Prime Video", "/logos/payees/amazon-prime.png");
        LOGO_PATHS.put("YouTube Premium", "/logos/payees/youtube.png");
        LOGO_PATHS.put("Canal+", "/logos/payees/canal-plus.png");
        LOGO_PATHS.put("OCS", "/logos/payees/ocs.png");
        LOGO_PATHS.put("PlayStation Store", "/logos/payees/playstation.png");
        LOGO_PATHS.put("Xbox Live", "/logos/payees/xbox.png");
        LOGO_PATHS.put("Nintendo eShop", "/logos/payees/nintendo.png");
        LOGO_PATHS.put("Steam", "/logos/payees/steam.png");

        // Groceries
        LOGO_PATHS.put("Carrefour", "/logos/payees/carrefour.png");
        LOGO_PATHS.put("Auchan", "/logos/payees/auchan.png");
        LOGO_PATHS.put("Leclerc", "/logos/payees/leclerc.png");
        LOGO_PATHS.put("Intermarché", "/logos/payees/intermarche.png");
        LOGO_PATHS.put("Super U", "/logos/payees/super-u.png");
        LOGO_PATHS.put("Casino", "/logos/payees/casino.png");
        LOGO_PATHS.put("Monoprix", "/logos/payees/monoprix.png");
        LOGO_PATHS.put("Franprix", "/logos/payees/franprix.png");
        LOGO_PATHS.put("Picard", "/logos/payees/picard.png");
        LOGO_PATHS.put("Biocoop", "/logos/payees/biocoop.png");

        // Restaurants & Fast Food
        LOGO_PATHS.put("McDonald's", "/logos/payees/mcdonalds.png");
        LOGO_PATHS.put("Burger King", "/logos/payees/burger-king.png");
        LOGO_PATHS.put("KFC", "/logos/payees/kfc.png");
        LOGO_PATHS.put("Subway", "/logos/payees/subway.png");
        LOGO_PATHS.put("Domino's Pizza", "/logos/payees/dominos.png");
        LOGO_PATHS.put("Just Eat", "/logos/payees/justeat.png");
        LOGO_PATHS.put("Deliveroo", "/logos/payees/deliveroo.png");
        LOGO_PATHS.put("Uber Eats", "/logos/payees/uber-eats.png");
        LOGO_PATHS.put("Starbucks", "/logos/payees/starbucks.png");
        LOGO_PATHS.put("Quick", "/logos/payees/quick.png");

        // Home improvement
        LOGO_PATHS.put("IKEA", "/logos/payees/ikea.png");
        LOGO_PATHS.put("Leroy Merlin", "/logos/payees/leroy-merlin.png");
        LOGO_PATHS.put("Bricomarché", "/logos/payees/bricomarche.png");
        LOGO_PATHS.put("But", "/logos/payees/but.png");

        // Internet & Phone
        LOGO_PATHS.put("Orange", "/logos/payees/orange.png");
        LOGO_PATHS.put("SFR", "/logos/payees/sfr.png");
        LOGO_PATHS.put("Bouygues Telecom", "/logos/payees/bouygues.png");
        LOGO_PATHS.put("Free", "/logos/payees/free.png");
        LOGO_PATHS.put("Sosh", "/logos/payees/sosh.png");
        LOGO_PATHS.put("NordVPN", "/logos/payees/nordvpn.png");

        // Transport - Gas Stations
        LOGO_PATHS.put("TotalEnergies", "/logos/payees/totalenergies.png");
        LOGO_PATHS.put("Shell", "/logos/payees/shell.png");
        LOGO_PATHS.put("BP", "/logos/payees/bp.png");
        LOGO_PATHS.put("Esso", "/logos/payees/esso.png");

        // Transport - Other
        LOGO_PATHS.put("SNCF", "/logos/payees/sncf.png");
        LOGO_PATHS.put("RATP", "/logos/payees/ratp.png");
        LOGO_PATHS.put("Uber", "/logos/payees/uber.png");
        LOGO_PATHS.put("BlaBlaCar", "/logos/payees/blablacar.png");
        LOGO_PATHS.put("FlixBus", "/logos/payees/flixbus.png");
        LOGO_PATHS.put("Air France", "/logos/payees/air-france.png");
        LOGO_PATHS.put("Ryanair", "/logos/payees/ryanair.png");
        LOGO_PATHS.put("EasyJet", "/logos/payees/easyjet.png");

        // Health & Pharmacy
        LOGO_PATHS.put("Pharmacie", null); // No logo available

        // Financial
        LOGO_PATHS.put("BNP Paribas", "/logos/payees/bnp-paribas.png");
        LOGO_PATHS.put("Société Générale", "/logos/payees/societe-generale.png");
        LOGO_PATHS.put("Crédit Agricole", "/logos/payees/credit-agricole.png");
        LOGO_PATHS.put("LCL", "/logos/payees/lcl.png");
        LOGO_PATHS.put("Boursorama", "/logos/payees/boursorama.png");
        LOGO_PATHS.put("PayPal", "/logos/payees/paypal.png");
        LOGO_PATHS.put("Wise", "/logos/payees/wise.png");

        // Insurance
        LOGO_PATHS.put("MAIF", "/logos/payees/maif.png");
        LOGO_PATHS.put("AXA", "/logos/payees/axa.png");
        LOGO_PATHS.put("GMF", "/logos/payees/gmf.png");
        LOGO_PATHS.put("MAAF", "/logos/payees/maaf.png");
        LOGO_PATHS.put("Allianz", "/logos/payees/allianz.png");

        // Education
        LOGO_PATHS.put("Udemy", "/logos/payees/udemy.png");
        LOGO_PATHS.put("Coursera", "/logos/payees/coursera.png");
        LOGO_PATHS.put("LinkedIn Learning", "/logos/payees/linkedin.png");
    }

    /**
     * Seeds default payees for a specific user. Idempotent operation - safe to call multiple times.
     *
     * @param userId the user ID to seed payees for
     */
    @Transactional
    public void seedDefaultPayees(Long userId) {
        log.info("Seeding default payees for user ID: {}", userId);

        // Check if already seeded (system payees are shared, so check if any exist)
        long existingCount = payeeRepository.count();
        if (existingCount > 0) {
            log.info("Found {} existing payees, skipping seed", existingCount);
            return;
        }

        // Ensure categories are seeded first for this user
        // This is a safety check - normally CategorySeeder runs first
        categorySeeder.seedDefaultCategories(userId);

        List<Payee> payees = createDefaultPayees(userId);

        // Log any payees that couldn't find their category
        long payeesWithCategory =
                payees.stream().filter(p -> p.getDefaultCategory() != null).count();
        log.info(
                "Created {} payees, {} have categories assigned",
                payees.size(),
                payeesWithCategory);

        payeeRepository.saveAll(payees);

        log.info("Successfully seeded {} default payees", payees.size());
    }

    /** Creates list of default payees with their categories. */
    private List<Payee> createDefaultPayees(Long userId) {
        return List.of(
                // Shopping
                createPayee("Amazon", "Shopping"),
                createPayee("Amazon Prime", "Shopping"),
                createPayee("eBay", "Shopping"),
                createPayee("AliExpress", "Shopping"),
                createPayee("Cdiscount", "Shopping"),
                createPayee("Vinted", "Shopping"),
                createPayee("Zalando", "Shopping"),
                createPayee("ASOS", "Shopping"),
                createPayee("Vente-Privée", "Shopping"),
                createPayee("Rakuten", "Shopping"),

                // Entertainment
                createPayee("Netflix", "Entertainment"),
                createPayee("Disney+", "Entertainment"),
                createPayee("Spotify", "Entertainment"),
                createPayee("Apple Music", "Entertainment"),
                createPayee("Amazon Prime Video", "Entertainment"),
                createPayee("YouTube Premium", "Entertainment"),
                createPayee("Canal+", "Entertainment"),
                createPayee("OCS", "Entertainment"),
                createPayee("PlayStation Store", "Entertainment"),
                createPayee("Xbox Live", "Entertainment"),
                createPayee("Nintendo eShop", "Entertainment"),
                createPayee("Steam", "Entertainment"),
                createPayee("Cinema", "Entertainment"),

                // Groceries
                createPayee("Carrefour", "Groceries"),
                createPayee("Auchan", "Groceries"),
                createPayee("Leclerc", "Groceries"),
                createPayee("Intermarché", "Groceries"),
                createPayee("Super U", "Groceries"),
                createPayee("Casino", "Groceries"),
                createPayee("Monoprix", "Groceries"),
                createPayee("Franprix", "Groceries"),
                createPayee("Picard", "Groceries"),
                createPayee("Biocoop", "Groceries"),

                // Restaurants & Fast Food
                createPayee("McDonald's", "Fast Food"),
                createPayee("Burger King", "Fast Food"),
                createPayee("KFC", "Fast Food"),
                createPayee("Subway", "Fast Food"),
                createPayee("Domino's Pizza", "Fast Food"),
                createPayee("Just Eat", "Fast Food"),
                createPayee("Deliveroo", "Fast Food"),
                createPayee("Uber Eats", "Fast Food"),
                createPayee("Starbucks", "Coffee Shops"),
                createPayee("Quick", "Fast Food"),

                // Restaurants - Casual Dining
                createPayee("Casual Dining", "Casual Dining"),

                // Home improvement
                createPayee("IKEA", "Shopping"),
                createPayee("Leroy Merlin", "Shopping"),
                createPayee("Bricomarché", "Shopping"),
                createPayee("But", "Shopping"),

                // Internet & Phone
                createPayee("Orange", "Phone"),
                createPayee("SFR", "Phone"),
                createPayee("Bouygues Telecom", "Phone"),
                createPayee("Free", "Phone"),
                createPayee("Sosh", "Phone"),
                createPayee("NordVPN", "Software"),

                // Transport - Gas Stations
                createPayee("TotalEnergies", "Gas/Fuel"),
                createPayee("Shell", "Gas/Fuel"),
                createPayee("BP", "Gas/Fuel"),
                createPayee("Esso", "Gas/Fuel"),

                // Transport - Other
                createPayee("SNCF", "Train"),
                createPayee("RATP", "Bus/Metro"),
                createPayee("Uber", "Taxi/Rideshare"),
                createPayee("BlaBlaCar", "Taxi/Rideshare"),
                createPayee("FlixBus", "Bus/Metro"),
                createPayee("Air France", "Airlines"),
                createPayee("Ryanair", "Airlines"),
                createPayee("EasyJet", "Airlines"),

                // Health & Pharmacy
                createPayee("Pharmacie", "Prescriptions"),
                createPayee("Mutuelle", "Health Insurance"),
                createPayee("CPAM", "Healthcare"),
                createPayee("Médecin", "Doctor Visits"),
                createPayee("Dentiste", "Dental"),

                // Financial
                createPayee("BNP Paribas", "Other Expenses"),
                createPayee("Société Générale", "Other Expenses"),
                createPayee("Crédit Agricole", "Other Expenses"),
                createPayee("LCL", "Other Expenses"),
                createPayee("Boursorama", "Other Expenses"),
                createPayee("PayPal", "Other Expenses"),
                createPayee("Wise", "Other Expenses"),

                // Insurance
                createPayee("MAIF", "Insurance"),
                createPayee("AXA", "Insurance"),
                createPayee("GMF", "Insurance"),
                createPayee("MAAF", "Insurance"),
                createPayee("Allianz", "Insurance"),

                // Education
                createPayee("Udemy", "Online Courses"),
                createPayee("Coursera", "Online Courses"),
                createPayee("LinkedIn Learning", "Online Courses"),
                createPayee("Domaine Skiable", "Entertainment"),
                createPayee("Loan Payment Test", "Mortgage"));
    }

    /**
     * Creates a Payee entity with logo and category.
     *
     * @param name payee name
     * @param categoryName name of the category to associate
     * @return Payee entity
     */
    private Payee createPayee(String name, String categoryName) {
        String logo = LOGO_PATHS.getOrDefault(name, DEFAULT_LOGO);

        // If logo is explicitly set to null in the map, use default
        if (logo == null && LOGO_PATHS.containsKey(name)) {
            logo = DEFAULT_LOGO;
        }

        // Find category by name
        Category category = findCategoryByName(categoryName);

        return Payee.builder()
                .name(name)
                .logo(logo)
                .defaultCategory(category)
                .isSystem(true)
                .isActive(true)
                .build();
    }

    /**
     * Find a category by name (case-insensitive).
     *
     * @param categoryName the category name to find
     * @return the category or null if not found
     */
    private Category findCategoryByName(String categoryName) {
        if (categoryName == null) {
            return null;
        }

        // Try to find by exact name first
        List<Category> allCategories = categoryRepository.findAll();

        return allCategories.stream()
                .filter(c -> c.getName().equalsIgnoreCase(categoryName))
                .findFirst()
                .orElse(null);
    }
}
