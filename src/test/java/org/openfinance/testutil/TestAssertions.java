package org.openfinance.testutil;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.web.servlet.ResultActions;

/**
 * Utility class for common test assertions. Provides reusable assertion methods for controller
 * tests.
 */
public final class TestAssertions {

    private TestAssertions() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Assert successful API response
     *
     * @param result the result actions
     * @return result actions for chaining
     * @throws Exception if assertion fails
     */
    public static ResultActions assertSuccessResponse(ResultActions result) throws Exception {
        return result.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Assert created API response (201)
     *
     * @param result the result actions
     * @return result actions for chaining
     * @throws Exception if assertion fails
     */
    public static ResultActions assertCreatedResponse(ResultActions result) throws Exception {
        return result.andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Assert error API response
     *
     * @param result the result actions
     * @return result actions for chaining
     * @throws Exception if assertion fails
     */
    public static ResultActions assertErrorResponse(ResultActions result) throws Exception {
        return result.andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    /**
     * Assert bad request response (400)
     *
     * @param result the result actions
     * @return result actions for chaining
     * @throws Exception if assertion fails
     */
    public static ResultActions assertBadRequestResponse(ResultActions result) throws Exception {
        return result.andExpect(status().isBadRequest());
    }

    /**
     * Assert unauthorized response (401)
     *
     * @param result the result actions
     * @return result actions for chaining
     * @throws Exception if assertion fails
     */
    public static ResultActions assertUnauthorizedResponse(ResultActions result) throws Exception {
        return result.andExpect(status().isUnauthorized());
    }

    /**
     * Assert forbidden response (403)
     *
     * @param result the result actions
     * @return result actions for chaining
     * @throws Exception if assertion fails
     */
    public static ResultActions assertForbiddenResponse(ResultActions result) throws Exception {
        return result.andExpect(status().isForbidden());
    }

    /**
     * Assert not found response (404)
     *
     * @param result the result actions
     * @return result actions for chaining
     * @throws Exception if assertion fails
     */
    public static ResultActions assertNotFoundResponse(ResultActions result) throws Exception {
        return result.andExpect(status().isNotFound());
    }

    /**
     * Assert validation error response
     *
     * @param result the result actions
     * @param fieldName the field with validation error
     * @return result actions for chaining
     * @throws Exception if assertion fails
     */
    public static ResultActions assertValidationError(ResultActions result, String fieldName)
            throws Exception {
        return assertBadRequestResponse(result)
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
