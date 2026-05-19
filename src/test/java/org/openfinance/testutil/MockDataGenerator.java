package org.openfinance.testutil;

import java.util.Random;
import java.util.UUID;

/**
 * Utility class for generating mock/random data for testing. Provides methods to generate realistic
 * test data.
 */
public final class MockDataGenerator {

    private static final Random RANDOM = new Random();
    private static final String[] FIRST_NAMES = {
        "John", "Jane", "Alice", "Bob", "Charlie", "Diana", "Edward", "Fiona"
    };
    private static final String[] LAST_NAMES = {
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis"
    };
    private static final String[] DOMAINS = {"example.com", "test.com", "demo.org", "sample.net"};

    private MockDataGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Generate random username
     *
     * @return random username
     */
    public static String randomUsername() {
        return randomFirstName().toLowerCase() + randomInt(100, 999);
    }

    /**
     * Generate random email
     *
     * @return random email address
     */
    public static String randomEmail() {
        return randomUsername() + "@" + randomElement(DOMAINS);
    }

    /**
     * Generate random password (always valid format)
     *
     * @return random valid password
     */
    public static String randomPassword() {
        return "Pass" + randomInt(1000, 9999) + "!";
    }

    /**
     * Generate random first name
     *
     * @return random first name
     */
    public static String randomFirstName() {
        return randomElement(FIRST_NAMES);
    }

    /**
     * Generate random last name
     *
     * @return random last name
     */
    public static String randomLastName() {
        return randomElement(LAST_NAMES);
    }

    /**
     * Generate random full name
     *
     * @return random full name
     */
    public static String randomFullName() {
        return randomFirstName() + " " + randomLastName();
    }

    /**
     * Generate random UUID string
     *
     * @return random UUID
     */
    public static String randomUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generate random integer in range
     *
     * @param min minimum value (inclusive)
     * @param max maximum value (inclusive)
     * @return random integer
     */
    public static int randomInt(int min, int max) {
        return RANDOM.nextInt((max - min) + 1) + min;
    }

    /**
     * Generate random long in range
     *
     * @param min minimum value (inclusive)
     * @param max maximum value (inclusive)
     * @return random long
     */
    public static long randomLong(long min, long max) {
        return min + (long) (RANDOM.nextDouble() * (max - min));
    }

    /**
     * Generate random double in range
     *
     * @param min minimum value
     * @param max maximum value
     * @return random double
     */
    public static double randomDouble(double min, double max) {
        return min + (RANDOM.nextDouble() * (max - min));
    }

    /**
     * Generate random boolean
     *
     * @return random boolean
     */
    public static boolean randomBoolean() {
        return RANDOM.nextBoolean();
    }

    /**
     * Select random element from array
     *
     * @param array the array
     * @param <T> element type
     * @return random element
     */
    @SafeVarargs
    public static <T> T randomElement(T... array) {
        return array[RANDOM.nextInt(array.length)];
    }

    /**
     * Generate random currency code
     *
     * @return random currency code
     */
    public static String randomCurrency() {
        return randomElement("USD", "EUR", "GBP", "JPY", "CAD", "AUD");
    }

    /**
     * Generate random account type
     *
     * @return random account type
     */
    public static String randomAccountType() {
        return randomElement("CHECKING", "SAVINGS", "CREDIT_CARD", "INVESTMENT", "CASH");
    }
}
