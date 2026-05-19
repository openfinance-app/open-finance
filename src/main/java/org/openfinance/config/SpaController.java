package org.openfinance.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards all non-API, non-asset requests to index.html so that React Router can handle
 * client-side routing.
 *
 * <p>The URL pattern {@code [^\\.]*} matches path segments that contain no dot character, which
 * excludes static assets (e.g. main.js, style.css) while catching all React Router paths (e.g.
 * /dashboard, /accounts/1).
 *
 * <p>Spring's handler mapping gives {@code @RestController} mappings under {@code /api/**} higher
 * priority, so there is no conflict.
 */
@Controller
public class SpaController {

    @GetMapping(value = {"/", "/{path:[^\\.]*}", "/{path:[^\\.]*}/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
