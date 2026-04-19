package com.trawhile.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Forwards only SPA-owned browser navigation paths to index.html so the
 * Angular router can handle client-side navigation on hard refresh.
 */
@Controller
public class SpaRoutingController {

    private static final Set<String> SERVER_OWNED_PREFIXES = Set.of(
        "api", "login", "oauth2", "actuator", "assets", "error"
    );

    @GetMapping("/")
    public String root() {
        return "forward:/index.html";
    }

    @GetMapping("/{*path}")
    public String forwardSpaRoute(@PathVariable String path) {
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        String firstSegment = normalizedPath.contains("/")
            ? normalizedPath.substring(0, normalizedPath.indexOf('/'))
            : normalizedPath;

        if (normalizedPath.contains(".") || SERVER_OWNED_PREFIXES.contains(firstSegment)) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        return "forward:/index.html";
    }
}
