package org.openfinance.service;

import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Models a caller retrying a rolled-back HTTP 409 without replaying successful writes. */
final class ConcurrentRequestSupport {
    private ConcurrentRequestSupport() {}

    static MockHttpServletResponse perform(
            MockMvc mvc, MockHttpServletRequestBuilder request, int attempts) throws Exception {
        for (int attempt = 1; ; attempt++) {
            MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
            if (response.getStatus() != 409 || attempt >= attempts) return response;
            Thread.sleep(Math.min(50L * attempt, 500L));
        }
    }
}
