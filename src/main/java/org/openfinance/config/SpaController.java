package org.openfinance.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards all non-API, non-asset requests to index.html so that React Router can handle
 * client-side routing.
 *
 * <p>Each path variable regex {@code [^\\.]*} rejects segments that contain a dot (file extension),
 * so static assets (e.g. /assets/main.js) are NOT forwarded — they fall through to Spring Boot's
 * static resource handler instead.
 *
 * <p>The explicit depth patterns cover all known React Router routes (max 4 segments).
 * Spring's handler mapping gives {@code @RestController} mappings under {@code /api/**} higher
 * priority, so there is no conflict.
 */
@Controller
public class SpaController {

    @GetMapping(value = {
            "/",
            "/{path:[^\\.]*}",
            "/{path:[^\\.]*}/{rest:[^\\.]*}",
            "/{path:[^\\.]*}/{rest1:[^\\.]*}/{rest2:[^\\.]*}",
            "/{path:[^\\.]*}/{rest1:[^\\.]*}/{rest2:[^\\.]*}/{rest3:[^\\.]*}"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
