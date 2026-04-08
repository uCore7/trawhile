package com.trawhile.web;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/auth/logout is handled directly by Spring Security (configured in SecurityConfig).
 * This controller exists as a placeholder.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
}
